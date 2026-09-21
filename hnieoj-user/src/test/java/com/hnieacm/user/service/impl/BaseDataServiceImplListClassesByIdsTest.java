package com.hnieacm.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.SysClassTaMapper;
import com.hnieacm.user.mapper.SysCollegeMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.support.MyBatisPlusTestSupport;
import com.hnieacm.user.vo.IdNameVo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 按 id 批量查询班级回归：命中/未命中语义、去重、非法 id 与空入参。
 */
class BaseDataServiceImplListClassesByIdsTest {

    @BeforeAll
    static void initTableInfo() {
        MyBatisPlusTestSupport.initTableInfo(SysClass.class);
    }

    private SysClassMapper sysClassMapper;
    private BaseDataServiceImpl service;

    @BeforeEach
    void setUp() {
        sysClassMapper = mock(SysClassMapper.class);
        service = new BaseDataServiceImpl(
                mock(SysCollegeMapper.class),
                sysClassMapper,
                mock(SysClassTaMapper.class),
                mock(UserInfoMapper.class));
    }

    @Test
    void mapsEntitiesToIdNameVoPreservingMapperOrder() {
        when(sysClassMapper.selectList(any())).thenReturn(List.of(clazz(7L, "软工2201"), clazz(3L, "计科2202")));

        List<IdNameVo> result = service.listClassesByIds(List.of(3L, 7L));

        assertThat(result).extracting(IdNameVo::getId).containsExactly(7L, 3L);
        assertThat(result).extracting(IdNameVo::getName).containsExactly("软工2201", "计科2202");
    }

    @Test
    void missingIdsAreSkippedWithoutError() {
        // 库中只有 5，请求里 5 存在、99 不存在 —— 只返回 5，不抛异常
        when(sysClassMapper.selectList(any())).thenReturn(List.of(clazz(5L, "计科2201")));

        List<IdNameVo> result = service.listClassesByIds(List.of(5L, 99L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(5L);
    }

    @Test
    void allIdsMissingReturnsEmptyList() {
        when(sysClassMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThat(service.listClassesByIds(List.of(98L, 99L))).isEmpty();
    }

    @Test
    void mapperReturningNullIsTreatedAsEmpty() {
        when(sysClassMapper.selectList(any())).thenReturn(null);

        assertThat(service.listClassesByIds(List.of(1L))).isEmpty();
    }

    @Test
    void duplicateIdsAreQueriedOnce() {
        when(sysClassMapper.selectList(any())).thenReturn(List.of(clazz(5L, "计科2201")));

        service.listClassesByIds(List.of(5L, 5L, 5L));

        LambdaQueryWrapper<SysClass> wrapper = capturedWrapper();
        // 3 个重复 id 只产生 1 个占位符 —— 去重确实发生在 SQL 之前
        assertThat(wrapper.getTargetSql()).contains("IN (?)");
        assertThat(inValuesOf(wrapper)).containsExactly("5");
    }

    @Test
    void buildsSingleInQueryOrderedById() {
        when(sysClassMapper.selectList(any())).thenReturn(List.of(clazz(5L, "计科2201")));

        service.listClassesByIds(List.of(5L, 6L, 7L));

        LambdaQueryWrapper<SysClass> wrapper = capturedWrapper();
        String sql = wrapper.getTargetSql();
        // 3 个不同 id → 恰好 3 个占位符：一次查询覆盖全部 id，这是「请求数从 ~80 降到 1」的后端侧保证
        assertThat(sql).contains("IN (?,?,?)");
        assertThat(sql).containsIgnoringCase("ORDER BY id ASC");
        assertThat(inValuesOf(wrapper)).containsExactly("5", "6", "7");
    }

    @Test
    void rejectsNullOrEmptyIds() {
        assertRejected(null, "ids 不能为空");
        assertRejected(Collections.emptyList(), "ids 不能为空");
    }

    @Test
    void rejectsIllegalIds() {
        assertRejected(List.of(0L), "id 必须>=1");
        assertRejected(List.of(-1L), "id 必须>=1");
        assertRejected(Arrays.asList(1L, null), "id 必须>=1");
    }

    private void assertRejected(List<Long> ids, String expectedMessage) {
        assertThatThrownBy(() -> service.listClassesByIds(ids))
                .isInstanceOf(BizException.class)
                .hasMessage(expectedMessage)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
    }

    private static List<String> inValuesOf(LambdaQueryWrapper<SysClass> wrapper) {
        // MyBatis-Plus 的 IN 参数在生成 SQL 段时才写入 paramNameValuePairs，先触发一次
        wrapper.getTargetSql();
        List<String> values = new ArrayList<>();
        for (Object param : wrapper.getParamNameValuePairs().values()) {
            values.add(String.valueOf(param));
        }
        // paramNameValuePairs 是 HashMap，迭代顺序不稳定，排序后再断言
        Collections.sort(values);
        return values;
    }

    private LambdaQueryWrapper<SysClass> capturedWrapper() {
        ArgumentCaptor<LambdaQueryWrapper<SysClass>> captor = lambdaCaptor();
        verify(sysClassMapper).selectList(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<LambdaQueryWrapper<SysClass>> lambdaCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }

    private static SysClass clazz(Long id, String name) {
        SysClass sysClass = new SysClass();
        sysClass.setId(id);
        sysClass.setName(name);
        return sysClass;
    }
}
