package com.hnieacm.user.vo;

import com.hnieacm.user.constant.ProfileChangeField;
import com.hnieacm.user.entity.UserInfo;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 资料快照的字段级语义。
 * <p>重点是 JSON 兼容性：只含部分字段的 original/proposed 反序列化后，其余字段为 null，
 * 两侧同为 null 必须视为「未申请变更」，否则既有待审记录会凭空多出一堆待改字段。</p>
 */
class ProfileSnapshotVoTest {

    @Test
    void legacyIdentityOnlyJsonYieldsOnlyIdentityChanges() {
        // 只写入身份字段的快照：联系/社交字段两边都是 null
        ProfileSnapshotVo original = new ProfileSnapshotVo();
        original.setRealname("Old");
        original.setCollegeId(1L);
        original.setGrade("2024");
        original.setClassId(10L);

        ProfileSnapshotVo proposed = new ProfileSnapshotVo();
        proposed.setRealname("New");
        proposed.setCollegeId(1L);
        proposed.setGrade("2024");
        proposed.setClassId(10L);

        Set<ProfileChangeField> changed = original.changedFields(proposed);

        assertThat(changed).containsExactly(ProfileChangeField.REALNAME);
    }

    @Test
    void identicalSnapshotsHaveNoChangedFields() {
        UserInfo user = user();
        ProfileSnapshotVo snapshot = ProfileSnapshotVo.of(user);

        assertThat(snapshot.changedFields(ProfileSnapshotVo.of(user))).isEmpty();
    }

    @Test
    void contactFieldChangeIsDetectedWithoutTouchingIdentity() {
        ProfileSnapshotVo original = ProfileSnapshotVo.of(user());
        ProfileSnapshotVo proposed = ProfileSnapshotVo.of(user());
        proposed.setEmail("new@example.com");

        assertThat(original.changedFields(proposed)).containsExactly(ProfileChangeField.EMAIL);
    }

    @Test
    void clearingAnOptionalFieldCountsAsAChange() {
        ProfileSnapshotVo original = ProfileSnapshotVo.of(user());
        ProfileSnapshotVo proposed = ProfileSnapshotVo.of(user());
        proposed.setBlog(null);

        assertThat(original.changedFields(proposed)).containsExactly(ProfileChangeField.BLOG);
    }

    @Test
    void matchesUserFieldIsPerField() {
        UserInfo user = user();
        ProfileSnapshotVo original = ProfileSnapshotVo.of(user);

        assertThat(original.matchesUserField(ProfileChangeField.EMAIL, user)).isTrue();

        // 只有 realname 被别处改了，邮箱相关申请不应被判失效
        user.setRealname("SomeoneElse");
        assertThat(original.matchesUserField(ProfileChangeField.EMAIL, user)).isTrue();
        assertThat(original.matchesUserField(ProfileChangeField.REALNAME, user)).isFalse();
    }

    @Test
    void applyToWritesExactlyOneField() {
        UserInfo user = user();
        ProfileSnapshotVo proposed = ProfileSnapshotVo.of(user);
        proposed.setCfUsername("new_cf");

        proposed.applyTo(user, ProfileChangeField.CF_USERNAME);

        assertThat(user.getCfUsername()).isEqualTo("new_cf");
        assertThat(user.getEmail()).isEqualTo("old@example.com");
        assertThat(user.getRealname()).isEqualTo("Old");
    }

    @Test
    void everyFieldIsAccessibleAndWritableThroughTheEnum() {
        // 快照字段与 ProfileChangeField 两份清单不许漂移
        UserInfo user = user();
        ProfileSnapshotVo snapshot = ProfileSnapshotVo.of(user);
        for (ProfileChangeField field : ProfileChangeField.values()) {
            Object value = snapshot.valueOf(field);
            assertThat(value).as("字段 %s 必须能取到值", field.getKey()).isNotNull();
        }
    }

    private static UserInfo user() {
        UserInfo user = new UserInfo();
        user.setUid("u1");
        user.setUsername("alice");
        user.setRealname("Old");
        user.setCollegeId(1L);
        user.setGrade("2024");
        user.setClassId(10L);
        user.setEmail("old@example.com");
        user.setPhone("13800000000");
        user.setAvatar("/avatar/a.png");
        user.setQq("10001");
        user.setCfUsername("old_cf");
        user.setGithub("https://github.com/old");
        user.setBlog("https://old.example.com");
        return user;
    }
}
