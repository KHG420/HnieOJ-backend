package com.hnieacm.user.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.user.dto.NoticeSaveRequest;
import com.hnieacm.user.vo.UserNoticeDetailVo;
import com.hnieacm.user.vo.UserNoticeListVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 定向通知管理服务（ADMIN/ROOT）
 */
public interface NoticeAdminService {

    /**
     * 分页查询通知（草稿+已发布），支持标题 keyword 与 status 过滤。
     *
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数，必须大于 0 且不超过 100
     * @param keyword  标题模糊匹配关键字，为空表示不按标题过滤
     * @param status   通知状态过滤（DRAFT/PUBLISHED），为空表示不按状态过滤
     * @return 按创建时间倒序的通知分页结果
     */
    PageVo<UserNoticeListVo> listNotices(int page, int pageSize, String keyword, String status);

    /**
     * 通知详情，返回已保存的目标设置（targetType + targetIds）。
     *
     * @param id 通知 id
     * @return 通知详情，含正文与已保存的目标设置
     */
    UserNoticeDetailVo getNotice(Long id);

    /**
     * 新建通知草稿，返回通知 id。
     *
     * @param request    通知保存参数，含标题、正文与目标设置
     * @param creatorUid 创建人 uid，由服务端登录态提供
     * @return 新建通知的 id
     */
    Long createNotice(NoticeSaveRequest request, String creatorUid);

    /**
     * 编辑草稿通知；已发布通知拒绝编辑正文与目标。
     *
     * @param id      通知 id
     * @param request 通知保存参数，含标题、正文与目标设置
     */
    void updateNotice(Long id, NoticeSaveRequest request);

    /**
     * 删除通知管理记录，不级联删除已送达的 user_message。
     *
     * @param id 通知 id
     */
    void deleteNotice(Long id);

    /**
     * 发布通知：锁定通知行，按当时班级/用户快照写入消息，原子置为 PUBLISHED；重复发布幂等。
     *
     * @param id 通知 id
     */
    void publishNotice(Long id);
}
