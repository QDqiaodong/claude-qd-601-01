package com.equestrian.club.dto;

/**
 * 事项补充请求：只追加一条 duty_item_note，不覆盖任何既有字段。
 * 普通教练（STAFF）只能补充分派给自己（coachId）的事项；负责人可补充本班全部事项。
 */
public class DutyItemSupplementRequest extends OperatorRequest {

    /** 补充说明，必填 */
    private String note;

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
