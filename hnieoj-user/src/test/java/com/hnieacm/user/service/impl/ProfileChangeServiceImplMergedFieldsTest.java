package com.hnieacm.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.user.constant.ProfileChangeStatusConstant;
import com.hnieacm.user.dto.BatchIdsRequest;
import com.hnieacm.user.dto.ProfileChangeCreateRequest;
import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.entity.SysCollege;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.entity.UserProfileChange;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.SysCollegeMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserProfileChangeMapper;
import com.hnieacm.user.service.support.UserAuthStateService;
import com.hnieacm.user.service.support.UserManageValidator;
import com.hnieacm.user.support.MyBatisPlusTestSupport;
import com.hnieacm.user.vo.BatchOperationResultVo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 合并两套「资料变更」流程后的单一流程回归（BE-03.6 / W5）。
 * <p>覆盖原通用资料流程独有的能力（联系/社交字段、批量审批），以及合并带来的关键性质：
 * 逐字段原值一致性——用户期间只改了别的字段时，本申请仍然有效且只写回本申请要改的字段。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileChangeServiceImplMergedFieldsTest {

    /** 邮箱申请：原值与目标值只差 email */
    private static final String EMAIL_ORIGINAL =
            "{\"realname\":\"Old\",\"collegeId\":1,\"grade\":\"2024\",\"classId\":10,\"email\":\"old@example.com\"}";
    private static final String EMAIL_PROPOSED =
            "{\"realname\":\"Old\",\"collegeId\":1,\"grade\":\"2024\",\"classId\":10,\"email\":\"new@example.com\"}";

    @Mock
    private UserProfileChangeMapper userProfileChangeMapper;

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private SysCollegeMapper sysCollegeMapper;

    @Mock
    private SysClassMapper sysClassMapper;

    @Mock
    private UserAuthStateService userAuthStateService;

    @Mock
    private UserManageValidator userManageValidator;

    private ProfileChangeServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MyBatisPlusTestSupport.initTableInfo(
                UserInfo.class, UserProfileChange.class, SysClass.class, SysCollege.class);
    }

    @BeforeEach
    void setUp() {
        service = new ProfileChangeServiceImpl(
                userProfileChangeMapper, userInfoMapper, sysCollegeMapper, sysClassMapper,
                userAuthStateService, userManageValidator, new ObjectMapper());
    }

    // ---------------- 提交 ----------------

    @Test
    void contactOnlyRequestNeedsNoIdentityFields() {
        when(userInfoMapper.selectOne(any())).thenReturn(user());
        when(userProfileChangeMapper.selectCount(any())).thenReturn(0L);
        when(userInfoMapper.selectCount(any())).thenReturn(0L);

        service.createChangeRequest("u1", emailOnlyRequest());

        ArgumentCaptor<UserProfileChange> captor = ArgumentCaptor.forClass(UserProfileChange.class);
        verify(userProfileChangeMapper).insert(captor.capture());
        UserProfileChange saved = captor.getValue();
        assertThat(saved.getOriginal()).contains("\"email\":\"old@example.com\"");
        assertThat(saved.getProposed()).contains("\"email\":\"new@example.com\"");
        // 纯联系字段申请不得触发身份校验（学院/班级查询）
        verify(sysCollegeMapper, never()).selectById(any());
        verify(sysClassMapper, never()).selectById(any());
    }

    @Test
    void requestWithoutAnyActualChangeIsRejected() {
        when(userInfoMapper.selectOne(any())).thenReturn(user());
        when(userProfileChangeMapper.selectCount(any())).thenReturn(0L);

        // 提交与当前值完全相同的字段
        ProfileChangeCreateRequest request = new ProfileChangeCreateRequest();
        request.setEmail("old@example.com");
        request.setReason("看起来有改动其实没有");

        assertThatThrownBy(() -> service.createChangeRequest("u1", request))
                .isInstanceOf(BizException.class)
                .hasMessage("至少提交一个修改字段");
        verify(userProfileChangeMapper, never()).insert(any(UserProfileChange.class));
    }

    @Test
    void duplicateEmailIsRejected() {
        when(userInfoMapper.selectOne(any())).thenReturn(user());
        when(userProfileChangeMapper.selectCount(any())).thenReturn(0L);
        // 邮箱唯一键：已被别人占用时必须在提交阶段拦下，否则审批时会撞 uk_email
        when(userInfoMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createChangeRequest("u1", emailOnlyRequest()))
                .isInstanceOf(BizException.class)
                .hasMessage("邮箱已被占用");
        verify(userProfileChangeMapper, never()).insert(any(UserProfileChange.class));
    }

    @Test
    void usernameLengthIsValidatedThroughSharedValidator() {
        when(userInfoMapper.selectOne(any())).thenReturn(user());
        when(userProfileChangeMapper.selectCount(any())).thenReturn(0L);
        org.mockito.Mockito.doThrow(new BizException(com.hnieacm.common.result.ResultCode.BAD_REQUEST,
                        "用户名长度应在 2-20 之间"))
                .when(userManageValidator).validateUsernameLength(anyString());

        ProfileChangeCreateRequest request = new ProfileChangeCreateRequest();
        request.setUsername("a".repeat(30));
        request.setReason("改名");

        assertThatThrownBy(() -> service.createChangeRequest("u1", request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名长度");
    }

    // ---------------- 审批 ----------------

    @Test
    void emailApprovalAppliesOnlyEmailAndIgnoresUnrelatedDrift() {
        UserProfileChange change = change(1L, ProfileChangeStatusConstant.PENDING,
                EMAIL_ORIGINAL, EMAIL_PROPOSED);
        when(userProfileChangeMapper.selectOne(any())).thenReturn(change);
        UserInfo user = user();
        // 期间实名被别的申请改过：本申请只改 email，不应被判失效
        user.setRealname("ChangedElsewhere");
        when(userInfoMapper.selectOne(any())).thenReturn(user);
        when(userInfoMapper.selectCount(any())).thenReturn(0L);

        service.approve(1L, "ok", "admin");

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getRealname()).isEqualTo("ChangedElsewhere");
        assertThat(change.getStatus()).isEqualTo(ProfileChangeStatusConstant.APPROVED);
        verify(userInfoMapper).updateById(user);
    }

    @Test
    void contactOnlyApprovalDoesNotClearAuthCache() {
        UserProfileChange change = change(1L, ProfileChangeStatusConstant.PENDING,
                EMAIL_ORIGINAL, EMAIL_PROPOSED);
        when(userProfileChangeMapper.selectOne(any())).thenReturn(change);
        when(userInfoMapper.selectOne(any())).thenReturn(user());
        when(userInfoMapper.selectCount(any())).thenReturn(0L);

        service.approve(1L, null, "admin");

        // 联系字段不影响鉴权，不做无谓的缓存抖动
        verify(userAuthStateService, never()).afterCommit(any());
        verify(userAuthStateService, never()).deleteUserAuthCache(anyString());
    }

    @Test
    void emailDriftInvalidatesTheRequest() {
        UserProfileChange change = change(1L, ProfileChangeStatusConstant.PENDING,
                EMAIL_ORIGINAL, EMAIL_PROPOSED);
        when(userProfileChangeMapper.selectOne(any())).thenReturn(change);
        UserInfo user = user();
        user.setEmail("taken-over@example.com");
        when(userInfoMapper.selectOne(any())).thenReturn(user);

        assertThatThrownBy(() -> service.approve(1L, null, "admin"))
                .isInstanceOf(BizException.class)
                .hasMessage("用户资料已发生变化，申请已失效");
        verify(userInfoMapper, never()).updateById(any(UserInfo.class));
    }

    @Test
    void identityApprovalStillValidatesAndClearsCache() {
        UserProfileChange change = change(1L, ProfileChangeStatusConstant.PENDING,
                "{\"realname\":\"Old\",\"collegeId\":1,\"grade\":\"2024\",\"classId\":10}",
                "{\"realname\":\"New\",\"collegeId\":1,\"grade\":\"2024\",\"classId\":10}");
        when(userProfileChangeMapper.selectOne(any())).thenReturn(change);
        when(userInfoMapper.selectOne(any())).thenReturn(user());
        stubValidIdentity();

        service.approve(1L, null, "admin");

        assertThat(change.getStatus()).isEqualTo(ProfileChangeStatusConstant.APPROVED);
        verify(sysClassMapper).selectById(10L);
        verify(userAuthStateService).afterCommit(any());
    }

    @Test
    void batchApproveReportsPerIdOutcomeWithoutAbortingOthers() {
        UserProfileChange ok = change(1L, ProfileChangeStatusConstant.PENDING, EMAIL_ORIGINAL, EMAIL_PROPOSED);
        UserProfileChange rejected = change(2L, ProfileChangeStatusConstant.REJECTED, EMAIL_ORIGINAL, EMAIL_PROPOSED);
        when(userProfileChangeMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserProfileChange> wrapper = invocation.getArgument(0);
            wrapper.getTargetSql();
            Long id = (Long) wrapper.getParamNameValuePairs().values().stream()
                    .filter(Long.class::isInstance).findFirst().orElse(null);
            return Long.valueOf(1L).equals(id) ? ok : rejected;
        });
        when(userInfoMapper.selectOne(any())).thenReturn(user());
        when(userInfoMapper.selectCount(any())).thenReturn(0L);

        BatchIdsRequest request = new BatchIdsRequest();
        request.setIds(List.of(1L, 2L, 1L));
        BatchOperationResultVo result = service.batchApprove(request, "admin");

        // 去重后 1 成功、1 失败；失败条目不影响成功条目
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailures()).hasSize(1);
        assertThat(ok.getStatus()).isEqualTo(ProfileChangeStatusConstant.APPROVED);
        assertThat(rejected.getStatus()).isEqualTo(ProfileChangeStatusConstant.REJECTED);
    }

    @Test
    void batchApproveRejectsEmptyIds() {
        BatchIdsRequest request = new BatchIdsRequest();
        request.setIds(List.of());

        assertThatThrownBy(() -> service.batchApprove(request, "admin"))
                .isInstanceOf(BizException.class)
                .hasMessage("ids 不能为空");
    }

    // ---------------- 辅助 ----------------

    private void stubValidIdentity() {
        SysCollege college = new SysCollege();
        college.setId(1L);
        when(sysCollegeMapper.selectById(1L)).thenReturn(college);
        SysClass sysClass = new SysClass();
        sysClass.setId(10L);
        sysClass.setCollegeId(1L);
        sysClass.setGrade("2024");
        when(sysClassMapper.selectById(10L)).thenReturn(sysClass);
        when(sysClassMapper.selectCount(any())).thenReturn(1L);
    }

    private ProfileChangeCreateRequest emailOnlyRequest() {
        ProfileChangeCreateRequest request = new ProfileChangeCreateRequest();
        request.setEmail("new@example.com");
        request.setReason("换邮箱");
        return request;
    }

    private UserProfileChange change(Long id, String status, String original, String proposed) {
        UserProfileChange change = new UserProfileChange();
        change.setId(id);
        change.setUid("u1");
        change.setStatus(status);
        change.setOriginal(original);
        change.setProposed(proposed);
        change.setReason("reason");
        return change;
    }

    private UserInfo user() {
        UserInfo user = new UserInfo();
        user.setUid("u1");
        user.setUsername("alice");
        user.setRealname("Old");
        user.setCollegeId(1L);
        user.setGrade("2024");
        user.setClassId(10L);
        user.setEmail("old@example.com");
        return user;
    }
}
