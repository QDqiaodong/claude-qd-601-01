package com.equestrian.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface DutyItemRepository extends JpaRepository<DutyItem, Long> {

    List<DutyItem> findByCurrentShiftIdOrderByIdAsc(Long currentShiftId);

    /** 某班次清单（按产生班次查，历史详情用） */
    List<DutyItem> findByOriginatingShiftIdOrderByIdAsc(Long originatingShiftId);

    /** 某班次全部未结事项（结束班次前的拦截检查用） */
    List<DutyItem> findByCurrentShiftIdAndStatusNotOrderByIdAsc(Long currentShiftId, String status);

    /**
     * 结束班次拦截：本班产生（originating）但已滚动到下一班、且仍未承接的事项也要逐项提示，
     * 不能因为事项 current 指针前移就让旧班「无债一身轻」地关掉。
     */
    List<DutyItem> findByOriginatingShiftIdAndStatusNotOrderByIdAsc(Long originatingShiftId, String status);

    List<DutyItem> findByCurrentShiftIdAndStatusOrderByIdAsc(Long currentShiftId, String status);

    /** 某负责教练名下未结事项（普通教练首页 / 权限校验） */
    List<DutyItem> findByOwnerCoachIdAndStatusNotOrderByIdAsc(Long ownerCoachId, String status);

    /**
     * 同步清单去重：同类型 + 同来源对象在「任意班次」只要已有未取消的事项就不再重复生成。
     * 课程：受影响场次一场一条；马匹：一匹马同类型一条；栏位：一个栏位同类型一条。
     */
    List<DutyItem> findByItemTypeAndRefIdOrderByIdAsc(String itemType, Long refId);

    /**
     * 行级悲观锁：承接确认 / 分派等事项级写操作锁事项行，
     * 与班次全局锁配合保证「检查 + 状态流转 + 承接记录」原子完成。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from DutyItem i where i.id = :id")
    Optional<DutyItem> findByIdForUpdate(@Param("id") Long id);
}
