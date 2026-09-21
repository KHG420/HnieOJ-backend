package com.hnieacm.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.constant.NoticeStatusConstant;
import com.hnieacm.user.constant.NoticeTargetTypeConstant;
import com.hnieacm.user.dto.NoticeSaveRequest;
import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.entity.UserMessage;
import com.hnieacm.user.entity.UserNotice;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserMessageMapper;
import com.hnieacm.user.mapper.UserNoticeMapper;
import com.hnieacm.user.service.NoticeAdminService;
import com.hnieacm.user.vo.UserNoticeDetailVo;
import com.hnieacm.user.vo.UserNoticeListVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 定向通知管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeAdminServiceImpl implements NoticeAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final int MAX_TARGET_COUNT = 1000;

    /**
     * 单次发布展开后的收件人总数上限：防止 CLASSES 目标展开成全校用户后产生数万行单事务写入。
     */
    private static final int MAX_RECIPIENT_COUNT = 5000;

    private static final int INSERT_BATCH_SIZE = 500;

    private final UserNoticeMapper userNoticeMapper;
    private final UserMessageMapper userMessageMapper;
    private final UserInfoMapper userInfoMapper;
    private final SysClassMapper sysClassMapper;
    private final ObjectMapper objectMapper;

    @Override
    public PageVo<UserNoticeListVo> listNotices(int page, int pageSize, String keyword, String status) {
        validatePage(page, pageSize);

        String normalizedStatus = null;
        if (status != null && !status.trim().isEmpty()) {
            normalizedStatus = NoticeStatusConstant.normalize(status);
            if (normalizedStatus == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "status 只能为 DRAFT 或 PUBLISHED");
            }
        }

        LambdaQueryWrapper<UserNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(
                UserNotice::getId,
                UserNotice::getTitle,
                UserNotice::getTargetType,
                UserNotice::getStatus,
                UserNotice::getCreatorUid,
                UserNotice::getPublishedAt,
                UserNotice::getGmtCreate,
                UserNotice::getGmtModified
        );
        if (normalizedStatus != null) {
            wrapper.eq(UserNotice::getStatus, normalizedStatus);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(UserNotice::getTitle, keyword.trim());
        }
        wrapper.orderByDesc(UserNotice::getGmtCreate, UserNotice::getId);

        Page<UserNotice> mpPage = new Page<>(page, pageSize);
        Page<UserNotice> result = userNoticeMapper.selectPage(mpPage, wrapper);
        List<UserNoticeListVo> list = result.getRecords().stream().map(this::toListVo).toList();
        return new PageVo<>(list, result.getTotal());
    }

    @Override
    public UserNoticeDetailVo getNotice(Long id) {
        UserNotice notice = requireNotice(id);
        return toDetailVo(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNotice(NoticeSaveRequest request, String creatorUid) {
        NormalizedTarget target = normalizeTarget(request);
        UserNotice notice = new UserNotice();
        notice.setTitle(request.getTitle().trim());
        notice.setContent(request.getContent());
        notice.setTargetType(target.targetType());
        notice.setTargetSpec(writeTargetSpec(target.targetIds()));
        notice.setStatus(NoticeStatusConstant.DRAFT);
        notice.setCreatorUid(creatorUid);
        userNoticeMapper.insert(notice);
        log.info("Notice draft created, id: {}, operator: {}", notice.getId(), creatorUid);
        return notice.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNotice(Long id, NoticeSaveRequest request) {
        // 与发布使用同一行锁：先锁通知行再判断状态/落库，避免并发发布已提交后，编辑事务用旧快照
        // 把 PUBLISHED 回写成 DRAFT/新目标，导致后续再次发布时给新收件人投递。
        UserNotice notice = requireLockedNotice(id);
        if (NoticeStatusConstant.PUBLISHED.equals(notice.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "已发布通知不可编辑正文或目标");
        }
        NormalizedTarget target = normalizeTarget(request);
        notice.setTitle(request.getTitle().trim());
        notice.setContent(request.getContent());
        notice.setTargetType(target.targetType());
        notice.setTargetSpec(writeTargetSpec(target.targetIds()));
        // 置 null 后 MyBatis-Plus 不写入该列，交由 DDL 的 ON UPDATE CURRENT_TIMESTAMP 维护
        notice.setGmtModified(null);
        userNoticeMapper.updateById(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotice(Long id) {
        // 与发布/编辑使用同一行锁，串行化删除与发布；已写入 user_message 的收件人快照不级联删除。
        UserNotice notice = requireLockedNotice(id);
        userNoticeMapper.deleteById(notice.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishNotice(Long id) {
        // 锁定通知行：并发发布串行化，第二个事务看到 PUBLISHED 后幂等返回。
        UserNotice notice = userNoticeMapper.selectOne(
                new LambdaQueryWrapper<UserNotice>().eq(UserNotice::getId, id).last("FOR UPDATE")
        );
        if (notice == null) {
            throw new BizException(ResultCode.NOT_FOUND, "通知不存在");
        }
        if (NoticeStatusConstant.PUBLISHED.equals(notice.getStatus())) {
            return;
        }

        List<String> targetIds = readTargetSpec(notice.getTargetSpec());
        List<String> recipients = resolveRecipients(notice.getTargetType(), targetIds);
        if (recipients.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "发布失败：没有有效收件人");
        }
        if (recipients.size() > MAX_RECIPIENT_COUNT) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "展开后的收件人总数不能超过 " + MAX_RECIPIENT_COUNT + "，请拆分通知批次");
        }

        LocalDateTime now = LocalDateTime.now();
        List<UserMessage> messages = recipients.stream().map(recipientUid -> {
            UserMessage message = new UserMessage();
            message.setNoticeId(notice.getId());
            message.setRecipientUid(recipientUid);
            message.setTitle(notice.getTitle());
            message.setContent(notice.getContent());
            message.setCreatedAt(now);
            return message;
        }).toList();
        // 批量插入（分批 flush），避免数万次单条 INSERT 的长事务持锁；
        // 唯一键 uk_notice_recipient + 通知行锁 + PUBLISHED 状态判断共同保证不重复投递。
        userMessageMapper.insert(messages, INSERT_BATCH_SIZE);

        notice.setStatus(NoticeStatusConstant.PUBLISHED);
        notice.setPublishedAt(now);
        notice.setGmtModified(null);
        userNoticeMapper.updateById(notice);
        log.info("Notice published, id: {}, recipients: {}", notice.getId(), recipients.size());
    }

    private void validatePage(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 和 pageSize 必须大于 0");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST, "pageSize 不能超过 100");
        }
    }

    private UserNotice requireNotice(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不能为空");
        }
        UserNotice notice = userNoticeMapper.selectById(id);
        if (notice == null) {
            throw new BizException(ResultCode.NOT_FOUND, "通知不存在");
        }
        return notice;
    }

    private UserNotice requireLockedNotice(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不能为空");
        }
        UserNotice notice = userNoticeMapper.selectOne(
                new LambdaQueryWrapper<UserNotice>().eq(UserNotice::getId, id).last("FOR UPDATE")
        );
        if (notice == null) {
            throw new BizException(ResultCode.NOT_FOUND, "通知不存在");
        }
        return notice;
    }

    private NormalizedTarget normalizeTarget(NoticeSaveRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        String targetType = NoticeTargetTypeConstant.normalize(request.getTargetType());
        if (targetType == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "targetType 只能为 USERS 或 CLASSES");
        }

        List<String> rawTargetIds = request.getTargetIds();
        if (rawTargetIds == null || rawTargetIds.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "targetIds 不能为空");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : rawTargetIds) {
            if (raw == null || raw.trim().isEmpty()) {
                throw new BizException(ResultCode.BAD_REQUEST, "targetIds 不能包含空值");
            }
            normalized.add(raw.trim());
        }
        if (normalized.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "targetIds 不能为空");
        }
        if (normalized.size() > MAX_TARGET_COUNT) {
            throw new BizException(ResultCode.BAD_REQUEST, "targetIds 数量不能超过 1000");
        }

        if (NoticeTargetTypeConstant.USERS.equals(targetType)) {
            ensureUsersExist(normalized);
        } else {
            ensureClassesExist(normalized);
        }
        return new NormalizedTarget(targetType, new ArrayList<>(normalized));
    }

    private void ensureUsersExist(Set<String> uids) {
        List<UserInfo> users = userInfoMapper.selectList(
                new LambdaQueryWrapper<UserInfo>().in(UserInfo::getUid, uids)
        );
        Set<String> existed = users == null ? Set.of()
                : users.stream().map(UserInfo::getUid).collect(Collectors.toSet());
        for (String uid : uids) {
            if (!existed.contains(uid)) {
                throw new BizException(ResultCode.USER_NOT_FOUND, "用户不存在: " + uid);
            }
        }
    }

    private void ensureClassesExist(Set<String> classIds) {
        List<Long> parsed = new ArrayList<>(classIds.size());
        for (String raw : classIds) {
            try {
                parsed.add(Long.parseLong(raw));
            } catch (NumberFormatException e) {
                throw new BizException(ResultCode.BAD_REQUEST, "CLASSES 目标必须为班级ID");
            }
        }
        List<SysClass> classes = sysClassMapper.selectBatchIds(parsed);
        Set<Long> existed = classes == null ? Set.of()
                : classes.stream().map(SysClass::getId).collect(Collectors.toSet());
        for (Long classId : parsed) {
            if (!existed.contains(classId)) {
                throw new BizException(ResultCode.CLASS_NOT_FOUND, "班级不存在: " + classId);
            }
        }
    }

    private List<String> resolveRecipients(String targetType, List<String> targetIds) {
        if (NoticeTargetTypeConstant.USERS.equals(targetType)) {
            List<UserInfo> users = userInfoMapper.selectList(
                    new LambdaQueryWrapper<UserInfo>().in(UserInfo::getUid, targetIds)
            );
            if (users == null) {
                return List.of();
            }
            return users.stream()
                    .map(UserInfo::getUid)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .sorted()
                    .toList();
        }

        List<Long> classIds = new ArrayList<>(targetIds.size());
        for (String raw : targetIds) {
            try {
                classIds.add(Long.parseLong(raw));
            } catch (NumberFormatException e) {
                throw new BizException(ResultCode.BAD_REQUEST, "CLASSES 目标必须为班级ID");
            }
        }
        List<UserInfo> users = userInfoMapper.selectList(
                new LambdaQueryWrapper<UserInfo>().in(UserInfo::getClassId, classIds)
        );
        if (users == null) {
            return List.of();
        }
        // 发布时按“当时班级现有用户”固定收件人快照。
        return users.stream()
                .map(UserInfo::getUid)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    private String writeTargetSpec(List<String> targetIds) {
        try {
            return objectMapper.writeValueAsString(targetIds);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "通知目标序列化失败");
        }
    }

    private List<String> readTargetSpec(String targetSpec) {
        if (!StringUtils.hasText(targetSpec)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(targetSpec, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "通知目标解析失败");
        }
    }

    private UserNoticeListVo toListVo(UserNotice notice) {
        UserNoticeListVo vo = new UserNoticeListVo();
        vo.setId(notice.getId());
        vo.setTitle(notice.getTitle());
        vo.setTargetType(notice.getTargetType());
        vo.setStatus(notice.getStatus());
        vo.setCreatorUid(notice.getCreatorUid());
        vo.setPublishedAt(notice.getPublishedAt());
        vo.setGmtCreate(notice.getGmtCreate());
        vo.setGmtModified(notice.getGmtModified());
        return vo;
    }

    private UserNoticeDetailVo toDetailVo(UserNotice notice) {
        UserNoticeDetailVo vo = new UserNoticeDetailVo();
        vo.setId(notice.getId());
        vo.setTitle(notice.getTitle());
        vo.setContent(notice.getContent());
        vo.setTargetType(notice.getTargetType());
        vo.setTargetIds(readTargetSpec(notice.getTargetSpec()));
        vo.setStatus(notice.getStatus());
        vo.setCreatorUid(notice.getCreatorUid());
        vo.setPublishedAt(notice.getPublishedAt());
        vo.setGmtCreate(notice.getGmtCreate());
        vo.setGmtModified(notice.getGmtModified());
        return vo;
    }

    private record NormalizedTarget(String targetType, List<String> targetIds) {
    }
}
