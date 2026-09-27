package com.equestrian.club.dto.view;

import java.util.List;

/**
 * 值班首页（交接看板）：
 * current = 最新一个班次（无论开闭）；未确认事项不会因班次结束而消失，
 * 它们始终挂在 current 班次的清单里；没有任何班次时 current 为 null。
 * 另给出「分派给当前教练但未承接」的个人待办，方便普通教练进页面就看到自己的事。
 */
public record DutyBoardView(
        DutyShiftView current,
        /** 是否已有下一班（用于提示「新变更将进入哪一班」） */
        boolean hasOpenShift,
        List<DutyItemView> myOpenItems,
        Long myCoachId) {
}
