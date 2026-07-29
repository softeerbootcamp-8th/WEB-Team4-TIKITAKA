package com.tikitaka.bidwinback.upload.presentation;

import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.upload.application.AuctionImagePresignService;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/uploads/auction-images")
public class AuctionImageUploadController {

    private final AuctionImagePresignService presignService;

    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<List<AuctionImagePresignResponse>>> presign(
            @NotEmpty(message = "이미지는 한 장 이상이어야 합니다.")
            @RequestBody List<@Valid AuctionImagePresignRequest> requests
    ) {
        List<AuctionImagePresignResponse> response = presignService.issue(requests);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
