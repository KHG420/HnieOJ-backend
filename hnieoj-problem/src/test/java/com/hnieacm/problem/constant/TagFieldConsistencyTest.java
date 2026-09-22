package com.hnieacm.problem.constant;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.problem.dto.TagSaveRequest;
import com.hnieacm.problem.mapper.ProblemTagMapper;
import com.hnieacm.problem.mapper.TagMapper;
import com.hnieacm.problem.service.impl.TagServiceImpl;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 标签长度上限一致性断言：把「DTO {@code @Size} 上限」「{@link TagFieldConstant} 常量」
 * 「DDL {@code varchar} 列长度」三处钉在一起，任何一处单独改动都会让本测试失败。
 * <p>DDL 无法与 Java 共享常量，因此这里直接读取初始化脚本中的 {@code CREATE TABLE `tag`} 解析列长度。
 * 另外用 Service 的真实校验路径断言「DTO 上限 == Service 实际拒绝阈值」，避免只测注解不测实现。</p>
 */
class TagFieldConsistencyTest {

    /** 字段名 → DTO 上 @Size 的 max 取值来源 */
    private static final Map<String, Integer> EXPECTED = new LinkedHashMap<>();

    static {
        EXPECTED.put("name", TagFieldConstant.NAME_LENGTH);
        EXPECTED.put("color", TagFieldConstant.COLOR_LENGTH);
        EXPECTED.put("category", TagFieldConstant.CATEGORY_LENGTH);
    }

    @Test
    void dtoSizeAnnotationUsesSharedConstants() throws Exception {
        for (Map.Entry<String, Integer> entry : EXPECTED.entrySet()) {
            String field = entry.getKey();
            int expected = entry.getValue();

            Field declared = TagSaveRequest.class.getDeclaredField(field);
            Size size = declared.getAnnotation(Size.class);

            assertThat(size)
                    .as("TagSaveRequest.%s 必须有 @Size 上限", field)
                    .isNotNull();
            assertThat(size.max())
                    .as("TagSaveRequest.%s 的 @Size(max) 必须引用 TagFieldConstant", field)
                    .isEqualTo(expected);
            assertThat(size.message())
                    .as("TagSaveRequest.%s 的超限文案必须由常量拼出（文案属对外契约，不可改）", field)
                    .isEqualTo(TagFieldConstant.lengthMessage(field, expected));
        }
    }

    @Test
    void ddlTagTableColumnLengthsMatchConstants() {
        Map<String, Integer> columns = parseTagTableColumnLengths(readInitScript());

        assertThat(columns.get("name"))
                .as("DDL tag.name 的 varchar 长度必须等于 TagFieldConstant.NAME_LENGTH")
                .isEqualTo(TagFieldConstant.NAME_LENGTH);
        assertThat(columns.get("color"))
                .as("DDL tag.color 的 varchar 长度必须等于 TagFieldConstant.COLOR_LENGTH")
                .isEqualTo(TagFieldConstant.COLOR_LENGTH);
        assertThat(columns.get("category"))
                .as("DDL tag.category 的 varchar 长度必须等于 TagFieldConstant.CATEGORY_LENGTH")
                .isEqualTo(TagFieldConstant.CATEGORY_LENGTH);
    }

    @Test
    void serviceAcceptsExactlyAtLimitAndRejectsOneOver() {
        TagServiceImpl service = newService();

        // 恰好等于上限：DTO 与 Service 都应放行（重名查询返回 0，流程走到 insert）
        TagSaveRequest atLimit = new TagSaveRequest();
        atLimit.setName("a".repeat(TagFieldConstant.NAME_LENGTH));
        atLimit.setColor("b".repeat(TagFieldConstant.COLOR_LENGTH));
        atLimit.setCategory("c".repeat(TagFieldConstant.CATEGORY_LENGTH));
        assertThatCode(() -> service.createTag(atLimit)).doesNotThrowAnyException();

        // 上限 + 1：Service 必须拒绝，且文案与 DTO 注解文案逐字一致
        assertThatThrownBy(() -> service.createTag(nameOfLength(TagFieldConstant.NAME_LENGTH + 1)))
                .isInstanceOf(BizException.class)
                .hasMessage(TagFieldConstant.NAME_LENGTH_MESSAGE);

        TagSaveRequest colorTooLong = new TagSaveRequest();
        colorTooLong.setName("ok");
        colorTooLong.setColor("b".repeat(TagFieldConstant.COLOR_LENGTH + 1));
        assertThatThrownBy(() -> service.createTag(colorTooLong))
                .isInstanceOf(BizException.class)
                .hasMessage(TagFieldConstant.COLOR_LENGTH_MESSAGE);

        TagSaveRequest categoryTooLong = new TagSaveRequest();
        categoryTooLong.setName("ok");
        categoryTooLong.setCategory("c".repeat(TagFieldConstant.CATEGORY_LENGTH + 1));
        assertThatThrownBy(() -> service.createTag(categoryTooLong))
                .isInstanceOf(BizException.class)
                .hasMessage(TagFieldConstant.CATEGORY_LENGTH_MESSAGE);
    }

    private static TagSaveRequest nameOfLength(int length) {
        TagSaveRequest request = new TagSaveRequest();
        request.setName("a".repeat(length));
        return request;
    }

    /** 构造走真实校验路径的 Service；重名查询返回 0 让流程继续到 insert */
    private static TagServiceImpl newService() {
        TagMapper tagMapper = mock(TagMapper.class);
        when(tagMapper.selectCount(any())).thenReturn(0L);
        return new TagServiceImpl(tagMapper, mock(ProblemTagMapper.class));
    }

    /** 读取仓库里的主初始化脚本；DDL 无法与 Java 共享常量，只能以源码为事实来源解析 */
    private static String readInitScript() {
        Path mysqlDir = locateDeployMysqlDir();
        try (Stream<Path> files = Files.list(mysqlDir)) {
            List<Path> candidates = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .toList();
            for (Path candidate : candidates) {
                String sql = Files.readString(candidate, StandardCharsets.UTF_8);
                if (sql.contains("CREATE TABLE `tag`")) {
                    return sql;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new AssertionError("在 " + mysqlDir + " 下未找到包含 CREATE TABLE `tag` 的初始化脚本");
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

    private static Map<String, Integer> parseTagTableColumnLengths(String sql) {
        Matcher table = Pattern
                .compile("(?is)CREATE TABLE\\s+`tag`\\s*\\((.*?)\\)\\s*ENGINE")
                .matcher(sql);
        assertThat(table.find()).as("初始化脚本中应存在 CREATE TABLE `tag`").isTrue();

        Map<String, Integer> lengths = new LinkedHashMap<>();
        Matcher column = Pattern
                .compile("(?i)`(\\w+)`\\s+varchar\\((\\d+)\\)")
                .matcher(table.group(1));
        while (column.find()) {
            lengths.put(column.group(1).toLowerCase(Locale.ROOT), Integer.parseInt(column.group(2)));
        }
        return lengths;
    }
}
