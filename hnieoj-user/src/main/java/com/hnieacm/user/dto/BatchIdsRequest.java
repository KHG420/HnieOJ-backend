package com.hnieacm.user.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 按 id 批量的请求体（资料变更申请批量审核用）。
 * <p>合并两套流程后审批粒度是「按申请 id」，不再有按 uid 的批量审批，
 * 因此与既有的 {@code BatchUidsRequest} 分开。</p>
 */
@Data
public class BatchIdsRequest {

    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;
}
