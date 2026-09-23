package com.hnieacm.contest.vo;

import lombok.Data;

@Data
public class MyContestTeamVo {
    private Long teamId;
    private String teamName;
    private Long contestId;
    private String contestTitle;
    private String captainUid;
}
