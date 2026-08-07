package com.tikitaka.bidwinback.mypage.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionDepositRepository;
import com.tikitaka.bidwinback.global.common.PageResponse;
import com.tikitaka.bidwinback.mypage.domain.exception.MyPageException;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MyDepositRecordResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyDepositRecordServiceTest {

    private static final long MEMBER_ID = 1L;
    private static final LocalDateTime CHANGED_AT = LocalDateTime.of(2026, 8, 1, 12, 0);

    @Mock
    private AuctionDepositRepository auctionDepositRepository;

    private MyDepositRecordService service;

    @BeforeEach
    void setUp() {
        service = new MyDepositRecordService(auctionDepositRepository);
    }

    @Test
    void 보증금_내역을_경매_제목과_함께_반환한다() {
        AuctionDeposit deposit = mock(AuctionDeposit.class);
        Auction auction = mock(Auction.class);
        when(auction.getId()).thenReturn(10L);
        when(auction.getTitle()).thenReturn("헤드폰");
        when(deposit.getId()).thenReturn(1L);
        when(deposit.getAuction()).thenReturn(auction);
        when(deposit.getReservedAmount()).thenReturn(30_000L);
        when(deposit.getStatus()).thenReturn(DepositStatus.HELD);
        when(deposit.getLastModifiedAt()).thenReturn(CHANGED_AT);

        Page<AuctionDeposit> page = new PageImpl<>(List.of(deposit), PageRequest.of(0, 10), 1);
        when(auctionDepositRepository.findByMemberIdAndStatusIn(eq(MEMBER_ID), any(), any()))
                .thenReturn(page);

        PageResponse<MyDepositRecordResponse> response =
                service.getDeposits(MEMBER_ID, "HELD", 1, 10, null);

        assertThat(response.items()).hasSize(1);
        MyDepositRecordResponse record = response.items().getFirst();
        assertThat(record.auctionId()).isEqualTo(10L);
        assertThat(record.auctionTitle()).isEqualTo("헤드폰");
        assertThat(record.amount()).isEqualTo(30_000L);
        assertThat(record.status()).isEqualTo(DepositStatus.HELD);
    }

    @Test
    void 지원하지_않는_상태_필터는_예외를_던진다() {
        assertThatThrownBy(() -> service.getDeposits(MEMBER_ID, "INVALID", 1, 10, null))
                .isInstanceOf(MyPageException.class);
    }
}
