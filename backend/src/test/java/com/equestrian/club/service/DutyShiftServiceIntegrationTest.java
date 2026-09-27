package com.equestrian.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.equestrian.club.common.BizException;
import com.equestrian.club.common.ConflictException;
import com.equestrian.club.dict.EquestrianDict;
import com.equestrian.club.domain.Coach;
import com.equestrian.club.domain.CoachRepository;
import com.equestrian.club.domain.DutyAcceptance;
import com.equestrian.club.domain.DutyAcceptanceRepository;
import com.equestrian.club.domain.DutyItem;
import com.equestrian.club.domain.DutyItemNoteRepository;
import com.equestrian.club.domain.DutyItemRepository;
import com.equestrian.club.domain.DutyShift;
import com.equestrian.club.domain.DutyShiftRepository;
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
import com.equestrian.club.dto.view.DutyBoardView;
import com.equestrian.club.dto.view.DutyItemView;
import com.equestrian.club.dto.view.DutyShiftView;
import com.equestrian.club.dto.view.SessionDutyView;

/**
 * 值班交接链路集成测试（H2 / MySQL 兼容模式）：
 * 开班清单生成、权限后端兜底、事项状态机、跨班滚动、结束班次拦截与延期、
 * 乐观版本冲突、承接幂等、失败整体回滚、课程详情交接信息、历史查询。
 */
@SpringBootTest
@Transactional
class DutyShiftServiceIntegrationTest {

    private static final String MANAGER = "总教练陈骁";
    private static final String OTHER_MANAGER = "负责人赵崇";
    private static final String STAFF_LIN = "教练林婉";

    @Autowired
    private DutyShiftService dutyService;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private DutyShiftRepository shiftRepository;
    @Autowired
    private DutyItemRepository itemRepository;
    @Autowired
    private DutyItemNoteRepository noteRepository;
    @Autowired
    private DutyAcceptanceRepository acceptanceRepository;
    @Autowired
    private HorseRepository horseRepository;
    @Autowired
    private StallRepository stallRepository;
    @Autowired
    private CoachRepository coachRepository;
    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private LessonSessionRepository sessionRepository;

    private Long affectedSessionId;
    private Long restingHorseId;
    private Long maintenanceStallId;
    private Long coachLinId;
    private Long coachZhaoId;

    @BeforeEach
    void setUp() {
        // 马：一匹在役、一匹休养（对应未闭环课程变更 / 马匹事项）
        Horse active = horse("H-T1", "追风", EquestrianDict.HORSE_ACTIVE);
        horseRepository.save(active);
        Horse resting = horse("H-T2", "玉兔", EquestrianDict.HORSE_RESTING);
        horseRepository.save(resting);
        restingHorseId = resting.getId();

        // 栏位：一个维护中
        Stall stall = new Stall();
        stall.setStallNo("STM-T1");
        stall.setBarnName("B 区马房");
        stall.setStatus(EquestrianDict.STALL_MAINTENANCE);
        stallRepository.save(stall);
        maintenanceStallId = stall.getId();

        // 教练
        Coach lin = coach("C-T1", "林婉");
        Coach zhao = coach("C-T2", "赵崇");
        coachRepository.save(lin);
        coachRepository.save(zhao);
        coachLinId = lin.getId();
        coachZhaoId = zhao.getId();

        // 课程 + 受影响场次（玉兔名下、未来、未取消、health_affected=1）
        Lesson lesson = new Lesson();
        lesson.setLessonNo("L-T1");
        lesson.setName("初级骑乘入门");
        lesson.setCategory(EquestrianDict.CATEGORY_BASIC);
        lesson.setCapacity(6);
        lesson.setDurationMin(60);
        lesson.setPrice(java.math.BigDecimal.valueOf(380));
        lesson.setTimesCost(1);
        lesson.setStatus(EquestrianDict.LESSON_ON);
        lessonRepository.save(lesson);

        LessonSession session = new LessonSession();
        session.setLessonId(lesson.getId());
        session.setCoachId(lin.getId());
        session.setHorseId(resting.getId());
        session.setSessionDate(LocalDate.now().plusDays(1));
        session.setStartTime("09:00");
        session.setEndTime("10:00");
        session.setCapacity(6);
        session.setBookedCount(0);
        session.setStatus(EquestrianDict.SESSION_SCHEDULED);
        session.setHealthAffected(true);
        sessionRepository.save(session);
        affectedSessionId = session.getId();
    }

