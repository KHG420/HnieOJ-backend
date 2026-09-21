package com.hnieacm.user.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.user.dto.BatchIdsRequest;
import com.hnieacm.user.dto.ProfileChangeCreateRequest;
import com.hnieacm.user.vo.BatchOperationResultVo;
import com.hnieacm.user.vo.ProfileChangeVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 用户资料变更申请服务（唯一的资料变更流程，按申请 id 审批）。
 * <p>合并两套流程（BE-03.6 / W5）后，本服务受理全部可变更字段：
 * 身份字段（实名/学院/年级/班级）+ 联系/社交字段（用户名/邮箱/手机号/头像/QQ/CF/GitHub/博客）。</p>
 */
public interface ProfileChangeService {

    /**
     * 本人提交变更申请；用户行锁保证同一用户仅存在一条待审申请。
     *
     * @param uid     本人登录态 uid，由服务端会话提供
     * @param request 变更申请参数，只提交需要变更的字段（身份与联系/社交字段均可）与申请原因
     */
    void createChangeRequest(String uid, ProfileChangeCreateRequest request);

    /**
     * 本人变更申请分页。
     *
     * @param uid      本人登录态 uid，由服务端会话提供
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数，必须大于 0 且不超过 100
     * @return 按创建时间倒序的本人变更申请分页结果
     */
    PageVo<ProfileChangeVo> listMyChangeRequests(String uid, int page, int pageSize);

    /**
     * 管理员变更申请分页，支持 status 与 keyword（uid/reason）过滤。
     *
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数，必须大于 0 且不超过 100
     * @param status   申请状态过滤（PENDING/APPROVED/REJECTED），为空表示不按状态过滤
     * @param keyword  按申请人 uid 或申请原因模糊匹配的关键字，为空表示不按关键字过滤
     * @return 按创建时间倒序的变更申请分页结果
     */
    PageVo<ProfileChangeVo> listAdminChangeRequests(int page, int pageSize, String status, String keyword);

    /**
     * 审核通过：校验原值仍一致后原子更新身份与审核信息；重复同结果幂等，反向审核拒绝。
     *
     * @param id          变更申请 id
     * @param reason      审核备注，可为空
     * @param reviewerUid 审核人 uid，由服务端登录态提供
     */
    void approve(Long id, String reason, String reviewerUid);

    /**
     * 审核驳回：仅更新申请状态与驳回信息；重复同结果幂等，反向审核拒绝。
     *
     * @param id          变更申请 id
     * @param reason      驳回原因，不能为空
     * @param reviewerUid 审核人 uid，由服务端登录态提供
     */
    void reject(Long id, String reason, String reviewerUid);

    /**
     * 批量审核通过（按申请 id）。
     * <p>逐条独立审批，单条失败不影响其它条目，结果里分别列出成功数与失败原因；
     * 某条失败不改变其它条目的状态，也不回滚已成功的条目。</p>
     *
     * @param request     待审批的申请 id 列表
     * @param reviewerUid 审核人 uid，由服务端登录态提供
     * @return 成功/失败明细
     */
    BatchOperationResultVo batchApprove(BatchIdsRequest request, String reviewerUid);
}
