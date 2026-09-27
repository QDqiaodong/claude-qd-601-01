package com.equestrian.club.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.equestrian.club.common.ApiResponse;
import com.equestrian.club.dto.DutyItemAcceptRequest;
import com.equestrian.club.dto.DutyItemCreateRequest;
import com.equestrian.club.dto.DutyItemDispatchRequest;
import com.equestrian.club.dto.DutyItemSupplementRequest;
import com.equestrian.club.dto.DutyShiftCloseRequest;
import com.equestrian.club.dto.DutyShiftCreateRequest;
import com.equestrian.club.dto.DutyShiftUpdateRequest;
import com.equestrian.club.dto.OperatorRequest;
import com.equestrian.club.dto.view.DutyBoardView;
import com.equestrian.club.dto.view.DutyItemView;
import com.equestrian.club.dto.view.DutyShiftView;
import com.equestrian.club.service.DutyShiftService;

/**
 * 值班交接台：
 * 首页看板 / 历史查询 / 班次详情 / 开班 / 交接说明 / 手工事项 / 清单同步 /
 * 事项补充（普通教练限本人事项）/ 优先级与分派（负责人）/ 逐项承接（下一班负责人）/
 * 结束班次（负责人，未承接逐项拦截或延期）。
 *
 * <p>所有角色、归属、状态流转、乐观版本与幂等校验都在后端 service 完成，
 * 前端隐藏按钮只做体验。
 */
@RestController
@RequestMapping("/api/duty")
public class DutyShiftController {

    private final DutyShiftService dutyShiftService;

    public DutyShiftController(DutyShiftService dutyShiftService) {
        this.dutyShiftService = dutyShiftService;
    }

    /** 值班首页：最新班次清单 + 当前教练个人未结事项 */
    @GetMapping("/board")
    public ApiResponse<DutyBoardView> board(@RequestParam(required = false) Long coachId) {
        return ApiResponse.success(dutyShiftService.board(coachId));
    }

    /** 历史班次：按日期区间 / 负责人 / 事项状态查询 */
    @GetMapping("/shifts")
    public ApiResponse<List<DutyShiftView>> history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String managerName,
            @RequestParam(required = false) String itemStatus) {
        return ApiResponse.success(dutyShiftService.history(from, to, managerName, itemStatus));
    }

    @GetMapping("/shifts/{id}")
    public ApiResponse<DutyShiftView> detail(@PathVariable Long id) {
        return ApiResponse.success(dutyShiftService.detail(id));
    }

    /** 结束班次预检：返回带 closingBlockers 逐条提示的班次视图 */
    @PostMapping("/shifts/{id}/precheck")
    public ApiResponse<DutyShiftView> precheck(@PathVariable Long id, @RequestBody OperatorRequest request) {
        return ApiResponse.success(dutyShiftService.precheck(id, request));
    }

    /** 建立值班班次（MANAGER），自动从上一班滚动未结事项并同步当班清单 */
    @PostMapping("/shifts")
    public ApiResponse<DutyShiftView> openShift(@RequestBody DutyShiftCreateRequest request) {
        return ApiResponse.success("值班班次已建立：上一班未承接事项已滚动到本班，当班课程、马匹、栏位清单已生成",
                dutyShiftService.openShift(request));
    }

    /** 负责人更新交接总说明（乐观版本校验） */
    @PostMapping("/shifts/{id}/handover-note")
    public ApiResponse<DutyShiftView> updateHandoverNote(@PathVariable Long id,
            @RequestBody DutyShiftUpdateRequest request) {
        return ApiResponse.success("交接说明已更新", dutyShiftService.updateHandoverNote(id, request));
    }

    /** 结束班次：未承接逐项拦截；记录延期原因并确认后完成交接 */
    @PostMapping("/shifts/{id}/close")
    public ApiResponse<DutyShiftView> close(@PathVariable Long id, @RequestBody DutyShiftCloseRequest request) {
        DutyShiftView view = dutyShiftService.close(id, request);
        String message = view.openCount() > 0
                ? "班次已结束，" + view.openCount() + " 个未承接事项已延期并滚动到下一班，不会消失"
                : "班次已结束，全部事项均已承接";
        return ApiResponse.success(message, view);
    }

    /** 立即同步当班课程 / 马匹 / 栏位变更到最新班次清单 */
    @PostMapping("/shifts/{id}/sync")
    public ApiResponse<DutyShiftView> sync(@PathVariable Long id, @RequestBody OperatorRequest request) {
        return ApiResponse.success("已同步当班课程、马匹状态与栏位安排，新出现的变更已进入本班清单",
                dutyShiftService.sync(id, request));
    }

    /** 负责人手工补充事项 */
    @PostMapping("/shifts/{id}/items")
    public ApiResponse<DutyShiftView> createItem(@PathVariable Long id,
            @RequestBody DutyItemCreateRequest request) {
        return ApiResponse.success("交接事项已补充到本班清单", dutyShiftService.createItem(id, request));
    }

    /** 事项补充：普通教练仅能补充分派给自己的事项，负责人可补充本班全部 */
    @PostMapping("/items/{id}/notes")
    public ApiResponse<DutyItemView> supplement(@PathVariable Long id,
            @RequestBody DutyItemSupplementRequest request) {
        return ApiResponse.success("补充说明已追加（历史未被覆盖）", dutyShiftService.supplement(id, request));
    }

    /** 负责人标记优先级 / 分派负责教练 */
    @PostMapping("/items/{id}/dispatch")
    public ApiResponse<DutyItemView> dispatch(@PathVariable Long id, @RequestBody DutyItemDispatchRequest request) {
        return ApiResponse.success("事项已更新，等待下一班负责人逐项确认承接",
                dutyShiftService.dispatch(id, request));
    }

    /** 下一班负责人逐项确认承接（幂等：重复确认不产生重复记录） */
    @PostMapping("/items/{id}/accept")
    public ApiResponse<DutyItemView> accept(@PathVariable Long id, @RequestBody DutyItemAcceptRequest request) {
        return ApiResponse.success("事项已承接", dutyShiftService.accept(id, request));
    }
}
