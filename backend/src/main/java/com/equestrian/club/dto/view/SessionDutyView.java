package com.equestrian.club.dto.view;

import java.util.List;

/**
 * 课程排期上的值班交接信息：这个场次属于哪个班次、事项当前状态、
 * 最近一次承接记录。嵌入 SessionView，原有课程 / 栏位页面结构不变。
 */
public record SessionDutyView(
        Long sessionId,
        /** 是否存在交接事项（没有时前端不显示交接区块） */
        boolean tracked,
        List<DutyItemView> items,
        DutyAcceptanceView latestAcceptance) {
}
