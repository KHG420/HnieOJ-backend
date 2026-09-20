package com.hnieacm.user.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.user.vo.UserMessageVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 本人站内消息服务。所有操作都必须由服务端登录态 uid 驱动，不接受客户端 ownerUid。
 */
public interface UserMessageService {

    /**
     * 本人收件箱分页；unread 为 true 时仅返回未读。
     *
     * @param uid      本人登录态 uid，由服务端会话提供
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数，必须大于 0 且不超过 100
     * @param unread   为 true 时仅返回未读消息，其他取值返回全部未删除消息
     * @return 按创建时间倒序的本人消息分页结果
     */
    PageVo<UserMessageVo> listMyMessages(String uid, int page, int pageSize, Boolean unread);

    /**
     * 本人未读数（排除已删除）。
     *
     * @param uid 本人登录态 uid，由服务端会话提供
     * @return 该用户未读且未删除的消息数量
     */
    long countUnread(String uid);

    /**
     * 标记本人消息已读；重复标记幂等，非本人消息按不存在处理。
     *
     * @param uid       本人登录态 uid，由服务端会话提供
     * @param messageId 消息 id
     */
    void markRead(String uid, Long messageId);

    /**
     * 本人全部未读标记已读。
     *
     * @param uid 本人登录态 uid，由服务端会话提供
     */
    void markAllRead(String uid);

    /**
     * 本人软删除消息；重复删除幂等，非本人消息按不存在处理。
     *
     * @param uid       本人登录态 uid，由服务端会话提供
     * @param messageId 消息 id
     */
    void deleteMessage(String uid, Long messageId);
}
