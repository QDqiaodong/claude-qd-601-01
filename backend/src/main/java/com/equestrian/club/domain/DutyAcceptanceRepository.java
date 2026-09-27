package com.equestrian.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DutyAcceptanceRepository extends JpaRepository<DutyAcceptance, Long> {

    /** 一项最多一条承接记录（uk_accept_item 数据库层兜底） */
    Optional<DutyAcceptance> findByItemId(Long itemId);

    /** 幂等：同一 requestId 的重复确认取回第一次那条 */
    Optional<DutyAcceptance> findByRequestId(String requestId);

    /** 某班次完成的全部承接（课程详情「最近一次承接记录」、班次详情用） */
    List<DutyAcceptance> findByShiftIdOrderByIdAsc(Long shiftId);

    boolean existsByItemId(Long itemId);
}
