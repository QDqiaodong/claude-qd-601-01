package com.equestrian.club.domain;

import java.time.LocalDate;
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
 * 值班班次实体。
 *
 * <p>OPEN 进行中 -> CLOSED 已结束。允许两个 OPEN 并存：上一班还有未承接事项时，
 * 下一班已经建立，事项滚动到下一班继续承接，因此结束本班不等于事项消失。
 *
 * <p>{@link Version} 乐观锁：旧班次页面拿旧 version 提交结束班次 / 修改事项时，
 * 若期间清单已被下一班建立、事项补充、承接确认等操作改动，直接抛
 * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}，
 * 由统一异常处理转成 409，提示数据已变化并要求重新载入。
 */
@Entity
@Table(name = "duty_shift")
public class DutyShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 班次编号，唯一，形如 DS-000001 */
    @Column(name = "shift_no", nullable = false, unique = true, length = 32)
    private String shiftNo;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    /** DAY 白班 / NIGHT 夜班 */
    @Column(name = "period", nullable = false, length = 16)
    private String period;

    /** 当班值班负责人姓名（结束班次只能由本人/负责人角色操作） */
    @Column(name = "manager_name", nullable = false, length = 64)
    private String managerName;

    @Column(name = "handover_note", length = 1000)
    private String handoverNote;

    /** OPEN / CLOSED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 上一班次 id，承接链按它逐级回溯 */
    @Column(name = "prev_shift_id")
    private Long prevShiftId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by", length = 64)
    private String closedBy;

    /** 仍有未承接事项时，负责人完成交接必须逐条/汇总记录的延期原因 */
    @Column(name = "delay_reason", length = 500)
    private String delayReason;

    /** JPA 乐观锁：任何对班次行的更新都带版本比对 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /**
     * 交接清单版本（service 显式维护）：开班滚动、事项同步、补充、分派、承接、
     * 关班滚动等任何影响清单的动作都 +1。它与 {@link #version} 不同：
     * 事项滚动到下一班时本班行本身不变（@Version 不动），但清单已经不属于本班，
     * 必须靠 listSeq 让旧班次页面的提交失效并提示重新载入。
     */
    @Column(name = "list_seq", nullable = false)
    private Long listSeq = 0L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        if (this.version == null) {
            this.version = 0L;
        }
        if (this.listSeq == null) {
            this.listSeq = 0L;
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

    public String getShiftNo() {
        return shiftNo;
    }

    public void setShiftNo(String shiftNo) {
        this.shiftNo = shiftNo;
    }

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

    public String getManagerName() {
        return managerName;
    }

    public void setManagerName(String managerName) {
        this.managerName = managerName;
    }

    public String getHandoverNote() {
        return handoverNote;
    }

    public void setHandoverNote(String handoverNote) {
        this.handoverNote = handoverNote;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getPrevShiftId() {
        return prevShiftId;
    }

    public void setPrevShiftId(Long prevShiftId) {
        this.prevShiftId = prevShiftId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
    }

    public String getDelayReason() {
        return delayReason;
    }

    public void setDelayReason(String delayReason) {
        this.delayReason = delayReason;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Long getListSeq() {
        return listSeq;
    }

    public void setListSeq(Long listSeq) {
        this.listSeq = listSeq;
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
