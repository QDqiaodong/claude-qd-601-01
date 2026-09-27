package com.equestrian.club.dto;

/**
 * 负责人更新班次交接总说明（handoverNote）。
 * 只有 MANAGER 能改；expectedVersion 为打开班次详情时看到的版本，过期提交直接 409。
 */
public class DutyShiftUpdateRequest extends OperatorRequest {

    private String handoverNote;

    /** 页面看到的班次版本，防止旧页面覆盖新内容 */
    private Long expectedVersion;

    public String getHandoverNote() {
        return handoverNote;
    }

    public void setHandoverNote(String handoverNote) {
        this.handoverNote = handoverNote;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
