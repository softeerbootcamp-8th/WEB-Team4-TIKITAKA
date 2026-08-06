package com.tikitaka.bidwinback.mypage.application;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionDepositRepository;
import com.tikitaka.bidwinback.global.common.PageResponse;
import com.tikitaka.bidwinback.mypage.domain.RecordPageRequest;
import com.tikitaka.bidwinback.mypage.domain.RecordSort;
import com.tikitaka.bidwinback.mypage.domain.StatusFilters;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MyDepositRecordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MyDepositRecordService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final AuctionDepositRepository auctionDepositRepository;

    @Transactional(readOnly = true)
    public PageResponse<MyDepositRecordResponse> getDeposits(
            long memberId,
            String statusFilter,
            int pageNumber,
            int size,
            String sort
    ) {
        List<DepositStatus> statuses = StatusFilters.resolve(DepositStatus.class, statusFilter);
        Pageable pageable = RecordPageRequest.of(pageNumber, size, "lastModifiedAt", RecordSort.from(sort));

        Page<AuctionDeposit> page = auctionDepositRepository.findByMemberIdAndStatusIn(
                memberId,
                statuses,
                pageable
        );

        List<MyDepositRecordResponse> items = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.from(page, items);
    }

    private MyDepositRecordResponse toResponse(AuctionDeposit deposit) {
        return new MyDepositRecordResponse(
                deposit.getId(),
                deposit.getAuction().getId(),
                deposit.getAuction().getTitle(),
                deposit.getReservedAmount(),
                deposit.getStatus(),
                deposit.getLastModifiedAt().atZone(SERVICE_ZONE).toInstant().toEpochMilli()
        );
    }
}
