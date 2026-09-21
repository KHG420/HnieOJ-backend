package com.hnieacm.problem.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 题目样例请求项。存量数据中样例键名为 in/out，通过 JsonAlias 兼容。
 */
@Data
public class ProblemExampleRequest {

    @NotBlank(message = "example.input不能为空")
    @JsonAlias("in")
    private String input;

    @NotBlank(message = "example.output不能为空")
    @JsonAlias("out")
    private String output;
}
