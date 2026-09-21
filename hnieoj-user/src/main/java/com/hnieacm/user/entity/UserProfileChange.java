package com.hnieacm.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 用户身份资料变更申请实体（user_profile_change）
 */
@Data
@TableName("user_profile_change")
public class UserProfileChange {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uid;

    /**
     * 申请时的原始资料全量快照 JSON（{@code ProfileChangeField} 全部 12 项可变更字段）。
     */
    private String original;

    /**
     * 期望变更后的资料全量快照 JSON（全部 12 项可变更字段；未申请变更的字段保留原始值）。
     */
    private String proposed;

    private String reason;

    /**
     * PENDING / APPROVED / REJECTED
     */
    private String status;

    private String reviewerUid;

    private String reviewReason;

    private LocalDateTime reviewAt;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
