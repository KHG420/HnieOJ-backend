package com.hnieacm.problem.service;

import com.hnieacm.problem.dto.TagCreateRequest;
import com.hnieacm.problem.dto.TagUpdateRequest;
import com.hnieacm.problem.vo.TagVo;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 标签目录与标签管理服务
 */
public interface TagService {

    /**
     * 标签目录（登录可读），按标签 id 升序返回全部标签。
     *
     * @return 全部标签，按标签 id 升序排列
     */
    List<TagVo> listTags();

    /**
     * 创建标签；名称去空格后校验长度与重名。
     *
     * @param request 标签创建参数，name 必填且去空格后不能为空，color/category 可选
     */
    void createTag(TagCreateRequest request);

    /**
     * 更新标签；不存在返回 NOT_FOUND，名称冲突返回业务错误。
     *
     * @param id      标签 id
     * @param request 标签更新参数，name 必填，color/category 可为空以清空原值
     */
    void updateTag(Long id, TagUpdateRequest request);

    /**
     * 删除标签；被题目引用时抛出 400 业务异常，不删除关联关系。
     *
     * @param id 标签 id
     */
    void deleteTag(Long id);
}
