package com.equestrian.club.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 交接事项承接确认记录：一个事项最多一条（uk_accept_item）。
 *
 * <p>下一班负责人逐项确认后落一条；{@code requestId} 是前端为一次点击生成的幂等键，
 * 重复确认（双击 / 网络重试）拿到同一条记录，绝不产生重复承接，
 * 事项状态也不会被重复联动。
 */
@Entity
@Table(name = "duty_acceptance")
public class DutyAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 被承接的事项；一项最多一条承接记录 */
    @Column(name = "item_id", nullable = false, unique = true)
    private Long itemId;

    /** 执行承接确认的班次（下一班） */
    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    @Column(name = "acceptor_name", nullable = false, length = 64)
    private String acceptorName;

    @Column(name = "note", length = 500)
    private String note;

    /** 前端幂等键，库里同值只允许存在一条 */
    @Column(name = "request_id", unique = true, length = 64)
    private String requestId;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    @PrePersist
    void onCreate() {
        this.acceptedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getShiftId() {
        return shiftId;
    }

    public void setShiftId(Long shiftId) {
        this.shiftId = shiftId;
    }

    public String getAcceptorName() {
        return acceptorName;
    }

    public void setAcceptorName(String acceptorName) {
        this.acceptorName = acceptorName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
}
