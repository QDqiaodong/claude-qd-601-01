package com.equestrian.club.dto;

/**
 * 下一班负责人逐项确认承接的请求。
 *
 * <p>承接是事项终态：只有事项当前所在班次（下一班）的 MANAGER 能确认；
 * requestId 幂等 + uk_accept_item 唯一约束双保险，重复确认不会产生两条承接记录，
 * 也不会重复推进状态。expectedVersion 拦截旧页面提交。
 */
public class DutyItemAcceptRequest extends OperatorRequest {

    /** 承接备注，可空 */
    private String note;

    private Long expectedVersion;

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
