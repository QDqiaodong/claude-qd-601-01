package com.equestrian.club.dto.view;

import java.time.LocalDateTime;

/** 承接确认记录视图：课程详情「最近一次承接记录」直接使用 */
public record DutyAcceptanceView(
        Long id,
        Long itemId,
        String itemNo,
        String itemTitle,
        Long shiftId,
        String shiftNo,
        String acceptorName,
        String note,
        LocalDateTime acceptedAt) {
}
