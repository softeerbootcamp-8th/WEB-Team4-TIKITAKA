package com.tikitaka.bidwinback.global.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FilterEscapedExceptionControllerTest {

    private final Logger logger =
            (Logger) LoggerFactory.getLogger(FilterEscapedExceptionController.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final FilterEscapedExceptionController controller =
            new FilterEscapedExceptionController(mock(ErrorAttributes.class));

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void 오류_디스패치에서는_원래_요청_경로를_기록한다() {
        MockHttpServletRequest request = dataAccessExceptionRequest("/error");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/auth/session");

        controller.handleError(request);

        assertThat(loggedPath()).isEqualTo("/api/v1/auth/session");
    }

    @Test
    void 원래_요청_경로가_없으면_현재_경로를_기록한다() {
        MockHttpServletRequest request = dataAccessExceptionRequest("/api/v1/auth/session");

        controller.handleError(request);

        assertThat(loggedPath()).isEqualTo("/api/v1/auth/session");
    }

    private MockHttpServletRequest dataAccessExceptionRequest(String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        request.setAttribute(
                RequestDispatcher.ERROR_EXCEPTION,
                new DataAccessResourceFailureException("Redis 장애")
        );
        return request;
    }

    private Object loggedPath() {
        assertThat(appender.list).hasSize(1);
        return appender.list.getFirst().getKeyValuePairs().stream()
                .filter(keyValue -> keyValue.key.equals("path"))
                .map(keyValue -> keyValue.value)
                .findFirst()
                .orElseThrow();
    }
}
