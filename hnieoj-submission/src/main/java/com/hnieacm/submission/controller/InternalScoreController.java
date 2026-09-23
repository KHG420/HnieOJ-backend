package com.hnieacm.submission.controller;

import com.hnieacm.common.dto.ScoreSubmissionVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.submission.mapper.JudgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/submissions/scores")
@RequiredArgsConstructor
public class InternalScoreController {
    private final JudgeMapper judgeMapper;

    @GetMapping
    public Result<List<ScoreSubmissionVo>> list(@RequestParam String scope, @RequestParam Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不合法");
        }
        if ("contest".equals(scope)) {
            return Result.success(judgeMapper.listContestScores(id));
        }
        if ("homework".equals(scope)) {
            return Result.success(judgeMapper.listHomeworkScores(id));
        }
        throw new BizException(ResultCode.BAD_REQUEST, "scope 不合法");
    }
}
