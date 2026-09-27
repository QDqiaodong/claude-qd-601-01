package com.equestrian.club.dto;

/**
 * 健康事件写操作的公共身份字段：谁（operatorName）以什么角色（operatorRole）操作。
 *
 * <p>角色不是只靠前端隐藏按钮：每个写接口都在后端再校验一次，
 * 普通工作人员（STAFF）即使直接调接口也无法完成复训放行（只有 MANAGER 可以）。
 */
public class OperatorRequest {

    /** 操作人姓名 */
    private String operatorName;

    /** STAFF 普通工作人员 / MANAGER 负责人 */
    private String operatorRole;

    /**
     * 前端为「一次点击」生成的幂等键。重复点击 / 网络重试携带同一个 requestId，
     * 后端只落一条记录、只联动一次马匹状态。
     */
    private String requestId;

    /**
     * 值班交接场景：当前操作人对应的教练 id。
     * 普通教练（STAFF）补充事项时后端用它校验「只能补充分派给自己的事项」；
     * 负责人（MANAGER）不需要，传空即可。
     */
    private Long coachId;

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getOperatorRole() {
        return operatorRole;
    }

    public void setOperatorRole(String operatorRole) {
        this.operatorRole = operatorRole;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getCoachId() {
        return coachId;
    }

    public void setCoachId(Long coachId) {
        this.coachId = coachId;
    }
}
