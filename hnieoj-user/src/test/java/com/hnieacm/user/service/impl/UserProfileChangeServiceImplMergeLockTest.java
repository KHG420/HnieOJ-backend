package com.hnieacm.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.user.constant.UserProfileChangeStatus;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.entity.UserProfileChangeApply;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.SysCollegeMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserProfileChangeApplyMapper;
import com.hnieacm.user.service.manager.UserInfoManager;
import com.hnieacm.user.service.support.UserManageValidator;
import com.hnieacm.user.support.MyBatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 合并碰撞回归：上游通用资料申请审批（UserProfileChangeServiceImpl#approve）必须与补丁身份申请
 * 审批一致，在同一事务内先锁 user_info 行，避免两套申请并发审批相互覆盖其它资料/密码。
 */
@ExtendWith(MockitoExtension.class)
class UserProfileChangeServiceImplMergeLockTest {

    @Mock
    private UserProfileChangeApplyMapper changeApplyMapper;

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private SysCollegeMapper sysCollegeMapper;

    @Mock
    private SysClassMapper sysClassMapper;

    @Mock
    private UserInfoManager userInfoManager;

    @Mock
    private UserManageValidator userManageValidator;

    private UserProfileChangeServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MyBatisPlusTestSupport.initTableInfo(UserInfo.class, UserProfileChangeApply.class);
    }

    @BeforeEach
    void setUp() {
        service = new UserProfileChangeServiceImpl(changeApplyMapper, userInfoMapper, sysCollegeMapper,
                sysClassMapper, userInfoManager, userManageValidator);
    }

    @Test
    void approveLocksUserRowAndOnlyAppliesRequestedFields() {
        UserProfileChangeApply apply = new UserProfileChangeApply();
        apply.setUid("u1");
        apply.setStatus(UserProfileChangeStatus.PENDING);
        apply.setUsername("new-name");
        when(changeApplyMapper.selectOne(any())).thenReturn(apply);

        UserInfo user = new UserInfo();
        user.setUid("u1");
        user.setUsername("old-name");
        user.setPassword("existing-hash");
        when(userInfoMapper.selectOne(any())).thenReturn(user);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("admin");
            service.approve("u1");
        }

        ArgumentCaptor<LambdaQueryWrapper<UserInfo>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userInfoMapper).selectOne(captor.capture());
        assertThat(captor.getValue().getTargetSql()).contains("FOR UPDATE");
        verify(userInfoMapper).updateById(user);
        assertThat(user.getUsername()).isEqualTo("new-name");
        // 申请未包含的字段（密码）不得被整行改写。
        assertThat(user.getPassword()).isEqualTo("existing-hash");
        assertThat(apply.getStatus()).isEqualTo(UserProfileChangeStatus.APPROVED);
        assertThat(apply.getReviewerUid()).isEqualTo("admin");
    }

    @Test
    void approveRejectsLegacyApplyCarryingIdentityFields() {
        UserProfileChangeApply apply = new UserProfileChangeApply();
        apply.setUid("u1");
        apply.setStatus(UserProfileChangeStatus.PENDING);
        apply.setRealname("张三");
        when(changeApplyMapper.selectOne(any())).thenReturn(apply);

        // 旧版通用流程遗留的待审记录含身份字段：显式拒绝，不静默跳过、不改写 user_info。
        assertThatThrownBy(() -> service.approve("u1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("身份字段");
        verifyNoInteractions(userInfoMapper);
    }

    @Test
    void approveDoesNotOverwriteIdentityFields() {
        UserProfileChangeApply apply = new UserProfileChangeApply();
        apply.setUid("u1");
        apply.setStatus(UserProfileChangeStatus.PENDING);
        apply.setPhone("13800000000");
        when(changeApplyMapper.selectOne(any())).thenReturn(apply);

        UserInfo user = new UserInfo();
        user.setUid("u1");
        user.setRealname("原姓名");
        user.setCollegeId(7L);
        user.setGrade("2024");
        user.setClassId(9L);
        when(userInfoMapper.selectOne(any())).thenReturn(user);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("admin");
            service.approve("u1");
        }

        assertThat(user.getPhone()).isEqualTo("13800000000");
        // 身份字段由身份资料变更流程独占受理，通用流程一律不得改写。
        assertThat(user.getRealname()).isEqualTo("原姓名");
        assertThat(user.getCollegeId()).isEqualTo(7L);
        assertThat(user.getGrade()).isEqualTo("2024");
        assertThat(user.getClassId()).isEqualTo(9L);
    }

    @Test
    void approveRunsInTransaction() throws Exception {
        assertThat(UserProfileChangeServiceImpl.class.getMethod("approve", String.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }
}
