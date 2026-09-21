package com.hnieacm.problem.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.dto.SaveTagConfigRequest;
import com.hnieacm.problem.dto.TagGroupRequest;
import com.hnieacm.problem.entity.ProblemTag;
import com.hnieacm.problem.entity.Tag;
import com.hnieacm.problem.mapper.ProblemTagMapper;
import com.hnieacm.problem.mapper.TagMapper;
import com.hnieacm.problem.service.ProblemTagConfigService;
import com.hnieacm.problem.vo.TagGroupVo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 题目标签配置服务实现
 */
@Service
@RequiredArgsConstructor
public class ProblemTagConfigServiceImpl implements ProblemTagConfigService {

    private final TagMapper tagMapper;
    private final ProblemTagMapper problemTagMapper;

    @Override
    public List<TagGroupVo> list() {
        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .orderByAsc(Tag::getCategory)
                .orderByAsc(Tag::getName));
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (Tag tag : tags) {
            String category = StrUtil.blankToDefault(tag.getCategory(), "未分类");
            grouped.computeIfAbsent(category, key -> new ArrayList<>()).add(tag.getName());
        }
        return grouped.entrySet().stream().map(entry -> {
            TagGroupVo vo = new TagGroupVo();
            vo.setTitle(entry.getKey());
            vo.setTags(entry.getValue());
            return vo;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(SaveTagConfigRequest request) {
        List<DesiredTag> desiredTags = normalize(request);
        List<Tag> existingTags = tagMapper.selectList(new LambdaQueryWrapper<Tag>());
        Map<String, Tag> existingByName = existingTags.stream()
                .collect(Collectors.toMap(Tag::getName, Function.identity(), (a, b) -> a));
        Set<String> desiredNames = desiredTags.stream().map(DesiredTag::name).collect(Collectors.toCollection(LinkedHashSet::new));

        deleteRemovedUnusedTags(existingTags, desiredNames);
        for (DesiredTag desiredTag : desiredTags) {
            Tag existing = existingByName.get(desiredTag.name());
            if (existing == null) {
                Tag tag = new Tag();
                tag.setName(desiredTag.name());
                tag.setCategory(desiredTag.category());
                try {
                    tagMapper.insert(tag);
                    continue;
                } catch (DuplicateKeyException e) {
                    // 并发保存同一份新标签：INSERT 命中唯一键时本事务已持有该行的 S 锁，
                    // 此处只能再用 S 兼容的当前读（FOR SHARE）确认，若改写成 FOR UPDATE 升级为 X
                    // 会与同样卡在 S 锁上的并发事务互相等待，直接死锁。
                    Tag concurrent = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                            .eq(Tag::getName, desiredTag.name())
                            .last("FOR SHARE"));
                    if (concurrent == null || concurrent.getId() == null) {
                        // 取不到说明对方已回滚，交回业务错误让调用方重试，绝不静默丢弃。
                        throw new BizException(ResultCode.INTERNAL_ERROR,
                                "标签写入并发冲突，请重试：" + desiredTag.name());
                    }
                    // 并发创建时以先写入者的分类为准，不再争抢该行写锁
                    continue;
                }
            }
            if (!desiredTag.category().equals(existing.getCategory())) {
                tagMapper.update(null, new LambdaUpdateWrapper<Tag>()
                        .eq(Tag::getId, existing.getId())
                        .set(Tag::getCategory, desiredTag.category()));
            }
        }
    }

    private List<DesiredTag> normalize(SaveTagConfigRequest request) {
        if (request == null || request.getTags() == null || request.getTags().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "tags 不能为空");
        }
        Set<String> names = new LinkedHashSet<>();
        List<DesiredTag> result = new ArrayList<>();
        for (TagGroupRequest group : request.getTags()) {
            String category = StrUtil.trimToNull(group == null ? null : group.getTitle());
            if (category == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "标签组标题不能为空");
            }
            if (group.getTags() == null || group.getTags().isEmpty()) {
                throw new BizException(ResultCode.BAD_REQUEST, "标签组不能为空");
            }
            for (String item : group.getTags()) {
                String name = StrUtil.trimToNull(item);
                if (name == null) {
                    throw new BizException(ResultCode.BAD_REQUEST, "标签名不能为空");
                }
                if (!names.add(name)) {
                    throw new BizException(ResultCode.BAD_REQUEST, "标签名重复：" + name);
                }
                result.add(new DesiredTag(category, name));
            }
        }
        result.sort(Comparator.comparing(DesiredTag::category).thenComparing(DesiredTag::name));
        return result;
    }

    private void deleteRemovedUnusedTags(List<Tag> existingTags, Set<String> desiredNames) {
        for (Tag tag : existingTags) {
            if (desiredNames.contains(tag.getName())) {
                continue;
            }
            // 与 TagServiceImpl.deleteTag、ensureTags 使用同一把 tag 行锁串行化：
            // 三条路径都先锁 tag 行，再读写 problem_tag，故删除与建关联不会交错。
            Tag locked = tagMapper.selectOne(
                    new LambdaQueryWrapper<Tag>().eq(Tag::getId, tag.getId()).last("FOR UPDATE"));
            if (locked == null) {
                continue;
            }
            // 引用检查必须是当前读：本事务在 save() 开头已用普通 SELECT 建立 REPEATABLE READ 快照，
            // 普通 SELECT 会沿用旧快照而看不到并发事务刚提交的关联，导致误删仍被引用的标签。
            List<ProblemTag> references = problemTagMapper.selectList(new LambdaQueryWrapper<ProblemTag>()
                    .eq(ProblemTag::getTid, tag.getId())
                    .last("LIMIT 1 FOR UPDATE"));
            if (!references.isEmpty()) {
                throw new BizException(ResultCode.BAD_REQUEST, "标签已被题目使用，不能删除：" + tag.getName());
            }
            tagMapper.deleteById(tag.getId());
        }
    }

    private record DesiredTag(String category, String name) {
    }
}
