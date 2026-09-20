package com.hnieacm.user.controller;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.user.dto.RejectUserProfileChangeRequest;
import com.hnieacm.user.dto.UpdatePasswordRequest;
import com.hnieacm.user.dto.UpdateUserProfileRequest;
import com.hnieacm.user.dto.UserProfileChangeApplyRequest;
import com.hnieacm.user.service.UserManageService;
import com.hnieacm.user.service.UserProfileChangeService;
import com.hnieacm.user.service.UserProfileService;
import com.hnieacm.user.vo.UserProfileChangeApplyVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 合并碰撞回归：UserProfileController 必须同时保留
 * 上游 {@code /profile/password}、{@code /profile/change-requests} 与
 * 补丁 {@code /password}、{@code PUT /profile} 两套契约；
 * UserManageController 的上游资料审核路由也不得在合并中丢失。
 * <p>
 * uid 一律取自服务端登录态，上游通用资料申请与补丁身份申请走各自的服务。
 */
class UserProfileControllerMergeCollisionTest {

    private UserProfileService userProfileService;
    private UserProfileChangeService userProfileChangeService;
    private UserProfileController userProfileController;

    @BeforeEach
    void setUp() {
        SaTokenContextMockUtil.clearContext();
        userProfileService = mock(UserProfileService.class);
        userProfileChangeService = mock(UserProfileChangeService.class);
        userProfileController = new UserProfileController(
                userProfileService, mock(UserManageService.class), userProfileChangeService);
    }

    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
    }

    @Test
    void upstreamProfileApplyAndPatchSelfServiceContractsCoexist() {
        UserProfileChangeApplyRequest applyRequest = new UserProfileChangeApplyRequest();
        UserProfileChangeApplyVo applyVo = new UserProfileChangeApplyVo();
        PageVo<UserProfileChangeApplyVo> page = new PageVo<>(List.of(applyVo), 1L);
        when(userProfileChangeService.submit(same("alice"), same(applyRequest))).thenReturn(applyVo);
        when(userProfileChangeService.listMine("alice", 1, 20)).thenReturn(page);

        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("alice");
            // 上游通用资料申请入口。
            assertThat(userProfileController.submitProfileChange(applyRequest).getData()).isSameAs(applyVo);
            assertThat(userProfileController.listMyProfileChanges(1, 20).getData()).isSameAs(page);
            // 补丁本人自助契约同时存在，且分别路由到对应服务。
            userProfileController.updateProfile(new UpdateUserProfileRequest());
            userProfileController.updatePassword(new UpdatePasswordRequest());
        });

        verify(userProfileChangeService).submit("alice", applyRequest);
        verify(userProfileChangeService).listMine("alice", 1, 20);
        verify(userProfileService).updateCurrentUserProfile(same("alice"), any(UpdateUserProfileRequest.class));
        verify(userProfileService).updatePassword(same("alice"), any(UpdatePasswordRequest.class));
    }

    @Test
    void upstreamAdminProfileReviewRoutesStillDelegateToProfileChangeService() {
        RejectUserProfileChangeRequest reject = new RejectUserProfileChangeRequest();
        reject.setReason("资料不完整");

        UserManageController manageController = new UserManageController(
                null, null, null, null, userProfileChangeService);

        manageController.listProfileChanges(1, 20, "alice", "pending");
        manageController.approveProfileChange("alice");
        manageController.rejectProfileChange("alice", reject);

        verify(userProfileChangeService).listForAdmin(1, 20, "alice", "pending");
        verify(userProfileChangeService).approve("alice");
        verify(userProfileChangeService).reject("alice", "资料不完整");
    }
}
