package com.hnieacm.training.feign;

import com.hnieacm.common.dto.ScoreSubmissionVo;
import com.hnieacm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "hnieoj-submission", contextId = "scoreSubmissionFeignClient")
public interface ScoreSubmissionFeignClient {
    @GetMapping("/internal/submissions/scores")
    Result<List<ScoreSubmissionVo>> listScores(@RequestParam("scope") String scope,
                                               @RequestParam("id") Long id);
}
