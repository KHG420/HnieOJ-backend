package com.hnieacm.contest.vo;

import lombok.Data;

@Data
public class ContestRatingVo {
    private int rank;
    private String uid;
    private String username;
    private int rating;
    private int contests;
}
