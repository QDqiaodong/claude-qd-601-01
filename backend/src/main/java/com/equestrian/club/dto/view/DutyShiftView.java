package com.equestrian.club.dto.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 班次视图：状态/时段中文名 + 上一班编号 + 清单统计 + 逐项未结阻塞明细。
 * closingBlockers 只在「结束班次预检失败」时返回，前端逐项提示后再要求延期原因。
 */
public record DutyShiftView(
        Long id,
        String shiftNo,
        LocalDate businessDate,
        String period,
        String periodName,
        String managerName,
        String handoverNote,
        String status,
        String statusName,
        boolean open,
        Long prevShiftId,
        String prevShiftNo,
        LocalDate prevShiftDate,
        String delayReason,
        Long version,
        LocalDateTime startedAt,
        LocalDateTime closedAt,
        String closedBy,
        int totalItems,
        int openCount,
        int pendingCount,
        int dispatchedCount,
        int acceptedCount,
        List<DutyItemView> items,
        /** 上一班延期滚动到本班、仍未承接的事项数（首页重点提示） */
        int inheritedOpenCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** 结束班次预检：未结事项逐条提示，无未结事项时为空 */
        List<String> closingBlockers) {
}
