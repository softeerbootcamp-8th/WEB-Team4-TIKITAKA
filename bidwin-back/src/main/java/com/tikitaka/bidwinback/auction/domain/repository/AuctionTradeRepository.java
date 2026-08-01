package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionTradeRepository extends JpaRepository<AuctionTrade, Long> {
}