    // ------------------------------ 开班与清单 ------------------------------

    @Test
    void openShift_generatesChecklistFromLessonsHorsesStalls() {
        DutyShiftView shift = dutyService.openShift(openReq(MANAGER, null));

        assertThat(shift.open()).isTrue();
        assertThat(shift.shiftNo()).startsWith("DS-");
        // 受影响课程 1 + 休养马 1 + 维护栏位 1
        assertThat(shift.totalItems()).isEqualTo(3);
        assertThat(shift.pendingCount()).isEqualTo(3);
        assertThat(itemsOfType(shift, EquestrianDict.ITEM_TYPE_LESSON)).hasSize(1);
        assertThat(itemsOfType(shift, EquestrianDict.ITEM_TYPE_HORSE)).hasSize(1);
        assertThat(itemsOfType(shift, EquestrianDict.ITEM_TYPE_STALL)).hasSize(1);
        DutyItemView lessonItem = itemsOfType(shift, EquestrianDict.ITEM_TYPE_LESSON).get(0);
        assertThat(lessonItem.refId()).isEqualTo(affectedSessionId);
        assertThat(lessonItem.priority()).isEqualTo(EquestrianDict.PRIORITY_HIGH);
    }

    @Test
    void openShift_staffForbidden_backendEnforced() {
        assertThatThrownBy(() -> dutyService.openShift(openReq(STAFF_LIN, coachLinId)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("值班负责人");
    }

    @Test
    void duplicateSamePeriodOpenShiftRejected() {
        dutyService.openShift(openReq(MANAGER, null));
        assertThatThrownBy(() -> dutyService.openShift(openReq(MANAGER, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("进行中的班次");
    }

    @Test
    void newCourseChange_syncEntersCurrentShift_notOldOne() {
        DutyShiftView first = dutyService.openShift(openReq(MANAGER, null));

        // 下一班先建立（两个班次 OPEN 并存的交接高峰）
        DutyShiftCreateRequest nextReq = openReq(OTHER_MANAGER, null);
        nextReq.setBusinessDate(LocalDate.now().plusDays(1));
        DutyShiftView second = dutyService.openShift(nextReq);

        // 开班之后新出现一匹休养马（新的课程变更）
        Horse extra = horse("H-T3", "黑旋风", EquestrianDict.HORSE_RESTING);
        horseRepository.save(extra);

        // 旧班次页面无法把新变更塞进旧班：同步只允许最新班次
        assertThatThrownBy(() -> dutyService.sync(first.id(), operator(MANAGER, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("最新班次");

        // 新变更同步进当前最新班次（下一班），且来源班次是下一班而不是旧班
        int before = second.totalItems();
        DutyShiftView synced = dutyService.sync(second.id(), operator(OTHER_MANAGER, null));
        assertThat(synced.totalItems()).isEqualTo(before + 1);
        DutyItemView newHorseItem = synced.items().stream()
                .filter(i -> i.refId().equals(extra.getId())).findFirst().orElseThrow();
        assertThat(newHorseItem.originatingShiftId()).isEqualTo(second.id());
    }

    // ------------------------------ 补充权限 ------------------------------

    @Test
    void staffCanOnlySupplementOwnDispatchedItem() {
        DutyShiftView shift = dutyService.openShift(openReq(MANAGER, null));
        Long itemId = shift.items().get(0).id();

        // 未分派：普通教练补充被拒
        assertThatThrownBy(() -> dutyService.supplement(itemId, noteReq(STAFF_LIN, coachLinId, "我先看看")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("分派给自己");

        // 负责人分派给林婉
        DutyItemView dispatched = dutyService.dispatch(itemId,
                dispatchReq(MANAGER, null, EquestrianDict.PRIORITY_HIGH, coachLinId, shift.items().get(0).version()));

        // 别的教练不能补充
        assertThatThrownBy(() -> dutyService.supplement(itemId, noteReq("教练赵崇", coachZhaoId, "我也看看")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("其他教练负责");

        // 负责教练可以补充，只追加不改写；补充推进清单版本
        long seqBefore = dutyService.detail(shift.id()).version();
        DutyItemView after = dutyService.supplement(itemId, noteReq(STAFF_LIN, coachLinId, "冰敷两次"));
        assertThat(after.notes()).extracting(note -> note.note()).contains("冰敷两次");
        assertThat(noteRepository.findByItemIdOrderByIdAsc(itemId)).hasSize(1);
        assertThat(dutyService.detail(shift.id()).version()).isEqualTo(seqBefore + 1);

        // 分派后状态为已分派
        assertThat(dispatched.status()).isEqualTo(EquestrianDict.ITEM_DISPATCHED);
        assertThat(dispatched.ownerCoachId()).isEqualTo(coachLinId);
    }

    @Test
    void managerCanUpdateHandoverNote_staleVersionConflicts() {
        DutyShiftView shift = dutyService.openShift(openReq(MANAGER, null));

        DutyShiftUpdateRequest req = new DutyShiftUpdateRequest();
        req.setOperatorName(MANAGER);
        req.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        req.setHandoverNote("新版交接说明");
        req.setExpectedVersion(shift.version() - 1); // 旧页面版本
        assertThatThrownBy(() -> dutyService.updateHandoverNote(shift.id(), req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("重新载入");

        req.setExpectedVersion(shift.version());
        DutyShiftView updated = dutyService.updateHandoverNote(shift.id(), req);
        assertThat(updated.handoverNote()).isEqualTo("新版交接说明");
    }

    // ------------------------------ 分派与承接状态机 ------------------------------

    @Test
    void accept_onlyNextShiftManager_andIdempotent() {
        DutyShiftView first = dutyService.openShift(openReq(MANAGER, null));
        Long itemId = first.items().get(0).id();

        // 普通教练不能承接
        assertThatThrownBy(() -> dutyService.accept(itemId, acceptReq(STAFF_LIN, coachLinId, "ok", 0L)))
                .isInstanceOf(BizException.class);
        // 非本班负责人不能承接
        assertThatThrownBy(() -> dutyService.accept(itemId, acceptReq(OTHER_MANAGER, null, "ok", 0L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("本班负责人");
        // 旧版本不能承接
        assertThatThrownBy(() -> dutyService.accept(itemId,
                acceptReq(MANAGER, null, "ok", first.items().get(0).version() - 1)))
                .isInstanceOf(ConflictException.class);

        long itemVersion = first.items().get(0).version();
        DutyItemAcceptRequest req = acceptReq(MANAGER, null, "已确认", itemVersion);
        DutyItemView accepted = dutyService.accept(itemId, req);
        assertThat(accepted.status()).isEqualTo(EquestrianDict.ITEM_ACCEPTED);
        assertThat(accepted.acceptance()).isNotNull();
        assertThat(accepted.acceptance().acceptorName()).isEqualTo(MANAGER);

        // 重复确认（同一 requestId）：不产生重复承接记录
        dutyService.accept(itemId, req);
        List<DutyAcceptance> all = acceptanceRepository.findAll();
        long sameItem = all.stream().filter(a -> a.getItemId().equals(itemId)).count();
        assertThat(sameItem).isEqualTo(1);
        assertThat(noteRepository.findAll()).isEmpty(); // 承接不该写补充记录
    }

    @Test
    void acceptedItemCanNeverBeResetByOldShift() {
        DutyShiftView first = dutyService.openShift(openReq(MANAGER, null));
        Long itemId = first.items().get(0).id();
        dutyService.accept(itemId, acceptReq(MANAGER, null, "确认", first.items().get(0).version()));

        // 已承接后再分派（改回未处理）必须被状态机拒绝
        DutyItemDispatchRequest dispatch = dispatchReq(MANAGER, null,
                EquestrianDict.PRIORITY_NORMAL, null, first.version());
        assertThatThrownBy(() -> dutyService.dispatch(itemId, dispatch))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已承接");

        // 已承接事项不能再补充
        assertThatThrownBy(() -> dutyService.supplement(itemId, noteReq(MANAGER, null, "想改历史")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已承接");
    }

    // ------------------------------ 下一班建立与滚动 ------------------------------

    @Test
    void openNextShift_rollsUnacceptedItemsForward_acceptedStaysTraceable() {
        DutyShiftView first = dutyService.openShift(openReq(MANAGER, null));
        List<DutyItemView> items = first.items();
        Long acceptedItemId = items.get(0).id();
        Long openItemId = items.get(1).id();
        dutyService.accept(acceptedItemId, acceptReq(MANAGER, null, "承接", items.get(0).version()));

        // 建立下一班（另一位负责人）
        DutyShiftCreateRequest req = openReq(OTHER_MANAGER, null);
        req.setBusinessDate(LocalDate.now().plusDays(1));
        DutyShiftView second = dutyService.openShift(req);

        // 未承接的 2 项滚动到下一班首页；已承接的课程事项不重复生成，
        // 休养马 / 维护栏位事项来自上一班未承接，也不再重复同步
        assertThat(second.items()).hasSize(2);
        assertThat(second.items()).allMatch(DutyItemView::open);
        assertThat(second.inheritedOpenCount()).isEqualTo(2);
        DutyItemView rolled = second.items().stream()
                .filter(i -> i.id().equals(openItemId)).findFirst().orElseThrow();
        assertThat(rolled.currentShiftId()).isEqualTo(second.id());
        assertThat(rolled.originatingShiftId()).isEqualTo(first.id());
        assertThat(rolled.originatingShiftNo()).isEqualTo(first.shiftNo());

        // 看板展示的是最新班次，未确认事项没有因上一班结束而消失
        DutyBoardView board = dutyService.board(null);
        assertThat(board.current().id()).isEqualTo(second.id());
        assertThat(board.current().openCount()).isEqualTo(2);

        // 旧班次页面拿着旧 listSeq 再提交，被 409 拦下要求重新载入
        DutyShiftUpdateRequest stale = new DutyShiftUpdateRequest();
        stale.setOperatorName(MANAGER);
        stale.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        stale.setHandoverNote("旧班次想改说明");
        stale.setExpectedVersion(first.version());
        assertThatThrownBy(() -> dutyService.updateHandoverNote(first.id(), stale))
                .isInstanceOf(ConflictException.class);
    }

    // ------------------------------ 结束班次 ------------------------------

    @Test
    void closeShift_blockedItemByItemWithoutDeferReason() {
        DutyShiftView shift = dutyService.openShift(openReq(MANAGER, null));

        DutyShiftCloseRequest pre = closeReq(MANAGER, false, null, shift.version());
        assertThatThrownBy(() -> dutyService.close(shift.id(), pre))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("3 个事项未承接")
                .hasMessageContaining("DI-");

        // 预检接口逐条返回
        DutyShiftView checked = dutyService.precheck(shift.id(), operator(MANAGER, null));
        assertThat(checked.closingBlockers()).hasSize(3);

        // confirmDefer=true 但没有任何延期原因，仍被拒
        assertThatThrownBy(() -> dutyService.close(shift.id(), closeReq(MANAGER, true, null, shift.version())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("延期原因");

        // 非负责人不能关班
        assertThatThrownBy(() -> dutyService.close(shift.id(), closeReq(OTHER_MANAGER, true, "统一延期", shift.version())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("本班负责人");
    }

    @Test
    void closeShift_withDeferReason_completesAndPersistsItemDelays() {
        DutyShiftView first = dutyService.openShift(openReq(MANAGER, null));
        // 先承接一条，剩余延期
        Long acceptItem = first.items().get(0).id();
        dutyService.accept(acceptItem, acceptReq(MANAGER, null, "ok", first.items().get(0).version()));

        long versionAfterAccept = dutyService.detail(first.id()).version();
        DutyItemView remainA = dutyService.detail(first.id()).items().get(0);
        DutyItemView remainB = dutyService.detail(first.id()).items().get(1);
        Map<Long, String> delays = Map.of(
                remainA.id(), "A 等兽医复查",
                remainB.id(), "B 等维修班组");
        DutyShiftView closed = dutyService.close(first.id(),
                closeReq(MANAGER, true, null, versionAfterAccept, delays));

        assertThat(closed.open()).isFalse();
        assertThat(closed.closedBy()).isEqualTo(MANAGER);
        assertThat(closed.delayReason()).contains("延期");
        assertThat(itemRepository.findById(remainA.id()).orElseThrow().getDelayNote()).isEqualTo("A 等兽医复查");
        assertThat(itemRepository.findById(remainB.id()).orElseThrow().getDelayNote()).isEqualTo("B 等维修班组");

        // 已结束不能重复结束
        assertThatThrownBy(() -> dutyService.close(first.id(),
                closeReq(MANAGER, true, "再关一次", closed.version())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已结束");
    }

    @Test
    void closeShift_rollsOpenItemsToExistingNextShift() {
        DutyShiftView first = dutyService.openShift(openReq(MANAGER, null));
        // 下一班先建立（题目要求的并发场景：结束班次与下一班建立同时发生）
        DutyShiftCreateRequest nextReq = openReq(OTHER_MANAGER, null);
        nextReq.setBusinessDate(LocalDate.now().plusDays(1));
        DutyShiftView second = dutyService.openShift(nextReq);
        assertThat(second.openCount()).isGreaterThanOrEqualTo(3);

        // 上一班带着未承接事项结束（统一延期原因）
        long latestFirstVersion = dutyService.detail(first.id()).version();
        DutyShiftView closed = dutyService.close(first.id(),
                closeReq(MANAGER, true, "统一延期到次日", latestFirstVersion));
        assertThat(closed.open()).isFalse();

        // 事项仍挂在下一班首页，未消失；delay_note 已逐条落盘
        DutyShiftView nextView = dutyService.detail(second.id());
        assertThat(nextView.openCount()).isGreaterThanOrEqualTo(3);
        assertThat(nextView.items()).allMatch(item -> item.currentShiftId().equals(second.id()));
        assertThat(itemRepository.findAll()).allMatch(item ->
                item.getStatus().equals(EquestrianDict.ITEM_ACCEPTED)
                        || (item.getCurrentShiftId().equals(second.id()) && "统一延期到次日".equals(item.getDelayNote())));
    }

    // ------------------------------ 失败不留半条记录 ------------------------------

    @Test
    void failedClose_leavesNoPartialAcceptanceOrStatusChange() {
        DutyShiftView shift = dutyService.openShift(openReq(MANAGER, null));
        long acceptancesBefore = acceptanceRepository.count();
        long openBefore = itemRepository.findByCurrentShiftIdAndStatusNotOrderByIdAsc(
                shift.id(), EquestrianDict.ITEM_ACCEPTED).size();

        // 缺延期原因导致整笔关班事务回滚：状态、延期说明都不落
        assertThatThrownBy(() -> dutyService.close(shift.id(),
                closeReq(MANAGER, true, null, shift.version())))
                .isInstanceOf(BizException.class);

        DutyShift reloaded = shiftRepository.findById(shift.id()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EquestrianDict.SHIFT_OPEN);
        assertThat(reloaded.getClosedAt()).isNull();
        assertThat(acceptanceRepository.count()).isEqualTo(acceptancesBefore);
        assertThat(itemRepository.findByCurrentShiftIdAndStatusNotOrderByIdAsc(
                shift.id(), EquestrianDict.ITEM_ACCEPTED)).hasSize((int) openBefore);
    }

    // ------------------------------ 课程详情联动 ------------------------------

    @Test
    void sessionDetail_showsOwningShiftAndLatestAcceptance() {
        DutyShiftView shift = dutyService.openShift(openReq(MANAGER, null));
        SessionDutyView before = sessionService.detail(affectedSessionId).duty();
        assertThat(before.tracked()).isTrue();
        assertThat(before.items()).hasSize(1);
        assertThat(before.items().get(0).originatingShiftNo()).isEqualTo(shift.shiftNo());

        // 承接后课程详情出现最近一次承接记录
        Long itemId = before.items().get(0).id();
        long itemVersion = before.items().get(0).version();
        dutyService.accept(itemId, acceptReq(MANAGER, null, "课程改期已联系会员", itemVersion));

        SessionDutyView after = sessionService.detail(affectedSessionId).duty();
        assertThat(after.latestAcceptance()).isNotNull();
        assertThat(after.latestAcceptance().note()).isEqualTo("课程改期已联系会员");
        assertThat(after.items().get(0).status()).isEqualTo(EquestrianDict.ITEM_ACCEPTED);

        // 列表接口也批量挂上（无 N+1），未被跟踪的场次 tracked=false
        assertThat(sessionService.list()).filteredOn(s -> s.id().equals(affectedSessionId))
                .first().extracting(s -> s.duty().tracked()).isEqualTo(true);
    }

    // ------------------------------ 历史查询 ------------------------------

    @Test
    void history_filterByDateManagerAndItemStatus() {
        DutyShiftView first = dutyService.openShift(openReq(MANAGER, null));
        dutyService.close(first.id(), closeReq(MANAGER, true, "延期", first.version()));

        DutyShiftCreateRequest nextReq = openReq(OTHER_MANAGER, null);
        nextReq.setBusinessDate(LocalDate.now().plusDays(1));
        dutyService.openShift(nextReq);

        // 按负责人
        List<DutyShiftView> byManager = dutyService.history(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(2), OTHER_MANAGER, null);
        assertThat(byManager).extracting(DutyShiftView::managerName).containsOnly(OTHER_MANAGER);

        // 按事项状态：找得到含待处理事项的班
        List<DutyShiftView> pending = dutyService.history(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(2), null, EquestrianDict.ITEM_PENDING);
        assertThat(pending).isNotEmpty();

        // 按日期外区间：查不到
        assertThat(dutyService.history(LocalDate.now().plusDays(30), LocalDate.now().plusDays(40), null, null))
                .isEmpty();
    }

    // ------------------------------ 已结束班次只读 ------------------------------

    @Test
    void closedShift_isReadOnly_acceptAndSupplementRejected() {
        DutyShiftView first = dutyService.openShift(openReq(MANAGER, null));
        // 建立下一班承接未结事项，然后把上一班关掉
        DutyShiftCreateRequest nextReq = openReq(OTHER_MANAGER, null);
        nextReq.setBusinessDate(LocalDate.now().plusDays(1));
        DutyShiftView second = dutyService.openShift(nextReq);
        Long rolledItemId = second.items().get(0).id();
        long firstVersion = dutyService.detail(first.id()).version();
        dutyService.close(first.id(), closeReq(MANAGER, true, "统一延期", firstVersion));

        // 已结束班次不能再编辑说明 / 手工加事项
        assertThatThrownBy(() -> dutyService.updateHandoverNote(first.id(),
                noteLikeUpdate(MANAGER, "想改", dutyService.detail(first.id()).version())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已结束");

        // 事项滚动到下一班后，旧班负责人（不再是事项当前班负责人）不能承接
        long rolledVersionBeforeClose = second.items().get(0).version();
        assertThatThrownBy(() -> dutyService.accept(rolledItemId,
                acceptReq(MANAGER, null, "旧班想承接", rolledVersionBeforeClose)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("本班负责人");

        // 关班时逐条落延期说明会推进事项版本：下一班承接必须重新读取最新版本
        long staleVersion = rolledVersionBeforeClose;
        long freshVersion = dutyService.detail(second.id()).items().stream()
                .filter(i -> i.id().equals(rolledItemId)).findFirst().orElseThrow().version();
        assertThat(freshVersion).isGreaterThan(staleVersion);
        assertThatThrownBy(() -> dutyService.accept(rolledItemId,
                acceptReq(OTHER_MANAGER, null, "用旧版本承接", staleVersion)))
                .isInstanceOf(ConflictException.class);

        // 下一班负责人可以正常承接
        DutyItemView accepted = dutyService.accept(rolledItemId,
                acceptReq(OTHER_MANAGER, null, "下一班承接", freshVersion));
        assertThat(accepted.status()).isEqualTo(EquestrianDict.ITEM_ACCEPTED);
        assertThat(accepted.acceptance().shiftNo()).isEqualTo(second.shiftNo());
    }

    // ------------------------------ 手工事项与分派 ------------------------------

    @Test
    void managerCreatesManualItem_andDispatchMovesStatus() {
        DutyShiftView shift = dutyService.openShift(openReq(MANAGER, null));

        DutyItemCreateRequest create = new DutyItemCreateRequest();
        create.setOperatorName(MANAGER);
        create.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        create.setItemType(EquestrianDict.ITEM_TYPE_OTHER);
        create.setTitle("下班前核对私教名单");
        create.setPriority(EquestrianDict.PRIORITY_URGENT);
        create.setOwnerCoachId(coachLinId);
        DutyShiftView added = dutyService.createItem(shift.id(), create);
        DutyItemView manual = added.items().stream()
                .filter(i -> "下班前核对私教名单".equals(i.title())).findFirst().orElseThrow();
        assertThat(manual.status()).isEqualTo(EquestrianDict.ITEM_DISPATCHED);
        assertThat(manual.ownerCoachId()).isEqualTo(coachLinId);

        // 普通教练出现在个人待办
        DutyBoardView linBoard = dutyService.board(coachLinId);
        assertThat(linBoard.myOpenItems()).extracting(DutyItemView::id).contains(manual.id());

        // 清除分派回到待处理（按事项自己的版本提交，不能拿班次 listSeq）
        DutyItemDispatchRequest clear = dispatchReq(MANAGER, null, null, -1L, manual.version());
        DutyItemView cleared = dutyService.dispatch(manual.id(), clear);
        assertThat(cleared.status()).isEqualTo(EquestrianDict.ITEM_PENDING);
        assertThat(cleared.ownerCoachId()).isNull();
    }

    // ------------------------------ 辅助方法 ------------------------------

    private List<DutyItemView> itemsOfType(DutyShiftView shift, String type) {
        return shift.items().stream().filter(i -> type.equals(i.itemType())).toList();
    }

    private Horse horse(String no, String name, String status) {
        Horse horse = new Horse();
        horse.setHorseNo(no);
        horse.setName(name);
        horse.setStatus(status);
        horse.setRideLevel("BEGINNER_SAFE");
        return horse;
    }

    private Coach coach(String no, String name) {
        Coach coach = new Coach();
        coach.setCoachNo(no);
        coach.setName(name);
        coach.setLevel("SENIOR");
        coach.setStatus(EquestrianDict.COACH_ON_DUTY);
        return coach;
    }

    private DutyShiftCreateRequest openReq(String name, Long coachId) {
        DutyShiftCreateRequest req = new DutyShiftCreateRequest();
        boolean manager = MANAGER.equals(name) || OTHER_MANAGER.equals(name);
        req.setOperatorName(name);
        req.setOperatorRole(manager ? EquestrianDict.ROLE_MANAGER : EquestrianDict.ROLE_STAFF);
        req.setCoachId(coachId);
        req.setPeriod(EquestrianDict.PERIOD_DAY);
        return req;
    }

    private DutyShiftUpdateRequest operator(String name, Long coachId) {
        DutyShiftUpdateRequest req = new DutyShiftUpdateRequest();
        req.setOperatorName(name);
        req.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        req.setCoachId(coachId);
        return req;
    }

    private DutyShiftUpdateRequest noteLikeUpdate(String name, String note, long version) {
        DutyShiftUpdateRequest req = new DutyShiftUpdateRequest();
        req.setOperatorName(name);
        req.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        req.setHandoverNote(note);
        req.setExpectedVersion(version);
        return req;
    }

    private DutyItemSupplementRequest noteReq(String name, Long coachId, String note) {
        DutyItemSupplementRequest req = new DutyItemSupplementRequest();
        boolean manager = MANAGER.equals(name) || OTHER_MANAGER.equals(name);
        req.setOperatorName(name);
        req.setOperatorRole(manager ? EquestrianDict.ROLE_MANAGER : EquestrianDict.ROLE_STAFF);
        req.setCoachId(coachId);
        req.setNote(note);
        return req;
    }

    private DutyItemDispatchRequest dispatchReq(String name, Long coachId, String priority,
            Long ownerCoachId, long version) {
        DutyItemDispatchRequest req = new DutyItemDispatchRequest();
        req.setOperatorName(name);
        req.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        req.setCoachId(coachId);
        req.setPriority(priority);
        req.setOwnerCoachId(ownerCoachId);
        req.setExpectedVersion(version);
        return req;
    }

    private DutyItemAcceptRequest acceptReq(String name, Long coachId, String note, long version) {
        DutyItemAcceptRequest req = new DutyItemAcceptRequest();
        boolean manager = MANAGER.equals(name) || OTHER_MANAGER.equals(name);
        req.setOperatorName(name);
        req.setOperatorRole(manager ? EquestrianDict.ROLE_MANAGER : EquestrianDict.ROLE_STAFF);
        req.setCoachId(coachId);
        req.setNote(note);
        req.setExpectedVersion(version);
        req.setRequestId("accept-" + name.hashCode() + "-" + System.nanoTime());
        return req;
    }

    private DutyShiftCloseRequest closeReq(String name, boolean confirm, String reason, long version) {
        return closeReq(name, confirm, reason, version, null);
    }

    private DutyShiftCloseRequest closeReq(String name, boolean confirm, String reason,
            long version, Map<Long, String> itemDelays) {
        DutyShiftCloseRequest req = new DutyShiftCloseRequest();
        req.setOperatorName(name);
        req.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        req.setConfirmDefer(confirm);
        req.setDelayReason(reason);
        req.setItemDelays(itemDelays);
        req.setExpectedVersion(version);
        return req;
    }
}
