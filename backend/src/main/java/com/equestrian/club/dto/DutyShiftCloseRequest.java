package com.equestrian.club.dto;

/**
 * 结束班次请求。
 *
 * <p>仍有未承接事项时：第一次提交（confirmDefer=false）被逐项拦截，
 * 返回每一条未结事项要求处理；负责人逐条记录延期原因（itemDelays）或填写班次
 * 延期总说明（delayReason）后，以 confirmDefer=true 再次提交才允许完成交接。
 * expectedVersion 防止旧班次页面关班覆盖下一班已经建立的变化。
 */
public class DutyShiftCloseRequest extends OperatorRequest {

    /** 班次级延期原因：所有未结事项统一延期到下一班时填写 */
    private String delayReason;

    /**
     * 逐项延期说明：key=事项 id，value=该事项延期原因；
     * 给了逐项原因的事项写入 duty_item.delay_note，便于跨班次逐条追溯。
     */
    private java.util.Map<Long, String> itemDelays;

    /** false=预检（有未结事项就拒绝并逐条返回）；true=已确认延期，允许关班 */
    private boolean confirmDefer;

    private Long expectedVersion;

    public String getDelayReason() {
        return delayReason;
    }

    public void setDelayReason(String delayReason) {
        this.delayReason = delayReason;
    }

    public java.util.Map<Long, String> getItemDelays() {
        return itemDelays;
    }

    public void setItemDelays(java.util.Map<Long, String> itemDelays) {
        this.itemDelays = itemDelays;
    }

    public boolean isConfirmDefer() {
        return confirmDefer;
    }

    public void setConfirmDefer(boolean confirmDefer) {
        this.confirmDefer = confirmDefer;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
