package com.equestrian.club.dto.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交接事项视图：展开类型 / 状态 / 优先级中文名，带来源班次、当前班次、
 * 负责教练、承接记录与全部补充历史。
 */
public record DutyItemView(
        Long id,
        String itemNo,
        Long originatingShiftId,
        String originatingShiftNo,
        Long currentShiftId,
        String currentShiftNo,
        String itemType,
        String itemTypeName,
        Long refId,
        String refLabel,
        String title,
        String detail,
        String priority,
        String priorityName,
        String status,
        String statusName,
        boolean open,
        Long ownerCoachId,
        String ownerCoachNo,
        String ownerCoachName,
        String delayNote,
        Long acceptedShiftId,
        String acceptedShiftNo,
        String acceptedBy,
        LocalDateTime acceptedAt,
        Long version,
        List<DutyItemNoteView> notes,
        DutyAcceptanceView acceptance,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
