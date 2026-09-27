package com.equestrian.club.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 值班交接事项实体。
 *
 * <p>事项状态单向推进：PENDING 待处理 -> DISPATCHED 已分派 -> ACCEPTED 已承接；
 * ACCEPTED 是终态，后端状态机不允许回退——即使旧班次页面拿着过期数据提交，
 * 已承接事项也不能被改回未处理。
 *
 * <p>{@code originatingShiftId} 是事项最初产生的班次（课程详情按它回答
 * 「属于哪个班次」）；{@code currentShiftId} 是事项当前所在班次，跨班承接时
 * 从旧班次滚动到新班次，「未确认事项继续显示在下一班首页」读的是它。
 *
 * <p>每项的承接结果由 {@link DutyAcceptance} 唯一约束（uk_accept_item）兜底，
 * 重复确认不会产生重复承接记录。
 */
@Entity
@Table(name = "duty_item")
public class DutyItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 事项编号，唯一，形如 DI-000001 */
    @Column(name = "item_no", nullable = false, unique = true, length = 32)
    private String itemNo;

    /** 事项最初产生的班次 */
    @Column(name = "originating_shift_id", nullable = false)
    private Long originatingShiftId;

    /** 事项当前所在班次：跨班滚动后会指向下一班 */
    @Column(name = "current_shift_id", nullable = false)
    private Long currentShiftId;

    /** LESSON 课程 / HORSE 马匹 / STALL 栏位 / OTHER 其他 */
    @Column(name = "item_type", nullable = false, length = 16)
    private String itemType;

    /** 关联对象 id（课程场次 / 马匹 / 栏位；OTHER 可空） */
    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "detail", length = 1000)
    private String detail;

    /** LOW / NORMAL / HIGH / URGENT */
    @Column(name = "priority", nullable = false, length = 16)
    private String priority;

    /** PENDING / DISPATCHED / ACCEPTED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 分派给的负责教练；未分派为 null */
    @Column(name = "owner_coach_id")
    private Long ownerCoachId;

    /** 结束班次时该事项仍未承接，负责人逐条记录的延期说明 */
    @Column(name = "delay_note", length = 500)
    private String delayNote;

    /** 实际承接该事项的班次 */
    @Column(name = "accepted_shift_id")
    private Long acceptedShiftId;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "accepted_by", length = 64)
    private String acceptedBy;

    /** 事项行乐观锁：分派 / 补充 / 承接并发时按版本拦截过期提交 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.version == null) {
            this.version = 0L;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public Long getOriginatingShiftId() {
        return originatingShiftId;
    }

    public void setOriginatingShiftId(Long originatingShiftId) {
        this.originatingShiftId = originatingShiftId;
    }

    public Long getCurrentShiftId() {
        return currentShiftId;
    }

    public void setCurrentShiftId(Long currentShiftId) {
        this.currentShiftId = currentShiftId;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOwnerCoachId() {
        return ownerCoachId;
    }

    public void setOwnerCoachId(Long ownerCoachId) {
        this.ownerCoachId = ownerCoachId;
    }

    public String getDelayNote() {
        return delayNote;
    }

    public void setDelayNote(String delayNote) {
        this.delayNote = delayNote;
    }

    public Long getAcceptedShiftId() {
        return acceptedShiftId;
    }

    public void setAcceptedShiftId(Long acceptedShiftId) {
        this.acceptedShiftId = acceptedShiftId;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public String getAcceptedBy() {
        return acceptedBy;
    }

    public void setAcceptedBy(String acceptedBy) {
        this.acceptedBy = acceptedBy;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
