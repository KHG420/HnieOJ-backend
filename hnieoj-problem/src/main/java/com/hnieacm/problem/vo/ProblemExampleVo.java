package com.hnieacm.problem.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 题目示例（输入/输出）。存量数据中样例键名为 in/out，通过 JsonAlias 兼容。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemExampleVo {
    @JsonAlias("in")
    private String input;

    @JsonAlias("out")
    private String output;
}

