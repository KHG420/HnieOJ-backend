package com.hnieacm.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.common.util.PageParamUtils;
import com.hnieacm.user.constant.UserProfileChangeStatus;
import com.hnieacm.user.dto.BatchUidsRequest;
import com.hnieacm.user.dto.UserProfileChangeApplyRequest;
import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.entity.UserProfileChangeApply;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.SysCollegeMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserProfileChangeApplyMapper;
import com.hnieacm.user.service.UserProfileChangeService;
import com.hnieacm.user.service.manager.UserInfoManager;
import com.hnieacm.user.service.support.UserManageValidator;
import com.hnieacm.user.vo.BatchOperationResultVo;
import com.hnieacm.user.vo.UserProfileChangeApplyVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户资料修改申请服务实现（通用资料流程，按 uid 审批）。
 * <p>流程分工：本流程只受理联系/社交字段（username/email/phone/avatar/qq/cf/github/blog）；
 * 身份字段（realname/college/grade/class）由 {@link ProfileChangeServiceImpl} 的
 * 身份资料变更流程（user_profile_change，按 id 审批，含原值一致性校验）独占受理。
 * 两套流程写同一 user_info 行，审批前都须在事务内锁定用户行。</p>
 */
@Service
@RequiredArgsConstructor
public class UserProfileChangeServiceImpl implements UserProfileChangeService {

    private final UserProfileChangeApplyMapper changeApplyMapper;
    private final UserInfoMapper userInfoMapper;
    private final SysCollegeMapper sysCollegeMapper;
    private final SysClassMapper sysClassMapper;
    private final UserInfoManager userInfoManager;
    private final UserManageValidator userManageValidator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileChangeApplyVo submit(String uid, UserProfileChangeApplyRequest request) {
        String normalizedUid = StrUtil.trimToNull(uid);
        if (normalizedUid == null || request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        userInfoManager.getUserByUid(normalizedUid);
        rejectIdentityFields(request);
        long pendingCount = changeApplyMapper.selectCount(new LambdaQueryWrapper<UserProfileChangeApply>()
                .eq(UserProfileChangeApply::getUid, normalizedUid)
                .eq(UserProfileChangeApply::getStatus, UserProfileChangeStatus.PENDING));
        if (pendingCount > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "存在待审核的资料修改申请");
        }

        if (!hasAnyChange(request)) {
            throw new BizException(ResultCode.BAD_REQUEST, "至少提交一个修改字段");
        }
        validateRequest(request, normalizedUid);
        UserProfileChangeApply apply = buildApply(normalizedUid, request);
        changeApplyMapper.insert(apply);
        return toVo(apply);
    }

    @Override
    public PageVo<UserProfileChangeApplyVo> listMine(String uid, int page, int pageSize) {
        return listByWrapper(page, pageSize, new LambdaQueryWrapper<UserProfileChangeApply>()
                .eq(UserProfileChangeApply::getUid, uid)
                .orderByDesc(UserProfileChangeApply::getGmtCreate)
                .orderByDesc(UserProfileChangeApply::getId));
    }

