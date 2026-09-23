package com.hnieacm.discussion.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.common.result.Result;
import com.hnieacm.discussion.mapper.DiscussionMapper;
import com.hnieacm.discussion.vo.ContributionRankVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author HnieOJ contributors
 */
@RestController
@SaCheckLogin
@RequestMapping("/api/discussions/contributions")
@RequiredArgsConstructor
public class ContributionController {
    private final DiscussionMapper discussionMapper;

    @GetMapping
    public Result<List<ContributionRankVo>> list() {
        List<ContributionRankVo> rows = discussionMapper.listContributions();
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setRank(i + 1);
        }
        return Result.success(rows);
    }

    @GetMapping("/{uid}")
    public Result<ContributionRankVo> detail(@PathVariable String uid) {
        return Result.success(discussionMapper.getContribution(uid));
    }
}
