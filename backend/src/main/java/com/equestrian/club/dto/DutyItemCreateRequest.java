package com.equestrian.club.dto;

/**
 * 负责人手工补充交接事项（开班自动生成之外的临时事项，OTHER 类型居多）。
 */
public class DutyItemCreateRequest extends OperatorRequest {

    /** LESSON / HORSE / STALL / OTHER，不填默认 OTHER */
    private String itemType;

    /** 关联对象 id（课程场次 / 马匹 / 栏位），OTHER 可空 */
    private Long refId;

    /** 事项标题，必填 */
    private String title;

    /** 事项说明，可空 */
    private String detail;

    /** LOW / NORMAL / HIGH / URGENT，不填默认 NORMAL */
    private String priority;

    /** 分派给的教练 id，可空 */
    private Long ownerCoachId;

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public Long getRefId() {
        return refId;
    }

    public void setRefId(Long refId) {
        this.refId = refId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Long getOwnerCoachId() {
        return ownerCoachId;
    }

    public void setOwnerCoachId(Long ownerCoachId) {
        this.ownerCoachId = ownerCoachId;
    }
}
