package com.hnieacm.submission.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserDailySubmitVo {
    private LocalDate day;
    private Integer submissions;
    private Integer accepted;
}
