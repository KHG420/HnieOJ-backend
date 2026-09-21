package com.hnieacm.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 提交资料变更申请请求（身份 + 联系/社交字段统一入口）。
 *
 * <p>合并两套流程（BE-03.6 / W5）后，本请求是唯一的资料变更入口：
 * 字段全部可选，<b>只提交需要变更的字段</b>，未提交（null）或与当前值相同的字段不进入申请；
 * {@code reason} 仍为必填。原身份流程要求 realname/collegeId/grade/classId 四项全填，
 * 现在只在「确实要改身份字段」时才需要它们成组出现。</p>
 */
@Data
public class ProfileChangeCreateRequest {

    // ---------------- 身份字段 ----------------
    @Size(max = 50, message = "realname 长度不能超过 50")
    private String realname;

    private Long collegeId;

    @Size(max = 20, message = "grade 长度不能超过 20")
    private String grade;

    private Long classId;

    // ---------------- 联系/社交字段 ----------------
    @Size(max = 100, message = "username 长度不能超过 100")
    private String username;

    @Size(max = 255, message = "email 长度不能超过 255")
    private String email;

    @Size(max = 20, message = "phone 长度不能超过 20")
    private String phone;

    @Size(max = 500, message = "avatar 长度不能超过 500")
    private String avatar;

    @Size(max = 20, message = "qq 长度不能超过 20")
    private String qq;

    @Size(max = 100, message = "cfUsername 长度不能超过 100")
    private String cfUsername;

    @Size(max = 255, message = "github 长度不能超过 255")
    private String github;

    @Size(max = 255, message = "blog 长度不能超过 255")
    private String blog;

    @NotBlank(message = "reason 不能为空")
    @Size(max = 1000, message = "reason 长度不能超过 1000")
    private String reason;
}
