package com.tikitaka.bidwinback.upload.presentation;

import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.upload.application.AuctionImageDraftService;
import com.tikitaka.bidwinback.upload.application.AuctionImagePresignService;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImageDraftResponse;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignBatchRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/uploads/auction-images")
public class AuctionImageUploadController {

    private final AuctionImagePresignService presignService;
    private final AuctionImageDraftService draftService;

    @PostMapping("/drafts")
    public ResponseEntity<ApiResponse<AuctionImageDraftResponse>> issueDraft() {
        AuctionImageDraftResponse response = new AuctionImageDraftResponse(draftService.issue());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<List<AuctionImagePresignResponse>>> presign(
            @RequestAttribute(AuthConstant.REQUEST_ATTRIBUTE_KEY) AuthMember authMember,
            @Valid @RequestBody AuctionImagePresignBatchRequest request
    ) {
        List<AuctionImagePresignResponse> response = presignService.issue(
                authMember.memberId(),
                request.draftId(),
                request.images()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
