package com.equestrian.club.dto;

/**
 * 负责人标记优先级 / 分派下一班负责教练的请求。
 * 两个字段都可空：只传 priority 表示仅标记优先级；只传 ownerCoachId 表示仅分派。
 * 分派后事项进入 DISPATCHED，仍需下一班负责人逐项确认承接。
 */
public class DutyItemDispatchRequest extends OperatorRequest {

    /** LOW / NORMAL / HIGH / URGENT，可空表示不改 */
    private String priority;

    /** 分派给的教练 id，可空表示不改；传 -1 表示清除分派回到待处理 */
    private Long ownerCoachId;

    /** 页面看到的事项版本，旧页面提交直接 409 */
    private Long expectedVersion;

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

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
