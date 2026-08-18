package com.tikitaka.bidwinback.global.exception;

import com.tikitaka.bidwinback.global.common.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SessionRepositoryFilter는 Ordered.HIGHEST_PRECEDENCE로 등록되어 어떤 필터도 그보다
 * 바깥에 설 수 없다. 그 필터 자신의 세션 커밋(finally 블록)에서 발생하는 예외는
 * AuthExceptionFilter를 포함한 모든 필터를 빠져나가 컨테이너의 에러 디스패치(/error)로만
 * 온다. 이 컨트롤러가 그 마지막 지점에서 Redis 장애만 우리 응답 규약으로 변환한다.
 * 이 컨트롤러를 등록하면 스프링 부트가 기본으로 만들어주는 BasicErrorController 빈은
 * @ConditionalOnMissingBean(ErrorController.class)에 의해 아예 생성되지 않으므로,
 * DataAccessException이 아닌 나머지(400, 403 등 임의의 상태 코드 포함)는 직접 만든
 * BasicErrorController 인스턴스에 위임해 원래 상태 코드와 본문을 그대로 보존한다.
 * ErrorProperties는 이 프로젝트에 server.error.* 설정이 없어 빈으로 등록되지 않으므로
 * 기본값으로 직접 생성한다. 나중에 server.error.* 설정을 추가하면 이 값도 함께 반영해야 한다.
 */
@RestController
@Slf4j
public class FilterEscapedExceptionController implements ErrorController {

    private final BasicErrorController defaultErrorController;

    public FilterEscapedExceptionController(ErrorAttributes errorAttributes) {
        this.defaultErrorController = new BasicErrorController(errorAttributes, new ErrorProperties());
    }

    @RequestMapping("/error")
    public ResponseEntity<?> handleError(HttpServletRequest request) {
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        if (exception instanceof DataAccessException dataAccessException) {
            log.atError()
                    .addKeyValue("event", "authentication_session_commit_failed")
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("path", request.getRequestURI())
                    .addKeyValue("failureType", dataAccessException.getClass().getSimpleName())
                    .log("인증 세션 변경 사항을 저장하지 못했습니다.");
            return ResponseEntity.status(ErrorCode.AUTHENTICATION_UNAVAILABLE.getStatus())
                    .body(ApiResponse.error(ErrorCode.AUTHENTICATION_UNAVAILABLE));
        }

        return defaultErrorController.error(request);
    }
}
