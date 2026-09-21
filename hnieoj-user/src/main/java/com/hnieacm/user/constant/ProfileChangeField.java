package com.hnieacm.user.constant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 资料变更申请可受理的字段全集（身份字段 + 联系/社交字段）。
 * <p>这是「可申请变更的字段」的唯一清单：{@code ProfileSnapshotVo} 的字段、申请里能出现的字段、
 * 审批时逐字段做原值一致性校验的字段都以本枚举为准。</p>
 *
 * <p>{@code ddlMaxLength} 是 {@code user_info} 对应列宽，供一致性测试与 DDL 对齐；
 * 业务校验另见 {@code ProfileChangeServiceImpl}（username 走可配置的 {@code UserManageProperties}，
 * 比列宽更严格）。</p>
 */
public enum ProfileChangeField {

    // ---------------- 身份字段（变更后需清理鉴权缓存） ----------------
    REALNAME("realname", "实名", 50, true),
    COLLEGE_ID("collegeId", "学院", null, true),
    GRADE("grade", "年级", 20, true),
    CLASS_ID("classId", "班级", null, true),

    // ---------------- 联系/社交字段 ----------------
    USERNAME("username", "用户名", 100, false),
    EMAIL("email", "邮箱", 255, false),
    PHONE("phone", "手机号", 20, false),
    AVATAR("avatar", "头像", 500, false),
    QQ("qq", "QQ", 20, false),
    CF_USERNAME("cfUsername", "Codeforces", 100, false),
    GITHUB("github", "GitHub", 255, false),
    BLOG("blog", "博客", 255, false);

    private final String key;
    private final String label;
    private final Integer ddlMaxLength;
    private final boolean identity;

    ProfileChangeField(String key, String label, Integer ddlMaxLength, boolean identity) {
        this.key = key;
        this.label = label;
        this.ddlMaxLength = ddlMaxLength;
        this.identity = identity;
    }

    /** JSON 字段名，与 {@code ProfileSnapshotVo} 的属性名及历史数据保持一致 */
    public String getKey() {
        return key;
    }

    /** 界面展示用的中文名 */
    public String getLabel() {
        return label;
    }

    /** user_info 列宽；无长度限制的 id 类字段为 null */
    public Integer getDdlMaxLength() {
        return ddlMaxLength;
    }

    /** 是否为身份字段（决定审批通过后是否清理鉴权缓存） */
    public boolean isIdentity() {
        return identity;
    }
}
