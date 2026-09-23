package com.hnieacm.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.common.dto.ScoreSubmissionVo;
import com.hnieacm.submission.vo.UserSolveRankVo;
import com.hnieacm.submission.vo.UserProblemSummaryVo;
import com.hnieacm.submission.vo.UserDailySubmitVo;
import com.hnieacm.submission.vo.AdminSubmissionDashboardVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: Judge mapper
 */
@Mapper
public interface JudgeMapper extends BaseMapper<Judge> {
    /**
     * List judged submissions for a contest.
     * @param id query id
     * @return query results
     */
    @Select("SELECT problem_id AS problemId, uid, username, status, score, gmt_create AS gmtCreate "
            + "FROM judge WHERE cid = #{id} AND cid > 0 ORDER BY gmt_create, id")
    List<ScoreSubmissionVo> listContestScores(@Param("id") Long id);

    /**
     * List judged submissions for a homework.
     * @param id query id
     * @return query results
     */
    @Select("SELECT problem_id AS problemId, uid, username, status, score, gmt_create AS gmtCreate "
            + "FROM judge WHERE hid = #{id} AND hid > 0 ORDER BY gmt_create, id")
    List<ScoreSubmissionVo> listHomeworkScores(@Param("id") Long id);

    /**
     * List all-time problem solving ranks.
     * @param monthStart query monthStart
     * @return query results
     */
    @Select("SELECT uid, MAX(username) AS username, "
            + "COUNT(DISTINCT CASE WHEN status = 0 THEN problem_id END) AS solved, "
            + "COUNT(DISTINCT CASE WHEN status = 0 AND gmt_create >= #{monthStart} THEN problem_id END) AS monthlySolved, "
            + "COUNT(*) AS submissions FROM judge WHERE cid = 0 AND hid = 0 AND tid = 0 "
            + "GROUP BY uid ORDER BY solved DESC, submissions ASC, uid ASC LIMIT 100")
    List<UserSolveRankVo> listSolveRanks(@Param("monthStart") java.time.LocalDateTime monthStart);

    /**
     * List monthly first-time problem solving ranks.
     * @param monthStart query monthStart
     * @return query results
     */
    @Select("SELECT j.uid, MAX(j.username) AS username, "
            + "COUNT(DISTINCT CASE WHEN j.status = 0 AND NOT EXISTS ("
            + "SELECT 1 FROM judge old WHERE old.uid = j.uid AND old.problem_id = j.problem_id "
            + "AND old.status = 0 AND old.cid = 0 AND old.hid = 0 AND old.tid = 0 "
            + "AND old.gmt_create < #{monthStart}) THEN j.problem_id END) AS solved, "
            + "COUNT(DISTINCT CASE WHEN j.status = 0 AND NOT EXISTS ("
            + "SELECT 1 FROM judge old WHERE old.uid = j.uid AND old.problem_id = j.problem_id "
            + "AND old.status = 0 AND old.cid = 0 AND old.hid = 0 AND old.tid = 0 "
            + "AND old.gmt_create < #{monthStart}) THEN j.problem_id END) AS monthlySolved, "
            + "COUNT(*) AS submissions FROM judge j WHERE j.cid = 0 AND j.hid = 0 AND j.tid = 0 "
            + "AND j.gmt_create >= #{monthStart} GROUP BY j.uid "
            + "ORDER BY monthlySolved DESC, submissions ASC, uid ASC LIMIT 100")
    List<UserSolveRankVo> listMonthlySolveRanks(@Param("monthStart") java.time.LocalDateTime monthStart);

    /**
     * List a user's normal problem results.
     * @param uid query uid
     * @return query results
     */
    @Select("SELECT problem_code AS problemCode, MAX(status = 0) AS accepted, COUNT(*) AS attempts "
            + "FROM judge WHERE uid = #{uid} AND cid = 0 AND hid = 0 AND tid = 0 "
            + "GROUP BY problem_id, problem_code ORDER BY problem_code LIMIT 1000")
    List<UserProblemSummaryVo> listUserProblems(@Param("uid") String uid);

    /**
     * List a user's daily submission totals.
     * @param uid query uid
     * @param since query since
     * @return query results
     */
    @Select("SELECT DATE(gmt_create) AS day, COUNT(*) AS submissions, "
            + "SUM(status = 0) AS accepted FROM judge WHERE uid = #{uid} "
            + "AND gmt_create >= #{since} GROUP BY DATE(gmt_create) ORDER BY day")
    List<UserDailySubmitVo> listUserDaily(@Param("uid") String uid,
                                          @Param("since") java.time.LocalDateTime since);

    /**
     * Count all submissions from a user.
     * @param uid query uid
     * @return query results
     */
    @Select("SELECT COUNT(*) FROM judge WHERE uid = #{uid}")
    int countUserSubmissions(@Param("uid") String uid);

    /**
     * List recent daily submission totals.
     * @param since query since
     * @return query results
     */
    @Select("SELECT DATE(gmt_create) AS day, COUNT(*) AS submissions FROM judge "
            + "WHERE gmt_create >= #{since} GROUP BY DATE(gmt_create) ORDER BY day")
    List<AdminSubmissionDashboardVo.Daily> listDashboardDaily(@Param("since") java.time.LocalDateTime since);

    /**
     * List submission totals by status.
     * @return query results
     */
    @Select("SELECT status, COUNT(*) AS submissions FROM judge GROUP BY status ORDER BY submissions DESC")
    List<AdminSubmissionDashboardVo.Status> listDashboardStatuses();

    /**
     * List the most submitted problems.
     * @return query results
     */
    @Select("SELECT problem_code AS problemCode, COUNT(*) AS submissions FROM judge "
            + "GROUP BY problem_id, problem_code ORDER BY submissions DESC, problem_code LIMIT 5")
    List<AdminSubmissionDashboardVo.Problem> listHotProblems();

    /**
     * List the least submitted problems with submissions.
     * @return query results
     */
    @Select("SELECT problem_code AS problemCode, COUNT(*) AS submissions FROM judge "
            + "GROUP BY problem_id, problem_code ORDER BY submissions ASC, problem_code LIMIT 5")
    List<AdminSubmissionDashboardVo.Problem> listLowActivityProblems();
}
