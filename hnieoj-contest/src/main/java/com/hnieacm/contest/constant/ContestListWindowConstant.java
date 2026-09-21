package com.hnieacm.contest.constant;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 比赛列表的时间窗（window）取值。
 * <p>{@link #RECENT} 表示按「距当前时间由近到远」排序，即首页「近期比赛」的产品语义：
 * 过去刚结束的与即将开始的都参与，取距离当前最小的在前。它只改变<b>排序</b>，
 * 不改变可见性过滤；与 {@code startFrom}/{@code startTo} 可叠加使用（后者只做过滤）。</p>
 */
public final class ContestListWindowConstant {

    /** 按距当前时间由近到远排序 */
    public static final String RECENT = "recent";

    private ContestListWindowConstant() {
    }

    /**
     * 校验并归一化 window 参数。
     * <p>非法值抛 400 而不是静默忽略：静默忽略会让 {@code window=recentt} 这类拼写错误
     * 悄悄退回「开始时间最晚」的旧语义，调用方拿到的是看似成功却语义不符的结果。</p>
     *
     * @param window 原始参数，可为 null/空白
     * @return 归一化后的取值；未指定时返回 {@code null}
     */
    public static String normalize(String window) {
        if (window == null || window.isBlank()) {
            return null;
        }
        String normalized = window.trim();
        if (RECENT.equalsIgnoreCase(normalized)) {
            return RECENT;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "window 参数不合法，仅支持 recent");
    }
}
