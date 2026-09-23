package com.hnieacm.submission.vo;

import lombok.Data;

import java.util.List;

@Data
public class UserSubmissionSummaryVo {
    private int totalSubmissions;
    private int acceptedProblems;
    private List<UserProblemSummaryVo> problems;
    private List<UserDailySubmitVo> daily;
}
