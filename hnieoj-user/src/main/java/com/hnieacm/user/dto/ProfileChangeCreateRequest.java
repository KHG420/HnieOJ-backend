package com.hnieacm.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 提交资料变更申请请求（身份字段 + 联系/社交字段）。
 *
 * <p>字段全部可选，<b>只提交需要变更的字段</b>：未提交（null）或与当前值相同的字段不进入申请。
 * {@code reason} 必填。</p>
 */
@Data
public class ProfileChangeCreateRequest {

    /** 身份字段：按需提交，与当前资料快照合并后整体校验身份四项 */
    @Size(max = 50, message = "realname 长度不能超过 50")
    private String realname;

    private Long collegeId;

    @Size(max = 20, message = "grade 长度不能超过 20")
    private String grade;

    private Long classId;

    /** 联系/社交字段：与身份字段同属一个申请，可单独提交 */
    @Size(max = 100, message = "username 长度不能超过 100")
    private String username;

    @Email(message = "email 格式不正确")
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
