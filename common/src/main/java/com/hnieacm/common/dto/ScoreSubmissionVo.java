package com.hnieacm.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** Minimal judged submission data used to calculate contest and homework standings. */
@Data
public class ScoreSubmissionVo {
    private Long problemId;
    private String uid;
    private String username;
    private Integer status;
    private Integer score;
    private LocalDateTime gmtCreate;
}
