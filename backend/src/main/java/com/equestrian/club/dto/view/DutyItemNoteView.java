package com.equestrian.club.dto.view;

import java.time.LocalDateTime;

/** 事项补充记录视图（只追加历史） */
public record DutyItemNoteView(
        Long id,
        Long itemId,
        String note,
        String operator,
        String role,
        String roleName,
        Long coachId,
        String coachName,
        LocalDateTime createdAt) {
}
