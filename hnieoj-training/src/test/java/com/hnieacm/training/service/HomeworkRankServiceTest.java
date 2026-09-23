package com.hnieacm.training.service;

import com.hnieacm.common.dto.ScoreSubmissionVo;
import com.hnieacm.training.entity.Homework;
import com.hnieacm.training.vo.HomeworkRankVo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeworkRankServiceTest {
    @Test
    void usesBestScoreOnlyDuringHomeworkWindow() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 23, 10, 0);
        Homework homework = new Homework();
        homework.setStartTime(start);
        homework.setEndTime(start.plusHours(2));
        List<HomeworkRankVo> rows = HomeworkRankService.calculate(homework, Set.of(1L), List.of(
                score("alice", 50, start.plusMinutes(5)),
                score("alice", 80, start.plusMinutes(10)),
                score("alice", 100, start.plusHours(3)),
                score("bob", 60, start.plusMinutes(20))));
        assertEquals("alice", rows.get(0).getUid());
        assertEquals(80, rows.get(0).getTotalScore());
        assertEquals(0, rows.get(0).getSolved());
    }

    private static ScoreSubmissionVo score(String uid, int value, LocalDateTime time) {
        ScoreSubmissionVo row = new ScoreSubmissionVo();
        row.setUid(uid);
        row.setUsername(uid);
        row.setProblemId(1L);
        row.setStatus(3);
        row.setScore(value);
        row.setGmtCreate(time);
        return row;
    }
}
