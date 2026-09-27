package com.equestrian.club.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.equestrian.club.dict.EquestrianDict;
import com.equestrian.club.domain.Coach;
import com.equestrian.club.domain.CoachRepository;
import com.equestrian.club.domain.Horse;
import com.equestrian.club.domain.HorseRepository;
import com.equestrian.club.domain.Lesson;
import com.equestrian.club.domain.LessonRepository;
import com.equestrian.club.domain.LessonSession;
import com.equestrian.club.domain.LessonSessionRepository;
import com.equestrian.club.domain.Stall;
import com.equestrian.club.domain.StallRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 值班交接链路 HTTP 端到端测试：真实起 Web 容器，验证路由、JSON、
 * 角色与归属在后端强制（普通教练绕过前端直连接口也被拒）、
 * 409 过期提交、承接幂等、课程接口带上值班信息。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DutyShiftControllerEndToEndTest {

    private static final String MANAGER = "值班负责人陈骁";
    private static final String STAFF = "普通教练林婉";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper mapper;
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
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from duty_acceptance");
        jdbc.update("delete from duty_item_note");
        jdbc.update("delete from duty_item");
        jdbc.update("delete from duty_shift");
        jdbc.update("delete from lesson_session");
        jdbc.update("delete from lesson");
        jdbc.update("delete from coach");
        jdbc.update("delete from stall");
        jdbc.update("delete from horse_health");
        jdbc.update("delete from horse");
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private ObjectNode body(String name, String role) {
        ObjectNode node = mapper.createObjectNode();
        node.put("operatorName", name);
        node.put("operatorRole", role);
        return node;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<JsonNode> resp) {
        return mapper.convertValue(resp.getBody().get("data"), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> shiftData) {
        return (List<Map<String, Object>>) shiftData.get("items");
    }

    @Test
    void fullHandoverChainOverHttp_permissionsVersioningIdempotencyAndSessionDuty() {
        seedBasics();

        // 普通教练不能开班（后端强制，不依赖前端隐藏按钮）
        ResponseEntity<JsonNode> staffOpen = rest.postForEntity(
                url("/api/duty/shifts"), new HttpEntity<>(body(STAFF, "STAFF")), JsonNode.class);
        assertThat(staffOpen.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(staffOpen.getBody().get("ok").asBoolean()).isFalse();
        assertThat(staffOpen.getBody().get("message").asText()).contains("值班负责人");

        // 负责人开班
        ObjectNode openBody = body(MANAGER, "MANAGER");
        openBody.put("period", "DAY");
        openBody.put("businessDate", LocalDate.now().toString());
        ResponseEntity<JsonNode> opened = rest.postForEntity(
                url("/api/duty/shifts"), new HttpEntity<>(openBody), JsonNode.class);
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> shift = data(opened);
        Long shiftId = ((Number) shift.get("id")).longValue();
        long version = ((Number) shift.get("version")).longValue();
        List<Map<String, Object>> shiftItems = items(shift);
        assertThat(shiftItems).isNotEmpty();

        // 首页看板取得到最新班次
        ResponseEntity<JsonNode> board = rest.getForEntity(
                url("/api/duty/board"), JsonNode.class);
        assertThat(board.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(board.getBody().get("data").get("current").get("id").asLong()).isEqualTo(shiftId);

        // 找一条课程事项验证课程列表上的值班信息
        Map<String, Object> lessonItem = shiftItems.stream()
                .filter(i -> "LESSON".equals(i.get("itemType"))).findFirst().orElseThrow();
        Long sessionId = ((Number) lessonItem.get("refId")).longValue();

        ResponseEntity<JsonNode> sessions = rest.getForEntity(url("/api/sessions"), JsonNode.class);
        assertThat(sessions.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode sessionArr = sessions.getBody().get("data");
        JsonNode tracked = null;
        for (JsonNode s : sessionArr) {
            if (s.get("id").asLong() == sessionId) {
                tracked = s;
                break;
            }
        }
        assertThat(tracked).isNotNull();
        assertThat(tracked.get("duty").get("tracked").asBoolean()).isTrue();
        assertThat(tracked.get("duty").get("items").get(0).get("originatingShiftNo").asText())
                .isEqualTo(shift.get("shiftNo").toString());

        Long itemId = ((Number) lessonItem.get("id")).longValue();
        long itemVersion = ((Number) lessonItem.get("version")).longValue();

        // 普通教练未分派不能补充（权限 + 归属后端兜底）
        ObjectNode noteBody = body(STAFF, "STAFF");
        noteBody.put("note", "我想补充");
        ResponseEntity<JsonNode> staffNote = rest.postForEntity(
                url("/api/duty/items/" + itemId + "/notes"), new HttpEntity<>(noteBody), JsonNode.class);
        assertThat(staffNote.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(staffNote.getBody().get("message").asText()).contains("分派给自己");

        // 过期版本提交结束班次 -> 409
        ObjectNode staleClose = body(MANAGER, "MANAGER");
        staleClose.put("confirmDefer", false);
        staleClose.put("expectedVersion", version - 1);
        ResponseEntity<JsonNode> conflict = rest.postForEntity(
                url("/api/duty/shifts/" + shiftId + "/close"), new HttpEntity<>(staleClose), JsonNode.class);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().get("message").asText()).contains("重新载入");

        // 未承接事项预检（当前版本）被逐项拦截 -> 400 且消息里带事项编号
        ObjectNode precheck = body(MANAGER, "MANAGER");
        ResponseEntity<JsonNode> blocked = rest.postForEntity(
                url("/api/duty/shifts/" + shiftId + "/precheck"), new HttpEntity<>(precheck), JsonNode.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(blocked.getBody().get("data").get("closingBlockers").size()).isGreaterThan(0);

        ObjectNode closeBody = body(MANAGER, "MANAGER");
        closeBody.put("confirmDefer", false);
        closeBody.put("expectedVersion", version);
        ResponseEntity<JsonNode> closeBlocked = rest.postForEntity(
                url("/api/duty/shifts/" + shiftId + "/close"), new HttpEntity<>(closeBody), JsonNode.class);
        assertThat(closeBlocked.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(closeBlocked.getBody().get("message").asText()).contains("未承接");

        // 非本班负责人不能关班
        ObjectNode otherManager = body("别人", "MANAGER");
        otherManager.put("confirmDefer", true);
        otherManager.put("delayReason", "延期");
        otherManager.put("expectedVersion", version);
        ResponseEntity<JsonNode> notOwner = rest.postForEntity(
                url("/api/duty/shifts/" + shiftId + "/close"), new HttpEntity<>(otherManager), JsonNode.class);
        assertThat(notOwner.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(notOwner.getBody().get("message").asText()).contains("本班负责人");

        // 承接：只有本班 MANAGER 可以
        ObjectNode acceptBody = body(MANAGER, "MANAGER");
        acceptBody.put("note", "课程改期已联系会员");
        acceptBody.put("expectedVersion", itemVersion);
        acceptBody.put("requestId", "e2e-accept-1");
        ResponseEntity<JsonNode> accepted = rest.postForEntity(
                url("/api/duty/items/" + itemId + "/accept"), new HttpEntity<>(acceptBody), JsonNode.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody().get("data").get("status").asText()).isEqualTo("ACCEPTED");

        // 同一 requestId 重复确认：不产生重复承接（仍 200，承接记录只有一条）
        ResponseEntity<JsonNode> dup = rest.postForEntity(
                url("/api/duty/items/" + itemId + "/accept"), new HttpEntity<>(acceptBody), JsonNode.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dup.getBody().get("data").get("status").asText()).isEqualTo("ACCEPTED");

        // 课程详情出现最近一次承接记录
        JsonNode trackedAfter = null;
        for (JsonNode s : rest.getForEntity(url("/api/sessions"), JsonNode.class).getBody().get("data")) {
            if (s.get("id").asLong() == sessionId) {
                trackedAfter = s;
            }
        }
        assertThat(trackedAfter.get("duty").get("latestAcceptance").get("acceptorName").asText())
                .isEqualTo(MANAGER);

        // 历史按负责人 + 事项状态查询
        String historyUrl = url("/api/duty/shifts?from=" + LocalDate.now().minusDays(1)
                + "&to=" + LocalDate.now().plusDays(1)
                + "&managerName=" + MANAGER + "&itemStatus=ACCEPTED");
        ResponseEntity<JsonNode> history = rest.getForEntity(historyUrl, JsonNode.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody().get("data").size()).isGreaterThanOrEqualTo(1);
    }

    private void seedBasics() {
        Horse horse = new Horse();
        horse.setHorseNo("H-E1");
        horse.setName("玉兔");
        horse.setStatus(EquestrianDict.HORSE_RESTING);
        horse.setRideLevel("BEGINNER_SAFE");
        horseRepository.save(horse);

        Stall stall = new Stall();
        stall.setStallNo("STM-E1");
        stall.setBarnName("B 区");
        stall.setStatus(EquestrianDict.STALL_MAINTENANCE);
        stallRepository.save(stall);

        Coach coach = new Coach();
        coach.setCoachNo("C-E1");
        coach.setName("林婉");
        coach.setLevel("JUNIOR");
        coach.setStatus(EquestrianDict.COACH_ON_DUTY);
        coachRepository.save(coach);

        Lesson lesson = new Lesson();
        lesson.setLessonNo("L-E1");
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
        session.setCoachId(coach.getId());
        session.setHorseId(horse.getId());
        session.setSessionDate(LocalDate.now().plusDays(1));
        session.setStartTime("09:00");
        session.setEndTime("10:00");
        session.setCapacity(6);
        session.setBookedCount(0);
        session.setStatus(EquestrianDict.SESSION_SCHEDULED);
        session.setHealthAffected(true);
        sessionRepository.save(session);
    }
}
