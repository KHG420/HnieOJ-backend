package com.hnieacm.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 资料变更申请展示对象（身份 + 联系/社交字段的完整快照）
 */
@Data
public class ProfileChangeVo {

    private Long id;

    private String uid;

    private ProfileSnapshotVo original;

    private ProfileSnapshotVo proposed;

    private String reason;

    private String status;

    private String reviewerUid;

    private String reviewReason;

    private LocalDateTime reviewAt;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
