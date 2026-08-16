package com.tikitaka.bidwinback.global.health;

import com.tikitaka.bidwinback.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "상태", description = "서비스 가용성 확인")
public class HealthController {

    @Operation(summary = "서비스 상태 확인", description = "애플리케이션이 요청을 처리할 수 있으면 `UP`을 반환합니다.")
    @GetMapping("/api/v1/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("UP");
    }
}
