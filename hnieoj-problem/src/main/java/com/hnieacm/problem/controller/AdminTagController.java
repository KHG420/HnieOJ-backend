package com.hnieacm.problem.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.problem.dto.SaveTagConfigRequest;
import com.hnieacm.problem.dto.TagSaveRequest;
import com.hnieacm.problem.service.ProblemTagConfigService;
import com.hnieacm.problem.service.TagService;
import com.hnieacm.problem.vo.TagGroupVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 管理端标签接口：保留上游 GET/PUT 根路径分组配置契约，并合入标签增删改。
 * <p>写操作由服务内 SaInterceptor 执行方法上的权限注解；增删改另保留显式角色+权限校验作双保险。</p>
 */
@Tag(name = "题目标签管理模块")
@Validated
@RestController
@RequestMapping("/api/admin/tags")
@RequiredArgsConstructor
public class AdminTagController {

    private final ProblemTagConfigService problemTagConfigService;
    private final TagService tagService;

    @Operation(summary = "获取标签列表")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @GetMapping
    public Result<List<TagGroupVo>> list() {
        return Result.success(problemTagConfigService.list());
    }

    @Operation(summary = "保存标签配置")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @PutMapping
    public Result<Void> save(@Valid @RequestBody SaveTagConfigRequest request) {
        problemTagConfigService.save(request);
        return Result.success("保存成功", null);
    }

    @Operation(summary = "创建标签")
    @SaCheckPermission(PermissionConstant.PROBLEM_CREATE)
    @PostMapping
    public Result<Void> create(@Valid @RequestBody TagSaveRequest request) {
        checkAdminRole();
        StpUtil.checkPermission(PermissionConstant.PROBLEM_CREATE);
        tagService.createTag(request);
        return Result.success("创建成功", null);
    }

    @Operation(summary = "更新标签")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Min(value = 1, message = "id 必须大于 0") Long id,
                               @Valid @RequestBody TagSaveRequest request) {
        checkAdminRole();
        StpUtil.checkPermission(PermissionConstant.PROBLEM_UPDATE);
        tagService.updateTag(id, request);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除标签")
    @SaCheckPermission(PermissionConstant.PROBLEM_DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(value = 1, message = "id 必须大于 0") Long id) {
        checkAdminRole();
        StpUtil.checkPermission(PermissionConstant.PROBLEM_DELETE);
        tagService.deleteTag(id);
        return Result.success("删除成功", null);
    }

    private void checkAdminRole() {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
    }
}
