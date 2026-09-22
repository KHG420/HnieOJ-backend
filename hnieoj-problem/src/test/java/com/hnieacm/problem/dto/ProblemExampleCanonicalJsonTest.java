package com.hnieacm.problem.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.problem.vo.ProblemExampleVo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/22
 * @Description: 题目样例 JSON 契约回归：canonical 键名固定为 input/output，请求与响应双向序列化/反序列化一致。
 */
class ProblemExampleCanonicalJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestDeserializesCanonicalKeys() throws Exception {
        ProblemExampleRequest request = objectMapper.readValue(
                "{\"input\":\"1 2\",\"output\":\"3\"}", ProblemExampleRequest.class);

        assertThat(request.getInput()).isEqualTo("1 2");
        assertThat(request.getOutput()).isEqualTo("3");
    }

    @Test
    void requestSerializesCanonicalKeysOnly() throws Exception {
        ProblemExampleRequest request = new ProblemExampleRequest();
        request.setInput("1 2");
        request.setOutput("3");

        Map<String, Object> serialized = objectMapper.readValue(
                objectMapper.writeValueAsString(request), new TypeReference<>() {
                });

        assertThat(serialized).containsOnlyKeys("input", "output");
        assertThat(serialized).containsEntry("input", "1 2").containsEntry("output", "3");
    }

    @Test
    void storedExamplesJsonListRoundTripsThroughRequest() throws Exception {
        String stored = "[{\"input\":\"1 2\",\"output\":\"3\"},{\"input\":\"5\",\"output\":\"5\"}]";

        List<ProblemExampleRequest> examples = objectMapper.readValue(stored, new TypeReference<>() {
        });

        assertThat(examples).hasSize(2);
        assertThat(examples.get(0).getInput()).isEqualTo("1 2");
        assertThat(examples.get(1).getOutput()).isEqualTo("5");
    }

    @Test
    void voSerializesCanonicalKeysOnly() throws Exception {
        Map<String, Object> serialized = objectMapper.readValue(
                objectMapper.writeValueAsString(new ProblemExampleVo("hello", "olleh")), new TypeReference<>() {
                });

        assertThat(serialized).containsOnlyKeys("input", "output");
        assertThat(serialized).containsEntry("input", "hello").containsEntry("output", "olleh");
    }

    @Test
    void voDeserializesCanonicalKeys() throws Exception {
        List<ProblemExampleVo> examples = objectMapper.readValue(
                "[{\"input\":\"hello\",\"output\":\"olleh\"}]", new TypeReference<>() {
                });

        assertThat(examples).hasSize(1);
        assertThat(examples.get(0).getInput()).isEqualTo("hello");
        assertThat(examples.get(0).getOutput()).isEqualTo("olleh");
    }
}
