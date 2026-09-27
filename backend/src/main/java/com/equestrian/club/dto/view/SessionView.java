package com.equestrian.club.dto.view;

import java.time.LocalDate;

/**
 * 课程排期视图：展开课程 / 教练 / 马匹的名称，并给出剩余名额。
 * duty 为该场次的值班交接信息（属于哪个班次、最近一次承接记录），
 * 没有交接事项时 tracked=false，课程页面原有结构不受影响。
 */
public record SessionView(
        Long id,
        Long lessonId,
        String lessonNo,
        String lessonName,
        String category,
        String categoryName,
        Long coachId,
        String coachName,
        Long horseId,
        String horseNo,
        String horseName,
        LocalDate sessionDate,
        String startTime,
        String endTime,
        Integer capacity,
        Integer bookedCount,
        Integer remain,
        String status,
        String statusName,
        boolean healthAffected,
        SessionDutyView duty) {

    /** 兼容只需要排期本体、不挂值班信息的构造场景（单场查询会再补挂） */
    public SessionView withDuty(SessionDutyView dutyView) {
        return new SessionView(id, lessonId, lessonNo, lessonName, category, categoryName,
                coachId, coachName, horseId, horseNo, horseName, sessionDate, startTime, endTime,
                capacity, bookedCount, remain, status, statusName, healthAffected, dutyView);
    }
}
