package com.equestrian.club.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface DutyShiftRepository extends JpaRepository<DutyShift, Long> {

    /** 班次编号取号用 */
    Optional<DutyShift> findFirstByOrderByIdDesc();

    /** 历史班次查询：按日期区间，新班次在前 */
    List<DutyShift> findByBusinessDateBetweenOrderByBusinessDateDescIdDesc(LocalDate from, LocalDate to);

    List<DutyShift> findByBusinessDateOrderByIdDesc(LocalDate businessDate);

    List<DutyShift> findByStatusOrderByIdDesc(String status);

    List<DutyShift> findByBusinessDateBetweenAndStatusOrderByBusinessDateDescIdDesc(
            LocalDate from, LocalDate to, String status);

    /** 负责人模糊 / 精确查询（历史按负责人筛选） */
    List<DutyShift> findByBusinessDateBetweenAndManagerNameContainingOrderByBusinessDateDescIdDesc(
            LocalDate from, LocalDate to, String managerName);

    List<DutyShift> findByBusinessDateBetweenAndStatusAndManagerNameContainingOrderByBusinessDateDescIdDesc(
            LocalDate from, LocalDate to, String status, String managerName);

    List<DutyShift> findByManagerNameContainingOrderByIdDesc(String managerName);

    List<DutyShift> findByStatusAndManagerNameContainingOrderByIdDesc(String status, String managerName);

    /** 开班时 prev_shift_id 指向本班的下一班（结束班次时把未结事项滚动给它） */
    java.util.List<DutyShift> findByPrevShiftId(Long prevShiftId);

    /** 同一天同一时段只允许一个进行中的班次 */
    boolean existsByBusinessDateAndPeriodAndStatus(LocalDate businessDate, String period, String status);

    /** 开班 / 关班并发的串行锁：锁全局最新班次行 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DutyShift s where s.id = :id")
    Optional<DutyShift> findByIdForUpdate(@Param("id") Long id);

    /**
     * 开班 / 关班并发的串行锁：对「全局最新班次行」加行写锁。
     * JPQL + Pageable(0,1) 生成 {@code order by id desc limit 1 for update}，
     * 所有值班写事务先过这把锁，串行化交接清单的滚动与状态联动。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DutyShift s order by s.id desc")
    List<DutyShift> findLatestForUpdate(Pageable pageable);
}
