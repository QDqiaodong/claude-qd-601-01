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
 * 交接事项补充记录：只追加，不提供 update / delete。
 *
 * <p>普通教练只能给自己负责（{@code coach_id = 自己}）的事项补充；
 * 值班负责人可补充本班全部事项。归属校验在 service 后端强制。
 */
@Entity
@Table(name = "duty_item_note")
public class DutyItemNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "note", nullable = false, length = 1000)
    private String note;

    @Column(name = "operator", nullable = false, length = 64)
    private String operator;

    /** STAFF 普通教练 / MANAGER 值班负责人 */
    @Column(name = "role", nullable = false, length = 16)
    private String role;

    /** 普通教练补充时其对应的教练 id（用于归属校验与展示） */
    @Column(name = "coach_id")
    private Long coachId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
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

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getCoachId() {
        return coachId;
    }

    public void setCoachId(Long coachId) {
        this.coachId = coachId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
