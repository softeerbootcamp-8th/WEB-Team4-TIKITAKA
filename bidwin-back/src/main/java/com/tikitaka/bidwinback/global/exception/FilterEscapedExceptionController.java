package com.tikitaka.bidwinback.global.exception;

import com.tikitaka.bidwinback.global.common.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SessionRepositoryFilter는 Ordered.HIGHEST_PRECEDENCE로 등록되어 어떤 필터도 그보다
 * 바깥에 설 수 없다. 그 필터 자신의 세션 커밋(finally 블록)에서 발생하는 예외는
 * AuthExceptionFilter를 포함한 모든 필터를 빠져나가 컨테이너의 에러 디스패치(/error)로만
 * 온다. 이 컨트롤러가 그 마지막 지점에서 Redis 장애를 우리 응답 규약으로 변환한다.
 */
@RestController
public class FilterEscapedExceptionController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiResponse<Void>> handleError(HttpServletRequest request) {
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        if (exception instanceof DataAccessException) {
            return ResponseEntity.status(ErrorCode.AUTHENTICATION_UNAVAILABLE.getStatus())
                    .body(ApiResponse.error(ErrorCode.AUTHENTICATION_UNAVAILABLE));
        }

        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
