package com.equestrian.club.dto;

import java.time.LocalDate;

/**
 * 建立值班班次请求。只有值班负责人（MANAGER）能开班，角色后端强制。
 */
public class DutyShiftCreateRequest extends OperatorRequest {

    /** 值班日期，不填取当天 */
    private LocalDate businessDate;

    /** DAY 白班 / NIGHT 夜班，不填默认白班 */
    private String period;

    /** 班次交接总说明（可选，开班时负责人先写一版） */
    private String handoverNote;

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(LocalDate businessDate) {
        this.businessDate = businessDate;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getHandoverNote() {
        return handoverNote;
    }

    public void setHandoverNote(String handoverNote) {
        this.handoverNote = handoverNote;
    }
}
