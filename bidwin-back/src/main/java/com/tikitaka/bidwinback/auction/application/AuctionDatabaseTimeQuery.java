package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AuctionDatabaseTimeQuery {

    private final AuctionRepository auctionRepository;

    @Transactional(readOnly = true)
    public LocalDateTime currentTime() {
        return auctionRepository.currentDatabaseTime();
    }
}
