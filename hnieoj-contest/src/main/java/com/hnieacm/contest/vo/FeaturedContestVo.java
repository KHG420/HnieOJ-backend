package com.hnieacm.contest.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FeaturedContestVo {
    private Long id;
    private String title;
    private String type;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
}
