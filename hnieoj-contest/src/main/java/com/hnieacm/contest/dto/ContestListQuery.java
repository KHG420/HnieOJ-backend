package com.hnieacm.contest.dto;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 比赛列表查询条件（对外 GET /api/contests 的入参聚合）。
 *
 * @param page      页码，>= 1
 * @param pageSize  每页条数，>= 1
 * @param type      赛制过滤，ACM/OI，null 表示不过滤
 * @param auth      权限过滤，Public/Private，null 表示不过滤
 * @param startFrom 开始时间下界（epoch 毫秒，含），null 表示不限
 * @param startTo   开始时间上界（epoch 毫秒，含），null 表示不限
 * @param window    排序口径，仅支持 {@code recent}（距当前由近到远），null 表示默认按开始时间倒序
 */
public record ContestListQuery(int page,
                               int pageSize,
                               String type,
                               String auth,
                               Long startFrom,
                               Long startTo,
                               String window) {
}
