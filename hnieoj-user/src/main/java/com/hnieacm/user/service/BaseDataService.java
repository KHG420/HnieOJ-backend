package com.hnieacm.user.service;

import com.hnieacm.user.vo.ClassTeacherVo;
import com.hnieacm.user.vo.ClassTaVo;
import com.hnieacm.user.vo.GradeVo;
import com.hnieacm.user.vo.IdNameVo;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/17
 * @Description: 基础数据服务
 */
public interface BaseDataService {

    /**
     * 获取学院列表
     */
    List<IdNameVo> listColleges();

    /**
     * 获取学院下年级列表
     */
    List<GradeVo> listGrades(Long collegeId);

    /**
     * 获取学院+年级下班级列表
     */
    List<IdNameVo> listClasses(Long collegeId, String grade);

    /**
     * 按班级 id 批量查询班级（id → name 反查）。
     * <p>替代前端「遍历学院×年级×班级」的反查：一次请求拿到全部已选班级的名称，
     * 不再随学院/年级数量线性放大请求数。</p>
     * <p>语义：库中不存在的 id <b>跳过不报错</b>，调用方对未命中的 id 保留原 id 展示、不阻断编辑；
     * 但 {@code null} 或 {@code <= 0} 的非法 id 视为调用方缺陷，抛 400。</p>
     *
     * @param ids 班级 id 列表，允许重复；去重后查询
     * @return 命中的班级（按 id 升序），未命中的 id 不出现在结果里
     */
    List<IdNameVo> listClassesByIds(List<Long> ids);

    /**
     * 获取班级老师列表（负责教师）
     */
    List<ClassTeacherVo> listTeachers(Long classId);

    /**
     * 获取班级助教列表
     */
    List<ClassTaVo> listTas(Long classId);
}
