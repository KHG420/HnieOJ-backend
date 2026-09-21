package com.hnieacm.problem.dto;

import com.hnieacm.problem.constant.TagFieldConstant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 标签请求（创建与更新共用）。
 * <p>原 {@code TagCreateRequest} 与 {@code TagUpdateRequest} 字段与校验完全相同，已合并为本类：
 * 创建（{@code POST /api/admin/tags}）与更新（{@code PUT /api/admin/tags/{id}}）的字段契约本就一致，
 * 分开只带来两份必须同步维护的副本。</p>
 * <p>长度上限与超限文案统一引用 {@link TagFieldConstant}，与 Service 校验、DDL 列长度由
 * {@code TagFieldConsistencyTest} 断言一致。</p>
 */
@Data
public class TagSaveRequest {

    @NotBlank(message = "name 不能为空")
    @Size(max = TagFieldConstant.NAME_LENGTH, message = TagFieldConstant.NAME_LENGTH_MESSAGE)
    private String name;

    @Size(max = TagFieldConstant.COLOR_LENGTH, message = TagFieldConstant.COLOR_LENGTH_MESSAGE)
    private String color;

    @Size(max = TagFieldConstant.CATEGORY_LENGTH, message = TagFieldConstant.CATEGORY_LENGTH_MESSAGE)
    private String category;
}
