package com.equestrian.club.dto.view;

import java.time.LocalDate;

/** 课程排期视图：展开课程 / 教练 / 马匹的名称，并给出剩余名额 */
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
        boolean healthAffected) {
}
