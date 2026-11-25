package kr.ac.cbnu.cafeteria.web;

import kr.ac.cbnu.cafeteria.domain.BusinessException;
import kr.ac.cbnu.cafeteria.web.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Controller 공통 처리(설계서 4.0절): 업무 예외를 HTTP 상태 코드와 ApiError(code, message)로 바꾼다.
 * 200 성공, 400 입력 오류, 401 미로그인, 403 권한 없음, 404 대상 없음, 409 상태 충돌(EIR-NW-03).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException e) {
        return ResponseEntity.status(e.getStatus()).body(new ApiError(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> badInput(Exception e) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_INPUT", "입력 형식이 올바르지 않습니다"));
    }

    /** 잠금 대기 시간 초과(설계서 7장 부하·스트레스 설계) */
    @ExceptionHandler({PessimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiError> busy(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("BUSY", "잠시 후 다시 시도하세요"));
    }

    /** UNIQUE 제약 위반: 같은 예약에 두 번째 결제·식권이 생기려 한 경우(NFR-05) */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> duplicate(DataIntegrityViolationException e) {
        log.warn("데이터 무결성 위반: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("DUPLICATE", "이미 처리된 요청입니다"));
    }
}
