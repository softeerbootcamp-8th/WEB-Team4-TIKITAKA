package com.tikitaka.bidwinback.auction.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class DownPriceSnapshotId implements Serializable {

    private LocalDateTime snapshotAt;
    private Long auctionId;
}
