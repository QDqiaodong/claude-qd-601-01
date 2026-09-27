package com.equestrian.club.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.equestrian.club.common.BizException;
import com.equestrian.club.common.ConflictException;
import com.equestrian.club.dict.EquestrianDict;
import com.equestrian.club.domain.Coach;
import com.equestrian.club.domain.CoachRepository;
import com.equestrian.club.domain.DutyAcceptance;
import com.equestrian.club.domain.DutyAcceptanceRepository;
import com.equestrian.club.domain.DutyItem;
import com.equestrian.club.domain.DutyItemNote;
import com.equestrian.club.domain.DutyItemNoteRepository;
import com.equestrian.club.domain.DutyItemRepository;
import com.equestrian.club.domain.DutyShift;
import com.equestrian.club.domain.DutyShiftRepository;
import com.equestrian.club.domain.HealthEvent;
import com.equestrian.club.domain.HealthEventRepository;
import com.equestrian.club.domain.Horse;
import com.equestrian.club.domain.HorseRepository;
import com.equestrian.club.domain.Lesson;
import com.equestrian.club.domain.LessonRepository;
import com.equestrian.club.domain.LessonSession;
import com.equestrian.club.domain.LessonSessionRepository;
import com.equestrian.club.domain.Stall;
import com.equestrian.club.domain.StallRepository;
import com.equestrian.club.dto.DutyItemAcceptRequest;
import com.equestrian.club.dto.DutyItemCreateRequest;
import com.equestrian.club.dto.DutyItemDispatchRequest;
import com.equestrian.club.dto.DutyItemSupplementRequest;
import com.equestrian.club.dto.DutyShiftCloseRequest;
import com.equestrian.club.dto.DutyShiftCreateRequest;
import com.equestrian.club.dto.DutyShiftUpdateRequest;
import com.equestrian.club.dto.OperatorRequest;
import com.equestrian.club.dto.view.DutyAcceptanceView;
import com.equestrian.club.dto.view.DutyBoardView;
import com.equestrian.club.dto.view.DutyItemNoteView;
import com.equestrian.club.dto.view.DutyItemView;
import com.equestrian.club.dto.view.DutyShiftView;
import com.equestrian.club.dto.view.SessionDutyView;

/**
 * 值班班次与交接事项服务。
 *
 * <p>承接链与状态机：
 * <ul>
 *   <li>开班（MANAGER）：锁定全局最新班次，把它的未结事项滚动到本班，
 *       再从当班课程（健康受影响场次）、马匹（休养中）、栏位（维护中）同步清单，
 *       新出现的课程变更只会进入「当前最新班次」；</li>
 *   <li>事项 PENDING -> DISPATCHED -> ACCEPTED 单向推进，ACCEPTED 是终态，
 *       旧班次拿旧页面提交会被乐观锁 / 状态机拦下，不能改回未处理；</li>
 *   <li>承接只能由事项当前所在班次的 MANAGER 逐项确认，requestId + 唯一约束幂等；</li>
 *   <li>结束班次只能由本班 MANAGER：仍有未结事项时第一次提交逐项拦截，
 *       记录延期原因（逐项或汇总）并确认后才允许关班；</li>
 *   <li>所有写操作先锁最新班次行串行化，失败随事务整体回滚，不留半条记录。</li>
 * </ul>
 */
@Service
public class DutyShiftService {

    private final DutyShiftRepository shiftRepository;
    private final DutyItemRepository itemRepository;
    private final DutyItemNoteRepository noteRepository;
    private final DutyAcceptanceRepository acceptanceRepository;
    private final LessonSessionRepository sessionRepository;
    private final LessonRepository lessonRepository;
    private final HorseRepository horseRepository;
    private final StallRepository stallRepository;
    private final CoachRepository coachRepository;
    private final HealthEventRepository healthEventRepository;

    public DutyShiftService(DutyShiftRepository shiftRepository,
            DutyItemRepository itemRepository,
            DutyItemNoteRepository noteRepository,
            DutyAcceptanceRepository acceptanceRepository,
            LessonSessionRepository sessionRepository,
            LessonRepository lessonRepository,
            HorseRepository horseRepository,
            StallRepository stallRepository,
            CoachRepository coachRepository,
            HealthEventRepository healthEventRepository) {
        this.shiftRepository = shiftRepository;
        this.itemRepository = itemRepository;
        this.noteRepository = noteRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.sessionRepository = sessionRepository;
        this.lessonRepository = lessonRepository;
        this.horseRepository = horseRepository;
        this.stallRepository = stallRepository;
        this.coachRepository = coachRepository;
        this.healthEventRepository = healthEventRepository;
    }

    // ============================== 查询 ==============================

    /**
     * 值班首页：最新一个班次 + 它的完整清单 + 当前教练的个人未结事项。
     * 未确认事项永远挂在「最新班次」上，班次结束不会让它们消失。
     */
    @Transactional(readOnly = true)
    public DutyBoardView board(Long coachId) {
        DutyShift latest = shiftRepository.findFirstByOrderByIdDesc().orElse(null);
        DutyShiftView current = latest == null ? null : toShiftView(latest, null);
        List<DutyItemView> mine = List.of();
        if (coachId != null) {
            mine = itemRepository.findByOwnerCoachIdAndStatusNotOrderByIdAsc(coachId, EquestrianDict.ITEM_ACCEPTED)
                    .stream().map(item -> toItemView(item, false)).toList();
        }
        boolean hasOpen = latest != null && EquestrianDict.SHIFT_OPEN.equals(latest.getStatus());
        return new DutyBoardView(current, hasOpen, mine, coachId);
    }

