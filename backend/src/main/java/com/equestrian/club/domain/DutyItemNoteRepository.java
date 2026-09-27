package com.equestrian.club.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DutyItemNoteRepository extends JpaRepository<DutyItemNote, Long> {

    /** 事项补充历史：时间正序，只追加不改写 */
    List<DutyItemNote> findByItemIdOrderByIdAsc(Long itemId);
}
