package com.hnieacm.user.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 按 id 批量的请求体（资料变更申请批量审核用）。
 * <p>资料变更的审批粒度是申请 id，与按 uid 批量的 {@code BatchUidsRequest} 用途不同，故单独定义。</p>
 */
@Data
public class BatchIdsRequest {

    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;
}