    /** 历史班次查询：按日期区间、负责人、事项状态组合筛选。 */
    @Transactional(readOnly = true)
    public List<DutyShiftView> history(LocalDate from, LocalDate to, String managerName, String itemStatus) {
        LocalDate start = from == null ? LocalDate.now().minusDays(30) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        if (end.isBefore(start)) {
            throw new BizException("查询日期区间不合法：开始日期晚于结束日期");
        }
        String manager = trimToNull(managerName);
        List<DutyShift> shifts;
        if (manager != null) {
            shifts = shiftRepository
                    .findByBusinessDateBetweenAndManagerNameContainingOrderByBusinessDateDescIdDesc(start, end, manager);
        } else {
            shifts = shiftRepository.findByBusinessDateBetweenOrderByBusinessDateDescIdDesc(start, end);
        }
        List<DutyShiftView> views = new ArrayList<>();
        for (DutyShift shift : shifts) {
            DutyShiftView view = toShiftView(shift, null);
            if (itemStatus != null && !itemStatus.isBlank()) {
                if (!EquestrianDict.isValidItemStatus(itemStatus)) {
                    throw new BizException("事项状态取值不合法：" + itemStatus);
                }
                boolean matched = view.items().stream().anyMatch(item -> itemStatus.equals(item.status()));
                if (!matched) {
                    continue;
                }
            }
            views.add(view);
        }
        return views;
    }

    @Transactional(readOnly = true)
    public DutyShiftView detail(Long shiftId) {
        return toShiftView(requireShift(shiftId), null);
    }

    /** 结束班次预检：返回当前班次视图，closingBlockers 逐条列出未结事项。 */
    @Transactional(readOnly = true)
    public DutyShiftView precheck(Long shiftId, OperatorRequest request) {
        Operator operator = requireManager(request);
        DutyShift shift = requireShift(shiftId);
        requireShiftManager(shift, operator);
        List<String> blockers = closingBlockers(shift);
        return toShiftView(shift, blockers);
    }

    // ============================== 开班 ==============================

    /**
     * 建立值班班次（仅 MANAGER）：
     * <ol>
     *   <li>锁全局最新班次串行化，同日同时段进行中的班次不允许重复建立；</li>
     *   <li>把上一班（最新班次）的未结事项滚动到本班 current_shift_id——
     *       无论上一班是否已结束，未确认事项都继续显示在本班首页；</li>
     *   <li>同步当班课程 / 马匹 / 栏位自动生成清单，新变更进入本班。</li>
     * </ol>
     */
    @Transactional
    public DutyShiftView openShift(DutyShiftCreateRequest request) {
        Operator operator = requireManager(request);

        LocalDate date = request.getBusinessDate() == null ? LocalDate.now() : request.getBusinessDate();
        String period = request.getPeriod() == null || request.getPeriod().isBlank()
                ? EquestrianDict.PERIOD_DAY : request.getPeriod().trim().toUpperCase();
        if (!EquestrianDict.isValidPeriod(period)) {
            throw new BizException("值班时段取值不合法：" + period + "，只支持 DAY 白班 / NIGHT 夜班");
        }

        // 锁全局最新班次：开班与开班、开班与关班、承接之间在此串行
        List<DutyShift> latestRows = shiftRepository.findLatestForUpdate(PageRequest.of(0, 1));
        DutyShift previous = latestRows.isEmpty() ? null : latestRows.get(0);

        if (shiftRepository.existsByBusinessDateAndPeriodAndStatus(date, period, EquestrianDict.SHIFT_OPEN)) {
            throw new BizException(EquestrianDict.periodName(period) + "（" + date
                    + "）已有进行中的班次，不能重复建立；未结事项会自动进入现有班次");
        }

        DutyShift shift = new DutyShift();
        shift.setBusinessDate(date);
        shift.setPeriod(period);
        shift.setManagerName(operator.name());
        shift.setHandoverNote(trimToNull(request.getHandoverNote()));
        shift.setStatus(EquestrianDict.SHIFT_OPEN);
        shift.setPrevShiftId(previous == null ? null : previous.getId());
        shift.setShiftNo(allocShiftNo(null));
        shift = shiftRepository.saveAndFlush(shift);
        shift.setShiftNo(allocShiftNo(shift.getId()));
        shift = shiftRepository.save(shift);

        // 上一班未结事项滚动到本班：current 指针前移，originating 保留，承接链不断。
        // 推进上一班清单版本：它的页面还停留在滚动前，再提交时版本对不上直接 409。
        boolean rolled = false;
        if (previous != null) {
            for (DutyItem open : itemRepository.findByCurrentShiftIdAndStatusNotOrderByIdAsc(
                    previous.getId(), EquestrianDict.ITEM_ACCEPTED)) {
                open.setCurrentShiftId(shift.getId());
                itemRepository.save(open);
                rolled = true;
            }
            if (rolled) {
                bumpListSeq(previous);
            }
        }

        int generated = syncItems(shift);
        if (generated > 0 || rolled) {
            bumpListSeq(shift);
        }
        return toShiftView(requireShift(shift.getId()), null);
    }

    // ============================== 班次说明 / 手工事项 ==============================

