package com.hnieacm.problem.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hnieacm.problem.entity.ProblemTag;
import com.hnieacm.problem.entity.Tag;
import com.hnieacm.problem.mapper.ProblemTagMapper;
import com.hnieacm.problem.mapper.TagMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/22
 * @Description: replaceProblemTags 锁顺序回归：必须像 TagServiceImpl.deleteTag /
 * ProblemTagConfigServiceImpl.deleteRemovedUnusedTags 一样先锁 tag 行（FOR UPDATE），
 * 再读写 problem_tag 关联。旧实现先 DELETE 关联后取 tag 锁，与那两条路径构成 ABBA 死锁，
 * 本测试的 InOrder 断言在旧顺序下会失败。
 */
@ExtendWith(MockitoExtension.class)
class ProblemServiceSupportReplaceProblemTagsTest {

    @Mock
    private TagMapper tagMapper;

    @Mock
    private ProblemTagMapper problemTagMapper;

    @BeforeEach
    void setUp() {
        initTableInfo(Tag.class);
        initTableInfo(ProblemTag.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void locksTagRowsBeforeWritingProblemTagRelations() {
        Tag dp = tag(11L, "dp");
        when(tagMapper.selectList(any())).thenReturn(List.of(dp));

        ProblemServiceSupport.replaceProblemTags(problemTagMapper, tagMapper, 7L, List.of("dp"));

        // 当前读取（FOR UPDATE）tag 行必须先于 problem_tag 的删除与插入
        InOrder order = inOrder(tagMapper, problemTagMapper);
        order.verify(tagMapper, times(2)).selectList(any());
        order.verify(problemTagMapper).delete(any());
        order.verify(problemTagMapper).insert(any(ProblemTag.class));

        // 首次 tag 读取必须是加锁当前读，普通快照读拿不到行锁
        ArgumentCaptor<Wrapper<Tag>> tagWrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(tagMapper, atLeastOnce()).selectList(tagWrapperCaptor.capture());
        assertThat(tagWrapperCaptor.getAllValues().get(0).getSqlSegment()).containsIgnoringCase("FOR UPDATE");

        // 已存在的标签不重复插入，正常替换写出一条去重后的关联
        verify(tagMapper, never()).insert(any(Tag.class));
        ArgumentCaptor<ProblemTag> relCaptor = ArgumentCaptor.forClass(ProblemTag.class);
        verify(problemTagMapper).insert(relCaptor.capture());
        assertThat(relCaptor.getValue().getProblemId()).isEqualTo(7L);
        assertThat(relCaptor.getValue().getTid()).isEqualTo(11L);
    }

    @Test
    void createsMissingTagUnderLockBeforeWritingRelations() {
        Tag created = tag(21L, "graph");
        when(tagMapper.selectList(any())).thenReturn(List.of(), List.of(created));
        doAnswer(invocation -> {
            Tag inserted = invocation.getArgument(0);
            inserted.setId(21L);
            return 1;
        }).when(tagMapper).insert(any(Tag.class));

        ProblemServiceSupport.replaceProblemTags(problemTagMapper, tagMapper, 7L, List.of("graph", "graph"));

        InOrder order = inOrder(tagMapper, problemTagMapper);
        order.verify(tagMapper).insert(any(Tag.class));
        order.verify(problemTagMapper).delete(any());
        order.verify(problemTagMapper).insert(any(ProblemTag.class));

        // 重复名称去重后只写一条关联
        ArgumentCaptor<ProblemTag> relCaptor = ArgumentCaptor.forClass(ProblemTag.class);
        verify(problemTagMapper).insert(relCaptor.capture());
        assertThat(relCaptor.getValue().getTid()).isEqualTo(21L);
    }

    @Test
    void emptyTagsClearRelationsWithoutTouchingTagRows() {
        ProblemServiceSupport.replaceProblemTags(problemTagMapper, tagMapper, 7L, List.of());

        verify(problemTagMapper).delete(any());
        verify(problemTagMapper, never()).insert(any(ProblemTag.class));
        verifyNoInteractions(tagMapper);
    }

    @Test
    void nullTagsClearRelationsWithoutTouchingTagRows() {
        ProblemServiceSupport.replaceProblemTags(problemTagMapper, tagMapper, 7L, null);

        verify(problemTagMapper).delete(any());
        verify(problemTagMapper, never()).insert(any(ProblemTag.class));
        verifyNoInteractions(tagMapper);
    }

    @Test
    void nullProblemIdIsNoOp() {
        ProblemServiceSupport.replaceProblemTags(problemTagMapper, tagMapper, null, List.of("dp"));

        verifyNoInteractions(tagMapper);
        verifyNoInteractions(problemTagMapper);
    }

    @Test
    void blankTagNamesAreFilteredBeforeLocking() {
        Tag dp = tag(11L, "dp");
        when(tagMapper.selectList(any())).thenReturn(List.of(dp));

        ProblemServiceSupport.replaceProblemTags(problemTagMapper, tagMapper, 7L, List.of("dp", " ", ""));

        verify(problemTagMapper).delete(any());
        ArgumentCaptor<ProblemTag> relCaptor = ArgumentCaptor.forClass(ProblemTag.class);
        verify(problemTagMapper).insert(relCaptor.capture());
        assertThat(relCaptor.getValue().getTid()).isEqualTo(11L);
    }

    private Tag tag(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