    @Override
    public PageVo<UserProfileChangeApplyVo> listForAdmin(int page, int pageSize, String uid, String status) {
        LambdaQueryWrapper<UserProfileChangeApply> wrapper = new LambdaQueryWrapper<UserProfileChangeApply>()
                .orderByDesc(UserProfileChangeApply::getGmtCreate)
                .orderByDesc(UserProfileChangeApply::getId);
        String normalizedUid = StrUtil.trimToNull(uid);
        if (normalizedUid != null) {
            wrapper.eq(UserProfileChangeApply::getUid, normalizedUid);
        }
        String normalizedStatus = normalizeStatus(status);
        if (normalizedStatus != null) {
            wrapper.eq(UserProfileChangeApply::getStatus, normalizedStatus);
        }
        return listByWrapper(page, pageSize, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String uid) {
        UserProfileChangeApply apply = getPendingApply(uid);
        rejectLegacyIdentityPayload(apply);
        // 通用资料申请与身份资料申请（UserProfileChange）都会写同一 user_info 行，
        // 审批前必须在事务内锁定用户行，避免两套申请并发审批相互覆盖其它资料/密码。
        UserInfo user = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, apply.getUid()).last("FOR UPDATE")
        );
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }
        applyToUser(user, apply);
        userInfoMapper.updateById(user);
        apply.setStatus(UserProfileChangeStatus.APPROVED);
        apply.setReason(null);
        apply.setReviewerUid(StpUtil.getLoginIdAsString());
        apply.setReviewTime(LocalDateTime.now());
        changeApplyMapper.updateById(apply);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String uid, String reason) {
        String normalizedReason = StrUtil.trimToNull(reason);
        if (normalizedReason == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "reason 不能为空");
        }
        UserProfileChangeApply apply = getPendingApply(uid);
        apply.setStatus(UserProfileChangeStatus.REJECTED);
        apply.setReason(normalizedReason);
        apply.setReviewerUid(StpUtil.getLoginIdAsString());
        apply.setReviewTime(LocalDateTime.now());
        changeApplyMapper.updateById(apply);
    }

    @Override
    public BatchOperationResultVo batchApprove(BatchUidsRequest request) {
        if (request == null || request.getUids() == null || request.getUids().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "uids 不能为空");
        }
        BatchOperationResultVo result = new BatchOperationResultVo();
        request.getUids().stream()
                .map(StrUtil::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(uid -> {
                    try {
                        approve(uid);
                        result.addSuccess();
                    } catch (Exception e) {
                        result.addFailure(uid, e.getMessage());
                    }
                });
        return result;
    }

    private PageVo<UserProfileChangeApplyVo> listByWrapper(int page, int pageSize,
                                                           LambdaQueryWrapper<UserProfileChangeApply> wrapper) {
        PageParamUtils.validate(page, pageSize);
        Page<UserProfileChangeApply> mpPage = new Page<>(page, pageSize);
        Page<UserProfileChangeApply> result = changeApplyMapper.selectPage(mpPage, wrapper);
        if (result.getRecords().isEmpty()) {
            return new PageVo<>(Collections.emptyList(), result.getTotal());
        }
        List<UserProfileChangeApplyVo> list = result.getRecords().stream().map(this::toVo).toList();
        return new PageVo<>(list, result.getTotal());
    }

    private void validateRequest(UserProfileChangeApplyRequest request, String uid) {
        if (StrUtil.isNotBlank(request.getUsername())) {
            userManageValidator.validateUsernameLength(request.getUsername());
        }
        if (StrUtil.isNotBlank(request.getEmail())) {
            long emailCount = userInfoMapper.selectCount(new LambdaQueryWrapper<UserInfo>()
                    .eq(UserInfo::getEmail, request.getEmail())
                    .ne(UserInfo::getUid, uid));
            if (emailCount > 0) {
                throw new BizException(ResultCode.USER_ALREADY_EXISTS, "邮箱已被占用");
            }
        }
        if (request.getCollegeId() != null && sysCollegeMapper.selectById(request.getCollegeId()) == null) {
            throw new BizException(ResultCode.COLLEGE_NOT_FOUND, "学院不存在");
        }
        if (request.getClassId() != null) {
            SysClass sysClass = sysClassMapper.selectById(request.getClassId());
            if (sysClass == null) {
                throw new BizException(ResultCode.CLASS_NOT_FOUND, "班级不存在");
            }
            if (request.getCollegeId() != null && sysClass.getCollegeId() != null
                    && !sysClass.getCollegeId().equals(request.getCollegeId())) {
                throw new BizException(ResultCode.BAD_REQUEST, "班级与学院不匹配");
            }
        }
    }

    /**
     * 旧版通用流程允许一次提交含身份字段的申请；这类历史待审记录不能由本流程批准，
     * 否则会覆盖身份流程的结果。此处显式拒绝并指向正确流程，不做静默跳过。
     */
    private void rejectLegacyIdentityPayload(UserProfileChangeApply apply) {
        if (apply.getCollegeId() != null
                || apply.getClassId() != null
                || StrUtil.isNotBlank(apply.getGrade())
                || StrUtil.isNotBlank(apply.getRealname())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "该申请为旧版通用流程创建且包含身份字段，不能由通用资料流程审批；"
                            + "请驳回该申请，由用户通过身份资料变更申请（/api/user/profile-change-requests）重新提交");
        }
    }

    /**
     * 身份字段由身份资料变更流程独占受理；本流程拒绝携带身份字段的请求，
     * 避免两条流程同时待审、串行审批时互相覆盖同一 user_info 行的身份字段。
     */
    private void rejectIdentityFields(UserProfileChangeApplyRequest request) {
        if (StrUtil.isNotBlank(request.getRealname())
                || request.getCollegeId() != null
                || request.getClassId() != null
                || StrUtil.isNotBlank(request.getGrade())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "实名/学院/年级/班级请通过身份资料变更申请（/api/user/profile-change-requests）提交");
        }
    }

    private boolean hasAnyChange(UserProfileChangeApplyRequest request) {
        return StrUtil.isNotBlank(request.getUsername())
                || StrUtil.isNotBlank(request.getEmail())
                || StrUtil.isNotBlank(request.getPhone())
                || StrUtil.isNotBlank(request.getAvatar())
                || StrUtil.isNotBlank(request.getQq())
                || StrUtil.isNotBlank(request.getCfUsername())
                || StrUtil.isNotBlank(request.getGithub())
                || StrUtil.isNotBlank(request.getBlog());
    }

    private UserProfileChangeApply buildApply(String uid, UserProfileChangeApplyRequest request) {
        UserProfileChangeApply apply = new UserProfileChangeApply();
        apply.setUid(uid);
        apply.setUsername(StrUtil.trimToNull(request.getUsername()));
        apply.setEmail(StrUtil.trimToNull(request.getEmail()));
        apply.setPhone(StrUtil.trimToNull(request.getPhone()));
        apply.setAvatar(StrUtil.trimToNull(request.getAvatar()));
        apply.setQq(StrUtil.trimToNull(request.getQq()));
        apply.setCfUsername(StrUtil.trimToNull(request.getCfUsername()));
        apply.setGithub(StrUtil.trimToNull(request.getGithub()));
        apply.setBlog(StrUtil.trimToNull(request.getBlog()));
        apply.setStatus(UserProfileChangeStatus.PENDING);
        return apply;
    }

    private UserProfileChangeApply getPendingApply(String uid) {
        String normalizedUid = StrUtil.trimToNull(uid);
        if (normalizedUid == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }
        UserProfileChangeApply apply = changeApplyMapper.selectOne(new LambdaQueryWrapper<UserProfileChangeApply>()
                .eq(UserProfileChangeApply::getUid, normalizedUid)
                .eq(UserProfileChangeApply::getStatus, UserProfileChangeStatus.PENDING)
                .orderByDesc(UserProfileChangeApply::getGmtCreate)
                .last("limit 1"));
        if (apply == null) {
            throw new BizException(ResultCode.NOT_FOUND, "待审核申请不存在");
        }
        return apply;
    }

    private void applyToUser(UserInfo user, UserProfileChangeApply apply) {
        if (apply.getUsername() != null) {
            user.setUsername(apply.getUsername());
        }
        if (apply.getEmail() != null) {
            user.setEmail(apply.getEmail());
        }
        if (apply.getPhone() != null) {
            user.setPhone(apply.getPhone());
        }
        if (apply.getAvatar() != null) {
            user.setAvatar(apply.getAvatar());
        }
        // 身份字段（realname/collegeId/grade/classId）由 ProfileChangeServiceImpl 独占受理，
        // 本流程一律不写，避免两条流程串行审批时互相覆盖。
        if (apply.getQq() != null) {
            user.setQq(apply.getQq());
        }
        if (apply.getCfUsername() != null) {
            user.setCfUsername(apply.getCfUsername());
        }
        if (apply.getGithub() != null) {
            user.setGithub(apply.getGithub());
        }
        if (apply.getBlog() != null) {
            user.setBlog(apply.getBlog());
        }
    }

    private String normalizeStatus(String status) {
        String normalized = StrUtil.trimToNull(status);
        if (normalized == null) {
            return null;
        }
        if (UserProfileChangeStatus.PENDING.equalsIgnoreCase(normalized)) {
            return UserProfileChangeStatus.PENDING;
        }
        if (UserProfileChangeStatus.APPROVED.equalsIgnoreCase(normalized)) {
            return UserProfileChangeStatus.APPROVED;
        }
        if (UserProfileChangeStatus.REJECTED.equalsIgnoreCase(normalized)) {
            return UserProfileChangeStatus.REJECTED;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "status 仅支持 pending/approved/rejected");
    }

    private UserProfileChangeApplyVo toVo(UserProfileChangeApply apply) {
        UserProfileChangeApplyVo vo = new UserProfileChangeApplyVo();
        vo.setId(apply.getId());
        vo.setUid(apply.getUid());
        vo.setUsername(apply.getUsername());
        vo.setEmail(apply.getEmail());
        vo.setPhone(apply.getPhone());
        vo.setAvatar(apply.getAvatar());
        vo.setCollegeId(apply.getCollegeId());
        vo.setClassId(apply.getClassId());
        vo.setGrade(apply.getGrade());
        vo.setRealname(apply.getRealname());
        vo.setQq(apply.getQq());
        vo.setCfUsername(apply.getCfUsername());
        vo.setGithub(apply.getGithub());
        vo.setBlog(apply.getBlog());
        vo.setStatus(apply.getStatus());
        vo.setReason(apply.getReason());
        vo.setReviewerUid(apply.getReviewerUid());
        vo.setReviewTime(apply.getReviewTime());
        vo.setGmtCreate(apply.getGmtCreate());
        vo.setGmtModified(apply.getGmtModified());
        return vo;
    }
}
