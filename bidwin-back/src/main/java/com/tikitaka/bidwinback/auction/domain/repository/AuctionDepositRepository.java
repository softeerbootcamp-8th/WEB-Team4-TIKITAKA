package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionDepositRepository extends JpaRepository<AuctionDeposit, Long> {
}
