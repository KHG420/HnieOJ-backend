package com.hnieacm.user.vo;

import com.hnieacm.user.constant.ProfileChangeField;
import com.hnieacm.user.entity.UserInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 用户资料快照（身份 + 联系/社交字段），用作变更申请的 original / proposed JSON。
 *
 * <p>JSON 字段名是对外契约：{@code original}/{@code proposed} 里缺失的字段一律反序列化为 null，
 * 且两侧同为 null 视为「未申请变更」。因此本类的<b>属性名与已有数据必须保持一致</b>，
 * 更名会读不出既有申请。</p>
 *
 * <p>字段比较与写回都以 {@link ProfileChangeField} 为准，避免「快照字段」与「可变更字段」
 * 两份清单漂移。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileSnapshotVo {

    private String realname;

    private Long collegeId;

    private String grade;

    private Long classId;

    private String username;

    private String email;

    private String phone;

    private String avatar;

    private String qq;

    private String cfUsername;

    private String github;

    private String blog;

    /** 取用户当前资料的完整快照 */
    public static ProfileSnapshotVo of(UserInfo user) {
        ProfileSnapshotVo snapshot = new ProfileSnapshotVo();
        snapshot.realname = user.getRealname();
        snapshot.collegeId = user.getCollegeId();
        snapshot.grade = user.getGrade();
        snapshot.classId = user.getClassId();
        snapshot.username = user.getUsername();
        snapshot.email = user.getEmail();
        snapshot.phone = user.getPhone();
        snapshot.avatar = user.getAvatar();
        snapshot.qq = user.getQq();
        snapshot.cfUsername = user.getCfUsername();
        snapshot.github = user.getGithub();
        snapshot.blog = user.getBlog();
        return snapshot;
    }

    /**
     * 本申请实际要变更的字段：{@code proposed} 与当前快照取值不同的字段。
     * <p>只有这些字段需要做原值一致性校验与写回，因此用户通过别的申请改了无关字段时
     * 不会把我们这条申请误判为失效。</p>
     */
    public Set<ProfileChangeField> changedFields(ProfileSnapshotVo proposed) {
        Set<ProfileChangeField> changed = new LinkedHashSet<>();
        for (ProfileChangeField field : ProfileChangeField.values()) {
            if (!Objects.equals(valueOf(field), proposed == null ? null : proposed.valueOf(field))) {
                changed.add(field);
            }
        }
        return changed;
    }

    /** 按字段取值 */
    public Object valueOf(ProfileChangeField field) {
        return switch (field) {
            case REALNAME -> realname;
            case COLLEGE_ID -> collegeId;
            case GRADE -> grade;
            case CLASS_ID -> classId;
            case USERNAME -> username;
            case EMAIL -> email;
            case PHONE -> phone;
            case AVATAR -> avatar;
            case QQ -> qq;
            case CF_USERNAME -> cfUsername;
            case GITHUB -> github;
            case BLOG -> blog;
            default -> throw new IllegalStateException("未处理的资料字段: " + field);
        };
    }

    /** 按字段写入（只在提交申请构造 proposed 时使用） */
    public void put(ProfileChangeField field, Object value) {
        switch (field) {
            case REALNAME: realname = (String) value; break;
            case COLLEGE_ID: collegeId = (Long) value; break;
            case GRADE: grade = (String) value; break;
            case CLASS_ID: classId = (Long) value; break;
            case USERNAME: username = (String) value; break;
            case EMAIL: email = (String) value; break;
            case PHONE: phone = (String) value; break;
            case AVATAR: avatar = (String) value; break;
            case QQ: qq = (String) value; break;
            case CF_USERNAME: cfUsername = (String) value; break;
            case GITHUB: github = (String) value; break;
            case BLOG: blog = (String) value; break;
            default: throw new IllegalStateException("未处理的资料字段: " + field);
        }
    }

    /** 用户当前行的该字段是否仍等于本快照（原值一致性校验） */
    public boolean matchesUserField(ProfileChangeField field, UserInfo user) {
        return Objects.equals(valueOf(field), ProfileSnapshotVo.of(user).valueOf(field));
    }

    /** 把本快照的字段值写回用户对象（调用方负责持久化） */
    public void applyTo(UserInfo user, ProfileChangeField field) {
        switch (field) {
            case REALNAME: user.setRealname(realname); break;
            case COLLEGE_ID: user.setCollegeId(collegeId); break;
            case GRADE: user.setGrade(grade); break;
            case CLASS_ID: user.setClassId(classId); break;
            case USERNAME: user.setUsername(username); break;
            case EMAIL: user.setEmail(email); break;
            case PHONE: user.setPhone(phone); break;
            case AVATAR: user.setAvatar(avatar); break;
            case QQ: user.setQq(qq); break;
            case CF_USERNAME: user.setCfUsername(cfUsername); break;
            case GITHUB: user.setGithub(github); break;
            case BLOG: user.setBlog(blog); break;
            default: throw new IllegalStateException("未处理的资料字段: " + field);
        }
    }
}
