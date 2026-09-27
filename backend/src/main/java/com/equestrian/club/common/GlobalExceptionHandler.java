package com.equestrian.club.common;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务校验失败：400 + 中文业务提示
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(e.getMessage()));
    }

    /**
     * 并发冲突：409 + 具体的变化提示。前端必须让用户重新载入最新事件链，不能重试旧提交。
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(e.getMessage()));
    }

    /**
     * JPA 乐观锁兜底（@Version 版本号落后）：两笔提交基于同一旧版本时后提交的一方在此失败，
     * 整个事务回滚，不会留下「状态改了、承接记录没写」的半条记录。统一转 409 要求重新载入。
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimistic(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(
                "数据已被其他人更新（班次建立、事项补充或承接确认刚发生），请重新载入最新交接清单后再提交，本次旧内容未保存"));
    }

    /**
     * 悲观锁等待超时：交接写事务都串行在班次锁上，正常不会触发；触发时同样要求重新载入，
     * 由前端整单重试而不是让用户看到 500。
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handlePessimistic(PessimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(
                "交接数据正被其他班次操作占用，请稍后重新载入最新清单再提交"));
    }

    /**
     * 参数注解校验失败：400 + 字段名与提示
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(message.isEmpty() ? "请求参数校验失败" : message));
    }

    /**
     * 请求体格式错误：400
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail("请求体格式有误，请检查 JSON 字段类型"));
    }

    /**
     * 唯一约束冲突：400（避免在页面上抛出非人话的外键报错）
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail("存在唯一性约束冲突，请检查编号、手机号是否重复"));
    }

    /**
     * 兜底：500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("服务异常：" + detail));
    }
}
