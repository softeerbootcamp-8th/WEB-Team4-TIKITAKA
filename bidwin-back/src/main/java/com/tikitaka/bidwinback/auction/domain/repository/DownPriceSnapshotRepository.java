package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.DownPriceSnapshot;
import com.tikitaka.bidwinback.auction.domain.entity.DownPriceSnapshotId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface DownPriceSnapshotRepository
        extends JpaRepository<DownPriceSnapshot, DownPriceSnapshotId> {

    @Modifying
    @Query(value = """
            INSERT INTO down_price_snapshot (snapshot_at, auction_id, price)
            SELECT :snapshotAt, a.id,
                   GREATEST(d.minimum_price,
                            a.start_price
                            - FLOOR(GREATEST(TIMESTAMPDIFF(MINUTE, a.started_at, :snapshotAt), 0)
                                    / GREATEST(d.price_drop_interval, 1)) * d.drop_price)
            FROM auction a
            JOIN down_auction d ON d.auction_id = a.id
            WHERE a.auction_type = 'DOWN'
              AND a.completed_at IS NULL
              AND a.started_at <= :snapshotAt
              AND a.ended_at > :snapshotAt
            """, nativeQuery = true)
    int capture(@Param("snapshotAt") LocalDateTime snapshotAt);

    @Modifying
    @Query(value = "DELETE FROM down_price_snapshot WHERE snapshot_at < :threshold",
            nativeQuery = true)
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}
