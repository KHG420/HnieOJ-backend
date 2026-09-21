package com.hnieacm.user.constant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: {@link ProfileChangeField} 的 DDL 列宽与初始化脚本一致性断言。
 * <p>资料变更申请现在可以改 12 个字段，若某个字段的列宽在 DDL 里被改窄而枚举没跟上，
 * 超长值会在写库时才炸（且只在那一个字段上炸）。这里把两边钉在一起。</p>
 */
class ProfileChangeFieldConsistencyTest {

    /** ProfileChangeField 的 key → user_info 列名 */
    private static final Map<String, String> COLUMN_BY_KEY = Map.ofEntries(
            Map.entry("realname", "realname"),
            Map.entry("grade", "grade"),
            Map.entry("username", "username"),
            Map.entry("email", "email"),
            Map.entry("phone", "phone"),
            Map.entry("avatar", "avatar"),
            Map.entry("qq", "qq"),
            Map.entry("cfUsername", "cf_username"),
            Map.entry("github", "github"),
            Map.entry("blog", "blog"));

    @Test
    void ddlMaxLengthMatchesUserInfoColumns() {
        Map<String, Integer> columns = parseUserInfoVarcharLengths(readInitScript());

        for (ProfileChangeField field : ProfileChangeField.values()) {
            String column = COLUMN_BY_KEY.get(field.getKey());
            if (column == null) {
                // collegeId / classId 是 bigint，没有长度上限
                assertThat(field.getDdlMaxLength())
                        .as("字段 %s 是 id 类字段，不应声明长度上限", field.getKey())
                        .isNull();
                continue;
            }
            assertThat(field.getDdlMaxLength())
                    .as("字段 %s 必须声明列宽", field.getKey())
                    .isNotNull();
            assertThat(field.getDdlMaxLength())
                    .as("user_info.%s 的列宽必须等于 ProfileChangeField.%s 的 ddlMaxLength",
                            column, field.name())
                    .isEqualTo(columns.get(column));
        }
    }

    @Test
    void identityFieldsAreFlaggedForAuthCacheInvalidation() {
        assertThat(ProfileChangeField.REALNAME.isIdentity()).isTrue();
        assertThat(ProfileChangeField.COLLEGE_ID.isIdentity()).isTrue();
        assertThat(ProfileChangeField.GRADE.isIdentity()).isTrue();
        assertThat(ProfileChangeField.CLASS_ID.isIdentity()).isTrue();
        assertThat(ProfileChangeField.EMAIL.isIdentity()).isFalse();
        assertThat(ProfileChangeField.USERNAME.isIdentity()).isFalse();
    }

    private static String readInitScript() {
        Path mysqlDir = locateDeployMysqlDir();
        try (Stream<Path> files = Files.list(mysqlDir)) {
            List<Path> candidates = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .toList();
            for (Path candidate : candidates) {
                String sql = Files.readString(candidate, StandardCharsets.UTF_8);
                if (sql.contains("CREATE TABLE `user_info`")) {
                    return sql;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new AssertionError("在 " + mysqlDir + " 下未找到包含 CREATE TABLE `user_info` 的初始化脚本");
    }

    private static Path locateDeployMysqlDir() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("deploy").resolve("mysql");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new AssertionError("未找到 deploy/mysql 目录（测试工作目录：" + Path.of("").toAbsolutePath() + "）");
    }

    private static Map<String, Integer> parseUserInfoVarcharLengths(String sql) {
        Matcher table = Pattern
                .compile("(?is)CREATE TABLE\\s+`user_info`\\s*\\((.*?)\\)\\s*ENGINE")
                .matcher(sql);
        assertThat(table.find()).as("初始化脚本中应存在 CREATE TABLE `user_info`").isTrue();

        Map<String, Integer> lengths = new LinkedHashMap<>();
        Matcher column = Pattern
                .compile("(?i)`(\\w+)`\\s+varchar\\((\\d+)\\)")
                .matcher(table.group(1));
        while (column.find()) {
            lengths.put(column.group(1).toLowerCase(Locale.ROOT), Integer.parseInt(column.group(2)));
        }
        return lengths;
    }

    @Test
    void retiredTableIsNeitherCreatedNorLeftBehind() {
        String sql = readInitScript();
        // 合并两套流程后不得再建 user_profile_change_apply
        assertThat(sql).doesNotContain("CREATE TABLE `user_profile_change_apply`");
        // 还必须显式 DROP：本脚本对已存在的库是「重跑」，只删 CREATE 会让上一版建出的表变成孤儿
        assertThat(sql).contains("DROP TABLE IF EXISTS `user_profile_change_apply`");
    }
}
