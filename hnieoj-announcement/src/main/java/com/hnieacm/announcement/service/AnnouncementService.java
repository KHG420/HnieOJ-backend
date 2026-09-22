package com.hnieacm.announcement.service;

import com.hnieacm.announcement.dto.AnnouncementCreateRequest;
import com.hnieacm.announcement.dto.AnnouncementUpdateRequest;
import com.hnieacm.announcement.dto.AnnouncementUpdateStatusRequest;
import com.hnieacm.announcement.vo.AnnouncementDetailVo;
import com.hnieacm.announcement.vo.AnnouncementListVo;
import com.hnieacm.common.dto.PageVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/24
 * @Description: 公告服务
 */
public interface AnnouncementService {

    /**
     * 前台分页查询公告列表（仅公开公告）；category 为空返回全部分类，非空精确过滤。
     *
     * @param page     页码，从 1 开始
     * @param pageSize 每页大小
     * @param keyword  标题关键字，可空
     * @param category 分类过滤，可空
     * @return 分页公告列表
     */
    PageVo<AnnouncementListVo> listPublicAnnouncements(int page, int pageSize, String keyword, String category);

    /**
     * 前台查询公告详情（仅公开公告）。
     *
     * @param id 公告 id
     * @return 公告详情
     */
    AnnouncementDetailVo getPublicAnnouncementDetail(Long id);

    /**
     * 后台查询公告详情（含下线公告，返回真实正文与状态）。
     *
     * @param id 公告 id
     * @return 公告详情
     */
    AnnouncementDetailVo getAdminAnnouncementDetail(Long id);

    /**
     * 后台分页查询公告列表；category 为空不额外过滤，非空精确过滤。
     *
     * @param page     页码，从 1 开始
     * @param pageSize 每页大小
     * @param keyword  标题关键字，可空
     * @param status   状态过滤，可空
     * @param category 分类过滤，可空
     * @return 分页公告列表
     */
    PageVo<AnnouncementListVo> listAdminAnnouncements(int page, int pageSize, String keyword, Integer status,
                                                      String category);

    /**
     * 后台创建公告。
     *
     * @param request 创建参数
     */
    void createAnnouncement(AnnouncementCreateRequest request);

    /**
     * 后台更新公告。
     *
     * @param id      公告 id
     * @param request 更新参数
     */
    void updateAnnouncement(Long id, AnnouncementUpdateRequest request);

    /**
     * 后台删除公告。
     *
     * @param id 公告 id
     */
    void deleteAnnouncement(Long id);

    /**
     * 后台更新公告状态。
     *
     * @param id      公告 id
     * @param request 状态参数
     */
    void updateAnnouncementStatus(Long id, AnnouncementUpdateStatusRequest request);
}
