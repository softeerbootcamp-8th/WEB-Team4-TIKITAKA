package com.tikitaka.bidwinback.global.health;

import com.tikitaka.bidwinback.global.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/v1/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("UP");
    }
}