    /** 负责人更新本班交接总说明（乐观版本校验，旧页面提交 409） */
    @Transactional
    public DutyShiftView updateHandoverNote(Long shiftId, DutyShiftUpdateRequest request) {
        Operator operator = requireManager(request);
        lockLatest();
        DutyShift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new BizException("班次不存在，编号：" + shiftId));
        requireOpen(shift, "修改交接说明");
        requireShiftManager(shift, operator);
        assertShiftVersion(shift, request.getExpectedVersion());
        shift.setHandoverNote(trimToNull(request.getHandoverNote()));
        // 交接总说明也是清单页内容：推进版本，避免旧页面基于过期快照再提交
        bumpListSeq(shift);
        return toShiftView(requireShift(shiftId), null);
    }

    /** 负责人手工补充事项 */
    @Transactional
    public DutyShiftView createItem(Long shiftId, DutyItemCreateRequest request) {
        Operator operator = requireManager(request);
        lockLatest();
        DutyShift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new BizException("班次不存在，编号：" + shiftId));
        requireOpen(shift, "补充交接事项");
        requireShiftManager(shift, operator);
        requireLatestShift(shift);

        String title = trimToNull(request.getTitle());
        if (title == null) {
            throw new BizException("请填写事项标题");
        }
        String type = request.getItemType() == null || request.getItemType().isBlank()
                ? EquestrianDict.ITEM_TYPE_OTHER : request.getItemType().trim().toUpperCase();
        if (!EquestrianDict.isValidItemType(type)) {
            throw new BizException("事项类型取值不合法：" + type);
        }
        String priority = request.getPriority() == null || request.getPriority().isBlank()
                ? EquestrianDict.PRIORITY_NORMAL : request.getPriority().trim().toUpperCase();
        if (!EquestrianDict.isValidPriority(priority)) {
            throw new BizException("优先级取值不合法：" + priority);
        }
        if (!EquestrianDict.ITEM_TYPE_OTHER.equals(type) && request.getRefId() == null) {
            throw new BizException(EquestrianDict.itemTypeName(type) + "类事项必须关联具体对象");
        }
        validateRef(type, request.getRefId());
        if (request.getOwnerCoachId() != null) {
            requireCoach(request.getOwnerCoachId());
        }

        DutyItem item = newItem(shift, type, request.getRefId(), title,
                trimToNull(request.getDetail()), priority);
        if (request.getOwnerCoachId() != null) {
            item.setOwnerCoachId(request.getOwnerCoachId());
            item.setStatus(EquestrianDict.ITEM_DISPATCHED);
        }
        saveItemWithNo(item);
        bumpListSeq(shift);
        return toShiftView(requireShift(shiftId), null);
    }

    // ============================== 事项同步 ==============================

    /**
     * 手动同步当班清单（负责人）：开班后新出现的课程变更、新休养的马、新维护的栏位
     * 立即进入本班；已存在未结事项的对象不重复生成。
     */
    @Transactional
    public DutyShiftView sync(Long shiftId, OperatorRequest request) {
        Operator operator = requireManager(request);
        lockLatest();
        DutyShift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new BizException("班次不存在，编号：" + shiftId));
        requireOpen(shift, "同步交接清单");
        requireShiftManager(shift, operator);
        requireLatestShift(shift);
        int generated = syncItems(shift);
        if (generated > 0) {
            bumpListSeq(shift);
        }
        return toShiftView(requireShift(shiftId), null);
    }

    /**
     * 从当班课程 / 马匹状态 / 栏位安排同步生成事项，返回新增条数。
     * 去重口径：同类型同对象只要存在「未承接」事项就不重复生成；
     * 历史事项已承接后又发生新变更（如再次受影响），允许生成新事项。
     */
    private int syncItems(DutyShift shift) {
        int created = 0;

        // 课程：健康事件受影响且未取消的场次（马匹休养导致换马 / 改期）
        for (LessonSession session : sessionRepository
                .findByHealthAffectedTrueAndStatusNotOrderBySessionDateAscStartTimeAsc(
                        EquestrianDict.SESSION_CANCELED)) {
            if (hasTrackedItem(EquestrianDict.ITEM_TYPE_LESSON, session.getId())) {
                continue;
            }
            Lesson lesson = lessonRepository.findById(session.getLessonId()).orElse(null);
            Horse horse = session.getHorseId() == null ? null
                    : horseRepository.findById(session.getHorseId()).orElse(null);
            String title = "受影响课程待改期：" + (lesson == null ? "课程已删除" : lesson.getName());
            String detail = session.getSessionDate() + " " + session.getStartTime() + "-" + session.getEndTime()
                    + " 场次原定用马「" + (horse == null ? "未指定" : horse.getName() + "（" + horse.getHorseNo() + "）")
                    + "」，已被健康事件标记受影响，请安排换马或联系会员改期。";
            DutyItem item = newItem(shift, EquestrianDict.ITEM_TYPE_LESSON, session.getId(),
                    title, detail, EquestrianDict.PRIORITY_HIGH);
            saveItemWithNo(item);
            created++;
        }

        // 马匹：休养中（健康事件未闭环）的马
        for (Horse horse : horseRepository.findByStatusOrderByIdAsc(EquestrianDict.HORSE_RESTING)) {
            if (hasTrackedItem(EquestrianDict.ITEM_TYPE_HORSE, horse.getId())) {
                continue;
            }
            List<HealthEvent> openEvents = healthEventRepository
                    .findByHorseIdAndStatusNotOrderByIdAsc(horse.getId(), EquestrianDict.EVENT_CLOSED);
            String eventNos = openEvents.stream().map(HealthEvent::getEventNo).toList().toString();
            String detail = "马匹「" + horse.getHorseNo() + " " + horse.getName() + "」处于休养状态"
                    + (openEvents.isEmpty() ? "" : "，未闭环健康事件 " + eventNos
                            + "；复查合格并由负责人复训放行后才能恢复排课") + "。";
            DutyItem item = newItem(shift, EquestrianDict.ITEM_TYPE_HORSE, horse.getId(),
                    "马匹「" + horse.getName() + "」休养中，恢复排课前需完成健康闭环",
                    detail, EquestrianDict.PRIORITY_URGENT);
            saveItemWithNo(item);
            created++;
        }

        // 栏位：维护中
        for (Stall stall : stallRepository.findByStatusOrderByIdAsc(EquestrianDict.STALL_MAINTENANCE)) {
            if (hasTrackedItem(EquestrianDict.ITEM_TYPE_STALL, stall.getId())) {
                continue;
            }
            String detail = stall.getStallNo() + "（" + stall.getBarnName() + "）处于维护中，维护结束前禁止入栏；"
                    + "维修完成后请在栏位看板执行「恢复」并通知下一班。";
            DutyItem item = newItem(shift, EquestrianDict.ITEM_TYPE_STALL, stall.getId(),
                    stall.getStallNo() + " 处于维护中，完成后需恢复空闲",
                    detail, EquestrianDict.PRIORITY_NORMAL);
            saveItemWithNo(item);
            created++;
        }

        return created;
    }

    // ============================== 事项补充 ==============================

    /**
     * 补充事项说明（只追加）。
     * 普通教练只能补充分派给自己的未结事项；负责人可补充本班全部未结事项。
     */
    @Transactional
    public DutyItemView supplement(Long itemId, DutyItemSupplementRequest request) {
        Operator operator = requireOperator(request);
        String note = trimToNull(request.getNote());
        if (note == null) {
            throw new BizException("请填写要补充的说明");
        }
        lockLatest();
        DutyItem item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BizException("交接事项不存在，编号：" + itemId));
        DutyShift shift = shiftRepository.findByIdForUpdate(item.getCurrentShiftId())
                .orElseThrow(() -> new BizException("事项所在班次不存在"));
        requireOpen(shift, "补充事项");
        if (EquestrianDict.ITEM_ACCEPTED.equals(item.getStatus())) {
            throw new BizException("事项「" + item.getItemNo() + "」已承接，承接后的事项不再补充，历史记录保持完整");
        }
        if (EquestrianDict.ROLE_STAFF.equals(operator.role())) {
            if (item.getOwnerCoachId() == null || operator.coachId() == null
                    || !Objects.equals(item.getOwnerCoachId(), operator.coachId())) {
                throw new BizException("普通教练只能补充分派给自己的事项；事项「" + item.getItemNo()
                        + "」" + (item.getOwnerCoachId() == null ? "尚未分派" : "由其他教练负责"));
            }
        } else {
            requireShiftManager(shift, operator);
        }

        DutyItemNote record = new DutyItemNote();
        record.setItemId(item.getId());
        record.setNote(note);
        record.setOperator(operator.name());
        record.setRole(operator.role());
        record.setCoachId(EquestrianDict.ROLE_STAFF.equals(operator.role()) ? operator.coachId() : null);
        noteRepository.save(record);
        // 补充是清单内容的变化：推进清单版本，让停留在旧页面的其他教练 / 负责人重新载入
        bumpListSeq(shift);
        return toItemView(requireItem(itemId), true);
    }

    // ============================== 优先级 / 分派 ==============================

    /** 负责人标记优先级、分派下一班负责教练（乐观版本校验） */
    @Transactional
    public DutyItemView dispatch(Long itemId, DutyItemDispatchRequest request) {
        Operator operator = requireManager(request);
        lockLatest();
        DutyItem item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BizException("交接事项不存在，编号：" + itemId));
        DutyShift shift = shiftRepository.findByIdForUpdate(item.getCurrentShiftId())
                .orElseThrow(() -> new BizException("事项所在班次不存在"));
        requireOpen(shift, "分派事项");
        requireShiftManager(shift, operator);
        // 终态校验先于版本校验：已承接事项即使页面旧，也要明确告知「不可回退」，而不是误导成并发冲突
        if (EquestrianDict.ITEM_ACCEPTED.equals(item.getStatus())) {
            throw new BizException("事项「" + item.getItemNo() + "」已承接，不能再修改优先级或分派，已承接结果不可回退");
        }
        assertItemVersion(item, request.getExpectedVersion());

        boolean changed = false;
        if (request.getPriority() != null && !request.getPriority().isBlank()) {
            String priority = request.getPriority().trim().toUpperCase();
            if (!EquestrianDict.isValidPriority(priority)) {
                throw new BizException("优先级取值不合法：" + priority);
            }
            item.setPriority(priority);
            changed = true;
        }
        if (request.getOwnerCoachId() != null) {
            if (request.getOwnerCoachId() == -1L) {
                item.setOwnerCoachId(null);
                item.setStatus(EquestrianDict.ITEM_PENDING);
            } else {
                Coach coach = requireCoach(request.getOwnerCoachId());
                item.setOwnerCoachId(coach.getId());
                item.setStatus(EquestrianDict.ITEM_DISPATCHED);
            }
            changed = true;
        }
        if (!changed) {
            throw new BizException("没有需要更新的分派内容");
        }
        itemRepository.save(item);
        bumpListSeq(shift);
        return toItemView(requireItem(itemId), true);
    }

    // ============================== 承接确认 ==============================

    /**
     * 下一班负责人逐项确认承接：
     * <ol>
     *   <li>必须是事项当前所在班次的 MANAGER，且班次进行中；</li>
     *   <li>requestId 幂等：重复确认取回第一条承接记录，不产生重复记录；</li>
     *   <li>uk_accept_item 唯一约束兜底：即使并发双击都穿透，也只会落一条；</li>
     *   <li>事项版本乐观锁：旧页面基于过期版本提交直接 409；</li>
     *   <li>承接是终态：已承接事项不能被旧班次改回未处理。</li>
     * </ol>
     */
    @Transactional
    public DutyItemView accept(Long itemId, DutyItemAcceptRequest request) {
        Operator operator = requireManager(request);

        // 幂等优先于一切：同一 requestId 的重复提交（无论班页面多旧）都取回第一条结果
        if (request.getRequestId() != null) {
            DutyAcceptance idempotent = acceptanceRepository.findByRequestId(request.getRequestId()).orElse(null);
            if (idempotent != null) {
                return toItemView(requireItem(idempotent.getItemId()), true);
            }
        }

        lockLatest();
        DutyItem item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BizException("交接事项不存在，编号：" + itemId));
        DutyShift shift = shiftRepository.findByIdForUpdate(item.getCurrentShiftId())
                .orElseThrow(() -> new BizException("事项所在班次不存在"));
        requireOpen(shift, "确认承接");
        requireShiftManager(shift, operator);

        if (EquestrianDict.ITEM_ACCEPTED.equals(item.getStatus())) {
            // 已承接：重复确认不能生成重复记录，返回既有承接；旧班次也无法在此回退状态
            return toItemView(item, true);
        }
        assertItemVersion(item, request.getExpectedVersion());

        LocalDateTime now = LocalDateTime.now();
        DutyAcceptance acceptance = new DutyAcceptance();
        acceptance.setItemId(item.getId());
        acceptance.setShiftId(shift.getId());
        acceptance.setAcceptorName(operator.name());
        acceptance.setNote(trimToNull(request.getNote()));
        acceptance.setRequestId(trimToNull(request.getRequestId()));
        try {
            acceptanceRepository.saveAndFlush(acceptance);
        } catch (DataIntegrityViolationException e) {
            // uk_accept_item 兜底并发：说明另一笔确认已落库，整单回滚并要求重新载入
            throw new ConflictException("事项「" + item.getItemNo() + "」已被其他负责人确认承接，请重新载入最新清单");
        }

        item.setStatus(EquestrianDict.ITEM_ACCEPTED);
        item.setAcceptedShiftId(shift.getId());
        item.setAcceptedAt(now);
        item.setAcceptedBy(operator.name());
        itemRepository.save(item);
        bumpListSeq(shift);
        return toItemView(requireItem(itemId), true);
    }

    // ============================== 结束班次 ==============================

    /**
     * 结束班次（仅本班 MANAGER）。
     * confirmDefer=false：预检模式，有未结事项则逐项 BizException 拦截；
     * confirmDefer=true：必须提供延期原因（汇总或覆盖全部未结事项的逐项原因），
     * 未结事项逐条写入延期说明；若下一班已建立，事项滚动给下一班，本班正常关闭。
     */
    @Transactional
    public DutyShiftView close(Long shiftId, DutyShiftCloseRequest request) {
        Operator operator = requireManager(request);
        lockLatest();
        DutyShift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new BizException("班次不存在，编号：" + shiftId));
        requireShiftManager(shift, operator);
        assertShiftVersion(shift, request.getExpectedVersion());
        if (EquestrianDict.SHIFT_CLOSED.equals(shift.getStatus())) {
            throw new BizException("班次「" + shift.getShiftNo() + "」已结束，不能重复结束");
        }

        // 本班全部未结事项：含开班后已滚动到下一班（current 前移）但 originating 仍是本班的，
        // 结束班次前必须逐项提示 / 记录延期，不能让旧班在无债假象下关闭。
        List<DutyItem> openItems = openItemsOfShift(shift);

        if (openItems.isEmpty()) {
            return doClose(shift, null, null);
        }

        if (request == null || !request.isConfirmDefer()) {
            // 预检：逐项提示并阻止误关班
            List<String> blockers = openItems.stream()
                    .map(item -> "「" + item.getItemNo() + "·" + EquestrianDict.itemTypeName(item.getItemType())
                            + "」" + item.getTitle() + "（" + EquestrianDict.itemStatusName(item.getStatus())
                            + "，" + EquestrianDict.priorityName(item.getPriority()) + "）尚未承接")
                    .toList();
            throw new BizException("仍有 " + openItems.size() + " 个事项未承接，不能结束班次："
                    + String.join("；", blockers)
                    + "。请逐项推动承接，或在记录延期原因后确认延期交接");
        }

        String globalReason = trimToNull(request.getDelayReason());
        Map<Long, String> itemDelays = request.getItemDelays() == null ? Map.of() : request.getItemDelays();

        // 每个未结事项都要有延期去向：逐项原因或班次汇总原因至少覆盖一项
        List<String> missing = new ArrayList<>();
        for (DutyItem item : openItems) {
            String per = trimToNull(itemDelays.get(item.getId()));
            if (per == null && globalReason == null) {
                missing.add(item.getItemNo());
            }
            String reason = per != null ? per : globalReason;
            item.setDelayNote(reason);
            itemRepository.save(item);
        }
        if (!missing.isEmpty()) {
            throw new BizException("以下未承接事项缺少延期原因：" + String.join("、", missing)
                    + "；可以逐项填写，也可以填写班次统一延期原因");
        }
        if (globalReason == null) {
            // 全部事项都给了逐项原因：班次延期说明记录一个可追溯的汇总
            globalReason = "共 " + openItems.size() + " 个事项延期，逐项原因见事项明细";
        }
        return doClose(shift, globalReason, openItems);
    }

    private DutyShiftView doClose(DutyShift shift, String delayReason, List<DutyItem> knownOpenItems) {
        // 下一班已建立（prev_shift_id 指向本班）：未结事项滚动过去，继续显示在下一班首页。
        // 已在开班时滚动过的事项 current 已指向下一班，这里只滚动仍挂在本班的，不重复处理。
        DutyShift successor = findSuccessor(shift.getId());
        List<DutyItem> openItems = knownOpenItems != null ? knownOpenItems : openItemsOfShift(shift);
        if (successor != null) {
            boolean moved = false;
            for (DutyItem item : openItems) {
                if (!Objects.equals(item.getCurrentShiftId(), successor.getId())) {
                    item.setCurrentShiftId(successor.getId());
                    itemRepository.save(item);
                    moved = true;
                }
            }
            // 下一班首页清单变了，推进下一班清单版本
            if (moved) {
                bumpListSeq(successor);
            }
        }
        shift.setStatus(EquestrianDict.SHIFT_CLOSED);
        shift.setClosedAt(LocalDateTime.now());
        shift.setClosedBy(shift.getManagerName());
        shift.setDelayReason(delayReason);
        // 延期说明逐项落盘，本班清单版本也要推进：旧页面上的「结束班次预检」结果已经失效
        shift.setListSeq((shift.getListSeq() == null ? 0L : shift.getListSeq()) + 1);
        shiftRepository.save(shift);
        return toShiftView(requireShift(shift.getId()), null);
    }

    // ============================== 课程 / 马匹 / 栏位页面联动 ==============================

    /**
     * 课程排期上的交接信息：该场次属于哪个班次（事项来源班次）以及最近一次承接记录。
     * 供 SessionService 在课程详情 / 列表里批量挂载，课程页面保持原有结构。
     */
    @Transactional(readOnly = true)
    public Map<Long, SessionDutyView> dutyBySessionIds(Set<Long> sessionIds) {
        Map<Long, SessionDutyView> result = new HashMap<>();
        if (sessionIds == null || sessionIds.isEmpty()) {
            return result;
        }
        for (Long sessionId : sessionIds) {
            List<DutyItem> items = itemRepository
                    .findByItemTypeAndRefIdOrderByIdAsc(EquestrianDict.ITEM_TYPE_LESSON, sessionId);
            if (items.isEmpty()) {
                result.put(sessionId, new SessionDutyView(sessionId, false, List.of(), null));
                continue;
            }
            List<DutyItemView> itemViews = items.stream().map(item -> toItemView(item, true)).toList();
            DutyAcceptanceView latest = latestAcceptance(items);
            result.put(sessionId, new SessionDutyView(sessionId, true, itemViews, latest));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SessionDutyView dutyBySessionId(Long sessionId) {
        return dutyBySessionIds(Set.of(sessionId)).get(sessionId);
    }

    // ============================== 视图组装 ==============================

    private DutyShiftView toShiftView(DutyShift shift, List<String> blockers) {
        List<DutyItem> items = itemRepository.findByCurrentShiftIdOrderByIdAsc(shift.getId());
        List<DutyItemView> itemViews = items.stream()
                .sorted(Comparator
                        .comparing((DutyItem item) -> EquestrianDict.isOpenItem(item.getStatus()) ? 0 : 1)
                        .thenComparing(item -> EquestrianDict.priorityRank(item.getPriority()))
                        .thenComparing(DutyItem::getId))
                .map(item -> toItemView(item, true)).toList();

        int pending = 0;
        int dispatched = 0;
        int accepted = 0;
        int inherited = 0;
        for (DutyItem item : items) {
            switch (item.getStatus()) {
                case EquestrianDict.ITEM_PENDING -> pending++;
                case EquestrianDict.ITEM_DISPATCHED -> dispatched++;
                case EquestrianDict.ITEM_ACCEPTED -> accepted++;
                default -> { }
            }
            if (!Objects.equals(item.getOriginatingShiftId(), shift.getId())
                    && EquestrianDict.isOpenItem(item.getStatus())) {
                inherited++;
            }
        }
        int openCount = pending + dispatched;

        DutyShift prev = shift.getPrevShiftId() == null ? null
                : shiftRepository.findById(shift.getPrevShiftId()).orElse(null);

        return new DutyShiftView(
                shift.getId(),
                shift.getShiftNo(),
                shift.getBusinessDate(),
                shift.getPeriod(),
                EquestrianDict.periodName(shift.getPeriod()),
                shift.getManagerName(),
                shift.getHandoverNote(),
                shift.getStatus(),
                EquestrianDict.shiftStatusName(shift.getStatus()),
                EquestrianDict.SHIFT_OPEN.equals(shift.getStatus()),
                shift.getPrevShiftId(),
                prev == null ? null : prev.getShiftNo(),
                prev == null ? null : prev.getBusinessDate(),
                shift.getDelayReason(),
                shift.getListSeq(),
                shift.getStartedAt(),
                shift.getClosedAt(),
                shift.getClosedBy(),
                items.size(),
                openCount,
                pending,
                dispatched,
                accepted,
                itemViews,
                inherited,
                shift.getCreatedAt(),
                shift.getUpdatedAt(),
                blockers == null ? List.of() : blockers);
    }

    private DutyItemView toItemView(DutyItem item, boolean withChain) {
        DutyShift originShift = shiftRepository.findById(item.getOriginatingShiftId()).orElse(null);
        DutyShift currentShift = shiftRepository.findById(item.getCurrentShiftId()).orElse(null);
        Coach coach = item.getOwnerCoachId() == null ? null
                : coachRepository.findById(item.getOwnerCoachId()).orElse(null);
        DutyShift acceptedShift = item.getAcceptedShiftId() == null ? null
                : shiftRepository.findById(item.getAcceptedShiftId()).orElse(null);

        List<DutyItemNoteView> notes = List.of();
        DutyAcceptanceView acceptance = null;
        if (withChain) {
            notes = noteRepository.findByItemIdOrderByIdAsc(item.getId()).stream()
                    .map(this::toNoteView).toList();
            acceptance = acceptanceRepository.findByItemId(item.getId())
                    .map(record -> toAcceptanceView(record, item)).orElse(null);
        }

        return new DutyItemView(
                item.getId(),
                item.getItemNo(),
                item.getOriginatingShiftId(),
                originShift == null ? "" : originShift.getShiftNo(),
                item.getCurrentShiftId(),
                currentShift == null ? "" : currentShift.getShiftNo(),
                item.getItemType(),
                EquestrianDict.itemTypeName(item.getItemType()),
                item.getRefId(),
                refLabel(item),
                item.getTitle(),
                item.getDetail(),
                item.getPriority(),
                EquestrianDict.priorityName(item.getPriority()),
                item.getStatus(),
                EquestrianDict.itemStatusName(item.getStatus()),
                EquestrianDict.isOpenItem(item.getStatus()),
                item.getOwnerCoachId(),
                coach == null ? null : coach.getCoachNo(),
                coach == null ? null : coach.getName(),
                item.getDelayNote(),
                item.getAcceptedShiftId(),
                acceptedShift == null ? null : acceptedShift.getShiftNo(),
                item.getAcceptedBy(),
                item.getAcceptedAt(),
                item.getVersion(),
                notes,
                acceptance,
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    private DutyItemNoteView toNoteView(DutyItemNote note) {
        Coach coach = note.getCoachId() == null ? null
                : coachRepository.findById(note.getCoachId()).orElse(null);
        return new DutyItemNoteView(
                note.getId(),
                note.getItemId(),
                note.getNote(),
                note.getOperator(),
                note.getRole(),
                EquestrianDict.roleName(note.getRole()),
                note.getCoachId(),
                coach == null ? null : coach.getName(),
                note.getCreatedAt());
    }

    private DutyAcceptanceView toAcceptanceView(DutyAcceptance record, DutyItem item) {
        DutyShift shift = shiftRepository.findById(record.getShiftId()).orElse(null);
        return new DutyAcceptanceView(
                record.getId(),
                record.getItemId(),
                item == null ? "" : item.getItemNo(),
                item == null ? "" : item.getTitle(),
                record.getShiftId(),
                shift == null ? "" : shift.getShiftNo(),
                record.getAcceptorName(),
                record.getNote(),
                record.getAcceptedAt());
    }

    private DutyAcceptanceView latestAcceptance(List<DutyItem> items) {
        DutyAcceptanceView latest = null;
        for (DutyItem item : items) {
            Optional<DutyAcceptance> record = acceptanceRepository.findByItemId(item.getId());
            if (record.isPresent() && (latest == null
                    || record.get().getAcceptedAt().isAfter(
                            latest.acceptedAt() == null ? LocalDateTime.MIN : latest.acceptedAt()))) {
                latest = toAcceptanceView(record.get(), item);
            }
        }
        return latest;
    }

    private String refLabel(DutyItem item) {
        if (item.getRefId() == null) {
            return "";
        }
        Long refId = item.getRefId();
        switch (item.getItemType()) {
            case EquestrianDict.ITEM_TYPE_LESSON -> {
                LessonSession session = sessionRepository.findById(refId).orElse(null);
                if (session == null) {
                    return "场次 #" + refId + "（已删除）";
                }
                Lesson lesson = lessonRepository.findById(session.getLessonId()).orElse(null);
                return session.getSessionDate() + " " + session.getStartTime() + " "
                        + (lesson == null ? "课程已删除" : lesson.getName());
            }
            case EquestrianDict.ITEM_TYPE_HORSE -> {
                Horse horse = horseRepository.findById(refId).orElse(null);
                return horse == null ? "马匹 #" + refId + "（已删除）"
                        : horse.getHorseNo() + " " + horse.getName();
            }
            case EquestrianDict.ITEM_TYPE_STALL -> {
                Stall stall = stallRepository.findById(refId).orElse(null);
                return stall == null ? "栏位 #" + refId + "（已删除）"
                        : stall.getStallNo() + "（" + stall.getBarnName() + "）";
            }
            default -> {
                return "#" + refId;
            }
        }
    }

    // ============================== 内部工具 ==============================

    private DutyItem newItem(DutyShift shift, String type, Long refId, String title, String detail, String priority) {
        DutyItem item = new DutyItem();
        item.setOriginatingShiftId(shift.getId());
        item.setCurrentShiftId(shift.getId());
        item.setItemType(type);
        item.setRefId(refId);
        item.setTitle(title);
        item.setDetail(detail);
        item.setPriority(priority);
        item.setStatus(EquestrianDict.ITEM_PENDING);
        return item;
    }

    /** 事项编号：先放唯一临时号，拿到自增 id 后改为 DI-00000x（同事务内，外部只见正式号） */
    private void saveItemWithNo(DutyItem item) {
        item.setItemNo("DI-TMP-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        DutyItem saved = itemRepository.saveAndFlush(item);
        long seq = Math.max(saved.getId(),
                itemRepository.findById(saved.getId()).map(DutyItem::getId).orElse(0L));
        saved.setItemNo(String.format("DI-%06d", seq));
        itemRepository.save(saved);
    }

    /** 班次编号同事项编号口径 */
    private String allocShiftNo(Long id) {
        if (id == null) {
            return "DS-TMP-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        long maxId = shiftRepository.findFirstByOrderByIdDesc().map(DutyShift::getId).orElse(id);
        return String.format("DS-%06d", Math.max(id, maxId));
    }

    private boolean hasTrackedItem(String type, Long refId) {
        // 只要该对象已有交接事项（无论承接与否）就不重复生成：
        // 已承接的课程事项代表这条变更已经走完交接，不能在下一班又冒出一条新的；
        // 若同一场次再次发生变更，应由负责人手工补充新事项并关联场次。
        return !itemRepository.findByItemTypeAndRefIdOrderByIdAsc(type, refId).isEmpty();
    }

    /**
     * 结束班次视角下本班的全部未结事项：
     * 既包括还挂在本班 current 清单上的，也包括开班后已滚动到下一班（current 前移）
     * 但 originating 仍是本班、且尚未承接的。后者同样要逐项提示 / 记录延期。
     */
    private List<DutyItem> openItemsOfShift(DutyShift shift) {
        Map<Long, DutyItem> merged = new LinkedHashMap<>();
        for (DutyItem item : itemRepository.findByCurrentShiftIdAndStatusNotOrderByIdAsc(
                shift.getId(), EquestrianDict.ITEM_ACCEPTED)) {
            merged.put(item.getId(), item);
        }
        for (DutyItem item : itemRepository.findByOriginatingShiftIdAndStatusNotOrderByIdAsc(
                shift.getId(), EquestrianDict.ITEM_ACCEPTED)) {
            merged.putIfAbsent(item.getId(), item);
        }
        return merged.values().stream().sorted(Comparator.comparing(DutyItem::getId)).toList();
    }

    private DutyShift findSuccessor(Long shiftId) {
        List<DutyShift> nexts = shiftRepository.findByPrevShiftId(shiftId);
        return nexts.stream().max(Comparator.comparing(DutyShift::getId)).orElse(null);
    }

    private List<String> closingBlockers(DutyShift shift) {
        if (EquestrianDict.SHIFT_CLOSED.equals(shift.getStatus())) {
            return List.of("班次「" + shift.getShiftNo() + "」已结束");
        }
        return itemRepository.findByCurrentShiftIdAndStatusNotOrderByIdAsc(
                        shift.getId(), EquestrianDict.ITEM_ACCEPTED).stream()
                .map(item -> "「" + item.getItemNo() + "·" + EquestrianDict.itemTypeName(item.getItemType())
                        + "」" + item.getTitle() + "（" + EquestrianDict.itemStatusName(item.getStatus())
                        + "，" + EquestrianDict.priorityName(item.getPriority()) + "）尚未承接")
                .toList();
    }

    private void validateRef(String type, Long refId) {
        switch (type) {
            case EquestrianDict.ITEM_TYPE_LESSON -> {
                if (sessionRepository.findById(refId).isEmpty()) {
                    throw new BizException("关联课程场次不存在，编号：" + refId);
                }
            }
            case EquestrianDict.ITEM_TYPE_HORSE -> {
                if (horseRepository.findById(refId).isEmpty()) {
                    throw new BizException("关联马匹不存在，编号：" + refId);
                }
            }
            case EquestrianDict.ITEM_TYPE_STALL -> {
                if (stallRepository.findById(refId).isEmpty()) {
                    throw new BizException("关联栏位不存在，编号：" + refId);
                }
            }
            default -> { }
        }
    }

    private Coach requireCoach(Long coachId) {
        return coachRepository.findById(coachId)
                .orElseThrow(() -> new BizException("教练不存在，编号：" + coachId));
    }

    private DutyShift requireShift(Long shiftId) {
        if (shiftId == null) {
            throw new BizException("请先选择值班班次");
        }
        return shiftRepository.findById(shiftId)
                .orElseThrow(() -> new BizException("值班班次不存在，编号：" + shiftId));
    }

    private DutyItem requireItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new BizException("交接事项不存在，编号：" + itemId));
    }

    private void requireOpen(DutyShift shift, String scene) {
        if (!EquestrianDict.SHIFT_OPEN.equals(shift.getStatus())) {
            throw new BizException("班次「" + shift.getShiftNo() + "」已结束，不能再" + scene
                    + "；历史只可查询，未结事项请在当前最新班次处理");
        }
    }

    private DutyShift lockLatest() {
        List<DutyShift> latest = shiftRepository.findLatestForUpdate(PageRequest.of(0, 1));
        if (latest.isEmpty()) {
            throw new BizException("还没有任何值班班次，请先由负责人建立班次");
        }
        return latest.get(0);
    }

    /**
     * 新变更只能进入「当前最新班次」：在旧班次页面提交补充事项 / 同步清单时，
     * 必须明确拒绝并引导到最新班次，避免课程变更挂到旧班、下一班首页看不到。
     */
    private void requireLatestShift(DutyShift shift) {
        DutyShift latest = shiftRepository.findFirstByOrderByIdDesc().orElse(null);
        if (latest == null || !Objects.equals(latest.getId(), shift.getId())) {
            throw new ConflictException("当前已有更新的值班班次"
                    + (latest == null ? "" : "（" + latest.getShiftNo() + "）")
                    + "，新出现的课程变更必须进入最新班次，请重新载入交接看板");
        }
    }

    /**
     * 乐观版本校验：expected 是页面看到的「清单版本 listSeq」。
     * 期间任何影响清单的动作（下一班建立滚动、同步、补充、分派、承接、关班滚动）都会让它 +1，
     * 对不上就 409，提示数据已变化并要求重新载入。
     */
    private void assertShiftVersion(DutyShift shift, Long expected) {
        if (expected == null) {
            throw new BizException("缺少班次数据版本号，请重新载入班次后再提交");
        }
        long current = shift.getListSeq() == null ? 0L : shift.getListSeq();
        if (expected.longValue() != current) {
            throw new ConflictException("班次「" + shift.getShiftNo() + "」的交接清单已被其他人更新"
                    + "（你看到的版本 v" + expected + "，当前版本 v" + current
                    + "），请重新载入最新清单后再提交，旧内容未被保存");
        }
    }

    /** 清单发生变化时推进清单版本；连同 @Version 一起落库（save 自动递增 @Version） */
    private void bumpListSeq(DutyShift shift) {
        shift.setListSeq((shift.getListSeq() == null ? 0L : shift.getListSeq()) + 1);
        shiftRepository.save(shift);
    }

    private void assertItemVersion(DutyItem item, Long expected) {
        if (expected == null) {
            throw new BizException("缺少事项数据版本号，请重新载入事项后再提交");
        }
        long current = item.getVersion() == null ? 0L : item.getVersion();
        if (expected.longValue() != current) {
            throw new ConflictException("事项「" + item.getItemNo() + "」已被其他人更新"
                    + "（你看到的版本 v" + expected + "，当前版本 v" + current
                    + "），请重新载入最新清单后再提交，旧内容未被保存");
        }
    }

    private Operator requireManager(OperatorRequest request) {
        Operator operator = requireOperator(request);
        if (!EquestrianDict.ROLE_MANAGER.equals(operator.role())) {
            throw new BizException("该操作只有值班负责人（MANAGER）可以执行，普通教练直调接口也会被拒绝");
        }
        return operator;
    }    private void requireShiftManager(DutyShift shift, Operator operator) {
        if (!EquestrianDict.ROLE_MANAGER.equals(operator.role())) {
            throw new BizException("该操作只有值班负责人可以执行");
        }
        if (!Objects.equals(shift.getManagerName(), operator.name())) {
            throw new BizException("班次「" + shift.getShiftNo() + "」的值班负责人是「"
                    + shift.getManagerName() + "」，只有本班负责人可以执行该操作");
        }
    }

    /** 校验并归一化操作人身份；角色合法性后端兜底，不信任前端按钮显隐 */
    private Operator requireOperator(OperatorRequest request) {
        if (request == null) {
            throw new BizException("请求体为空");
        }
        String name = trimToNull(request.getOperatorName());
        if (name == null) {
            throw new BizException("请先选择操作人（值班身份）");
        }
        String role = request.getOperatorRole();
        if (!EquestrianDict.isValidRole(role)) {
            throw new BizException("操作人角色不合法：" + role + "，只支持 STAFF / MANAGER");
        }
        Long coachId = request.getCoachId();
        if (EquestrianDict.ROLE_STAFF.equals(role) && coachId != null) {
            // 传了教练 id 就必须真实存在，防止前端伪造归属
            requireCoach(coachId);
        }
        return new Operator(name, role, coachId);
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record Operator(String name, String role, Long coachId) {
    }
}
