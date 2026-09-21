package com.hnieacm.problem.constant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 标签字段长度上限与超限文案的**唯一 Java 来源**。
 * <p>DTO（{@code TagSaveRequest}）的 {@code @Size} 与 Service 的运行时校验都引用本类常量，
 * 不再各自声明字面量。DDL 的 {@code varchar} 无法与 Java 共享常量，由
 * {@code TagFieldConsistencyTest} 断言「DTO 注解上限 == 本类常量 == DDL 列长度」把三者钉在一起。</p>
 */
public final class TagFieldConstant {

    /**
     * 超限文案里「字段名」与上限数字之间的固定片段。
     * <p>注解的 {@code message} 必须是编译期常量，无法调用方法，因此这里抽成常量，
     * 由下面的 {@code *_LENGTH_MESSAGE}（编译期拼接）与 {@link #lengthMessage(String, int)}
     * （运行期拼接，Service 用）共同引用，保证两条路径产出的文案逐字一致。</p>
     */
    private static final String LENGTH_LIMIT_INFIX = " 长度不能超过 ";

    /** tag.name 上限，对应 DDL {@code varchar(50)} */
    public static final int NAME_LENGTH = 50;

    /** tag.color 上限，对应 DDL {@code varchar(20)} */
    public static final int COLOR_LENGTH = 20;

    /** tag.category 上限，对应 DDL {@code varchar(50)} */
    public static final int CATEGORY_LENGTH = 50;

    /** name 超限文案（编译期常量，直接用于 {@code @Size(message = ...)}） */
    public static final String NAME_LENGTH_MESSAGE = "name" + LENGTH_LIMIT_INFIX + NAME_LENGTH;

    /** color 超限文案（编译期常量） */
    public static final String COLOR_LENGTH_MESSAGE = "color" + LENGTH_LIMIT_INFIX + COLOR_LENGTH;

    /** category 超限文案（编译期常量） */
    public static final String CATEGORY_LENGTH_MESSAGE = "category" + LENGTH_LIMIT_INFIX + CATEGORY_LENGTH;

    private TagFieldConstant() {
    }

    /**
     * 运行时拼接长度超限文案，与 {@code *_LENGTH_MESSAGE} 同源。
     *
     * @param field     字段名，如 {@code name}
     * @param maxLength 该字段的长度上限
     * @return 形如 {@code "name 长度不能超过 50"} 的文案
     */
    public static String lengthMessage(String field, int maxLength) {
        return field + LENGTH_LIMIT_INFIX + maxLength;
    }
}
