package com.hnieacm.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.common.util.PageParamUtils;
import com.hnieacm.user.constant.ProfileChangeField;
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
import com.hnieacm.user.service.ProfileChangeService;
import com.hnieacm.user.service.support.UserAuthStateService;
import com.hnieacm.user.service.support.UserManageValidator;
import com.hnieacm.user.vo.BatchOperationResultVo;
import com.hnieacm.user.vo.ProfileChangeVo;
import com.hnieacm.user.vo.ProfileSnapshotVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 用户资料变更申请服务实现（**唯一**的资料变更流程，按 id 审批）。
 *
 * <h3>合并背景（BE-03.6 / W5）</h3>
 * 原先两套流程并存：本流程（表 user_profile_change，按 id 审）独占身份字段；
 * UserProfileChangeServiceImpl（表 user_profile_change_apply，按 uid 审）受理联系/社交字段。
 * 两条待审记录可先后覆盖同一 user_info 行。现在收敛为本流程一处：
 * <ul>
 *   <li>字段全集见 {@link ProfileChangeField}（4 个身份 + 8 个联系/社交）；</li>
 *   <li>original/proposed 用 {@link ProfileSnapshotVo} 全量快照，历史身份 JSON 仍可反序列化；</li>
 *   <li>只有 original 与 proposed 不同的字段才算「本申请要改的字段」，逐字段做原值一致性校验并写回，
 *       因此别的申请改了无关字段不会让本条申请误判失效，两条流程互相覆盖的问题从根上消失；</li>
 *   <li>审批粒度仍是申请 id，批量审批按 id 列表（{@link #batchApprove}）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileChangeServiceImpl implements ProfileChangeService {

    private static final int MAX_REASON_LENGTH = 1000;

    private final UserProfileChangeMapper userProfileChangeMapper;
    private final UserInfoMapper userInfoMapper;
    private final SysCollegeMapper sysCollegeMapper;
    private final SysClassMapper sysClassMapper;
    private final UserAuthStateService userAuthStateService;
    private final UserManageValidator userManageValidator;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createChangeRequest(String uid, ProfileChangeCreateRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        // 用户行锁：同一用户并发提交串行化，保证仅一条待审申请。
        UserInfo user = lockUser(uid);
        ensureNoPendingRequest(uid);

        String reason = StrUtil.trim(request.getReason());
        if (StrUtil.isBlank(reason)) {
            throw new BizException(ResultCode.BAD_REQUEST, "reason 不能为空");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "reason 长度不能超过 1000");
        }

        ProfileSnapshotVo original = ProfileSnapshotVo.of(user);
        ProfileSnapshotVo proposed = ProfileSnapshotVo.of(user);
        mergeRequest(proposed, request);

        Set<ProfileChangeField> changedFields = original.changedFields(proposed);
        if (changedFields.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "至少提交一个修改字段");
        }
        validateProposed(changedFields, proposed, uid);

        UserProfileChange change = new UserProfileChange();
        change.setUid(uid);
        change.setOriginal(writeJson(original));
        change.setProposed(writeJson(proposed));
        change.setReason(reason);
        change.setStatus(ProfileChangeStatusConstant.PENDING);
        userProfileChangeMapper.insert(change);
        log.info("Profile change request created, id: {}, uid: {}, fields: {}",
                change.getId(), uid, changedFields);
    }

    @Override
    public PageVo<ProfileChangeVo> listMyChangeRequests(String uid, int page, int pageSize) {
        PageParamUtils.validate(page, pageSize);
        LambdaQueryWrapper<UserProfileChange> wrapper = new LambdaQueryWrapper<UserProfileChange>()
                .eq(UserProfileChange::getUid, uid)
                .orderByDesc(UserProfileChange::getGmtCreate, UserProfileChange::getId);
        return queryPage(page, pageSize, wrapper);
    }

    @Override
    public PageVo<ProfileChangeVo> listAdminChangeRequests(int page, int pageSize, String status, String keyword) {
        PageParamUtils.validate(page, pageSize);

        String normalizedStatus = null;
        if (status != null && !status.trim().isEmpty()) {
            normalizedStatus = ProfileChangeStatusConstant.normalize(status);
            if (normalizedStatus == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "status 只能为 PENDING/APPROVED/REJECTED");
            }
        }

        LambdaQueryWrapper<UserProfileChange> wrapper = new LambdaQueryWrapper<>();
        if (normalizedStatus != null) {
            wrapper.eq(UserProfileChange::getStatus, normalizedStatus);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(UserProfileChange::getUid, kw)
                    .or()
                    .like(UserProfileChange::getReason, kw));
        }
        wrapper.orderByDesc(UserProfileChange::getGmtCreate, UserProfileChange::getId);
        return queryPage(page, pageSize, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String reason, String reviewerUid) {
        // 统一锁序：先锁申请行，再锁用户行，避免与其它审核事务死锁。
        UserProfileChange change = requireLockedChange(id);
        if (ProfileChangeStatusConstant.APPROVED.equals(change.getStatus())) {
            return;
        }
        if (ProfileChangeStatusConstant.REJECTED.equals(change.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请已驳回，不能再次通过");
        }

        UserInfo user = lockUser(change.getUid());

        ProfileSnapshotVo original = readJson(change.getOriginal());
        ProfileSnapshotVo proposed = readJson(change.getProposed());
        Set<ProfileChangeField> changedFields = original.changedFields(proposed);
        if (changedFields.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请内容不包含任何字段变更，无法通过");
        }

        // 逐字段原值一致性：只有当用户当前值仍等于申请时的原值才允许写回，
        // 否则说明期间已有其它变更落库，本次直接作废而不是覆盖。
        for (ProfileChangeField field : changedFields) {
            if (!original.matchesUserField(field, user)) {
                throw new BizException(ResultCode.BAD_REQUEST, "用户资料已发生变化，申请已失效");
            }
        }
        validateProposed(changedFields, proposed, change.getUid());

        for (ProfileChangeField field : changedFields) {
            proposed.applyTo(user, field);
        }
        userInfoMapper.updateById(user);

        LocalDateTime now = LocalDateTime.now();
        change.setStatus(ProfileChangeStatusConstant.APPROVED);
        change.setReviewerUid(reviewerUid);
        change.setReviewReason(normalizeReviewReason(reason));
        change.setReviewAt(now);
        // 置 null 后 MyBatis-Plus 不写入该列，交由 DDL 的 ON UPDATE CURRENT_TIMESTAMP 维护
        change.setGmtModified(null);
        userProfileChangeMapper.updateById(change);

        // 身份信息变更会影响鉴权展示/缓存，提交后清理（不改动任何角色/权限行）；
        // 纯联系/社交字段变更不影响鉴权，不做无谓的缓存抖动。
        if (changedFields.stream().anyMatch(ProfileChangeField::isIdentity)) {
            userAuthStateService.afterCommit(() -> userAuthStateService.deleteUserAuthCache(change.getUid()));
        }
        log.info("Profile change approved, id: {}, uid: {}, reviewer: {}, fields: {}",
                id, change.getUid(), reviewerUid, changedFields);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason, String reviewerUid) {
        UserProfileChange change = requireLockedChange(id);
        if (ProfileChangeStatusConstant.REJECTED.equals(change.getStatus())) {
            return;
        }
        if (ProfileChangeStatusConstant.APPROVED.equals(change.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请已通过，不能再次驳回");
        }

        String normalizedReason = normalizeReviewReason(reason);
        if (normalizedReason == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "驳回原因不能为空");
        }

        change.setStatus(ProfileChangeStatusConstant.REJECTED);
        change.setReviewerUid(reviewerUid);
        change.setReviewReason(normalizedReason);
        change.setReviewAt(LocalDateTime.now());
        change.setGmtModified(null);
        userProfileChangeMapper.updateById(change);
        log.info("Profile change rejected, id: {}, uid: {}, reviewer: {}", id, change.getUid(), reviewerUid);
    }

    @Override
    public BatchOperationResultVo batchApprove(BatchIdsRequest request, String reviewerUid) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "ids 不能为空");
        }
        BatchOperationResultVo result = new BatchOperationResultVo();
        request.getIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(id -> {
                    try {
                        approve(id, null, reviewerUid);
                        result.addSuccess();
                    } catch (Exception e) {
                        result.addFailure(String.valueOf(id), e.getMessage());
                    }
                });
        return result;
    }

    private UserInfo lockUser(String uid) {
        UserInfo user = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, uid).last("FOR UPDATE")
        );
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void ensureNoPendingRequest(String uid) {
        Long pendingCount = userProfileChangeMapper.selectCount(
                new LambdaQueryWrapper<UserProfileChange>()
                        .eq(UserProfileChange::getUid, uid)
                        .eq(UserProfileChange::getStatus, ProfileChangeStatusConstant.PENDING)
        );
        if (pendingCount != null && pendingCount > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "已存在待审核的变更申请");
        }
    }

    /**
     * 把请求里**显式提供**的字段写入 proposed；未提供（null）的字段保持为用户当前值，
     * 因此不会进入「本申请要改的字段」。
     */
    private void mergeRequest(ProfileSnapshotVo proposed, ProfileChangeCreateRequest request) {
        putIfPresent(proposed, ProfileChangeField.REALNAME, StrUtil.trimToNull(request.getRealname()));
        putIfPresent(proposed, ProfileChangeField.COLLEGE_ID, request.getCollegeId());
        putIfPresent(proposed, ProfileChangeField.GRADE, StrUtil.trimToNull(request.getGrade()));
        putIfPresent(proposed, ProfileChangeField.CLASS_ID, request.getClassId());
        putIfPresent(proposed, ProfileChangeField.USERNAME, StrUtil.trimToNull(request.getUsername()));
        putIfPresent(proposed, ProfileChangeField.EMAIL, StrUtil.trimToNull(request.getEmail()));
        putIfPresent(proposed, ProfileChangeField.PHONE, StrUtil.trimToNull(request.getPhone()));
        putIfPresent(proposed, ProfileChangeField.AVATAR, StrUtil.trimToNull(request.getAvatar()));
        putIfPresent(proposed, ProfileChangeField.QQ, StrUtil.trimToNull(request.getQq()));
        putIfPresent(proposed, ProfileChangeField.CF_USERNAME, StrUtil.trimToNull(request.getCfUsername()));
        putIfPresent(proposed, ProfileChangeField.GITHUB, StrUtil.trimToNull(request.getGithub()));
        putIfPresent(proposed, ProfileChangeField.BLOG, StrUtil.trimToNull(request.getBlog()));
    }

    private void putIfPresent(ProfileSnapshotVo proposed, ProfileChangeField field, Object value) {
        if (value != null) {
            proposed.put(field, value);
        }
    }

    /**
     * 按待变更字段做校验。
     * <p>身份字段只要有一个要改，就按原身份流程的口径整体校验（实名/学院/年级/班级四项齐全且互相匹配），
     * 因为 proposed 里的这四项始终是完整值。</p>
     */
    private void validateProposed(Set<ProfileChangeField> changedFields, ProfileSnapshotVo proposed, String uid) {
        boolean identityChanged = changedFields.stream().anyMatch(ProfileChangeField::isIdentity);
        if (identityChanged) {
            validateIdentity(proposed.getRealname(), proposed.getCollegeId(),
                    proposed.getGrade(), proposed.getClassId());
        }
        for (ProfileChangeField field : changedFields) {
            Object value = proposed.valueOf(field);
            if (value instanceof String text && field.getDdlMaxLength() != null
                    && text.length() > field.getDdlMaxLength()) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        field.getKey() + " 长度不能超过 " + field.getDdlMaxLength());
            }
        }
        if (changedFields.contains(ProfileChangeField.USERNAME)) {
            userManageValidator.validateUsernameLength(proposed.getUsername());
        }
        if (changedFields.contains(ProfileChangeField.EMAIL)) {
            Long emailCount = userInfoMapper.selectCount(new LambdaQueryWrapper<UserInfo>()
                    .eq(UserInfo::getEmail, proposed.getEmail())
                    .ne(UserInfo::getUid, uid));
            if (emailCount != null && emailCount > 0) {
                throw new BizException(ResultCode.USER_ALREADY_EXISTS, "邮箱已被占用");
            }
        }
    }

    /**
     * 校验实名/学院/年级/班级归属（原身份流程口径，保持不变）。
     */
    private void validateIdentity(String realname, Long collegeId, String grade, Long classId) {
        String trimmedRealname = StrUtil.trim(realname);
        if (StrUtil.isBlank(trimmedRealname)) {
            throw new BizException(ResultCode.BAD_REQUEST, "realname 不能为空");
        }
        if (trimmedRealname.length() > 50) {
            throw new BizException(ResultCode.BAD_REQUEST, "realname 长度不能超过 50");
        }
        String trimmedGrade = StrUtil.trim(grade);
        if (StrUtil.isBlank(trimmedGrade)) {
            throw new BizException(ResultCode.BAD_REQUEST, "grade 不能为空");
        }
        if (trimmedGrade.length() > 20) {
            throw new BizException(ResultCode.BAD_REQUEST, "grade 长度不能超过 20");
        }
        if (collegeId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "collegeId 不能为空");
        }
        if (classId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "classId 不能为空");
        }

        SysCollege college = sysCollegeMapper.selectById(collegeId);
        if (college == null) {
            throw new BizException(ResultCode.COLLEGE_NOT_FOUND, "学院不存在");
        }
        SysClass sysClass = sysClassMapper.selectById(classId);
        if (sysClass == null) {
            throw new BizException(ResultCode.CLASS_NOT_FOUND, "班级不存在");
        }
        if (sysClass.getCollegeId() != null && !sysClass.getCollegeId().equals(collegeId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "班级与学院不匹配");
        }
        if (StrUtil.isNotBlank(sysClass.getGrade()) && !sysClass.getGrade().equals(trimmedGrade)) {
            throw new BizException(ResultCode.BAD_REQUEST, "班级与年级不匹配");
        }
        Long gradeCount = sysClassMapper.selectCount(
                new LambdaQueryWrapper<SysClass>()
                        .eq(SysClass::getCollegeId, collegeId)
                        .eq(SysClass::getGrade, trimmedGrade)
        );
        if (gradeCount == null || gradeCount == 0) {
            throw new BizException(ResultCode.GRADE_NOT_FOUND, "该学院下不存在该年级");
        }
    }

    private UserProfileChange requireLockedChange(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不能为空");
        }
        UserProfileChange change = userProfileChangeMapper.selectOne(
                new LambdaQueryWrapper<UserProfileChange>().eq(UserProfileChange::getId, id).last("FOR UPDATE")
        );
        if (change == null) {
            throw new BizException(ResultCode.NOT_FOUND, "变更申请不存在");
        }
        return change;
    }

    private String normalizeReviewReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "reason 长度不能超过 1000");
        }
        return trimmed;
    }

    private PageVo<ProfileChangeVo> queryPage(int page, int pageSize,
                                              LambdaQueryWrapper<UserProfileChange> wrapper) {
        Page<UserProfileChange> mpPage = new Page<>(page, pageSize);
        Page<UserProfileChange> result = userProfileChangeMapper.selectPage(mpPage, wrapper);
        List<ProfileChangeVo> list = result.getRecords().stream().map(this::toVo).toList();
        return new PageVo<>(list, result.getTotal());
    }

    private String writeJson(ProfileSnapshotVo snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "资料快照序列化失败");
        }
    }

    private ProfileSnapshotVo readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProfileSnapshotVo.class);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "资料快照解析失败");
        }
    }

    private ProfileChangeVo toVo(UserProfileChange change) {
        ProfileChangeVo vo = new ProfileChangeVo();
        vo.setId(change.getId());
        vo.setUid(change.getUid());
        vo.setOriginal(readJson(change.getOriginal()));
        vo.setProposed(readJson(change.getProposed()));
        vo.setReason(change.getReason());
        vo.setStatus(change.getStatus());
        vo.setReviewerUid(change.getReviewerUid());
        vo.setReviewReason(change.getReviewReason());
        vo.setReviewAt(change.getReviewAt());
        vo.setGmtCreate(change.getGmtCreate());
        vo.setGmtModified(change.getGmtModified());
        return vo;
    }
}
