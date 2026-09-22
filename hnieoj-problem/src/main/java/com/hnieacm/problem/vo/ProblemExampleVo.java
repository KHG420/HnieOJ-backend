package com.hnieacm.problem.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 题目示例（输入/输出），序列化字段名为 input/output。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemExampleVo {
    private String input;

    private String output;
}
