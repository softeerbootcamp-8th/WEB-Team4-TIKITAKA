package com.tikitaka.bidwinback.auction.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BidPriceCache {

    // 가격은 항상 양수이므로, "졌다"를 나타내는 신호로 절대 안 헷갈리는 -1을 쓴다.
    private static final long LOST = -1L;

    // 마감 시각에 딱 맞춰 만료시키면 그 순간 처리 중인 요청이 애매해지므로 여유를 둔다.
    private static final long EXPIRE_AFTER_ENDED_HOURS = 1L;

    private final StringRedisTemplate redisTemplate;

    // 비교와 갱신을 한 번에 원자적으로 처리해, 다음 요청이 곧바로 최신 값을 보게 한다.
    // 이겼을 때 "이전 값"을 같이 반환해, 나중에 되돌려야 할 때 그 값을 다시 쓸 수 있게 한다.
    private static final RedisScript<Long> WIN_RACE_SCRIPT = new DefaultRedisScript<>(
            """
                local current = tonumber(redis.call('GET', KEYS[1]) or '0')
                if tonumber(ARGV[1]) > current then
                    redis.call('SET', KEYS[1], ARGV[1])
                    return current
                else
                    return -1
                end
            """,
            Long.class
    );

    // 내가 올린 값이 그 사이 아무도 안 건드려서 아직 그대로일 때만 이전 값으로 되돌린다.
    // 이미 다른 요청이 더 높은 값으로 갱신했으면 그 값을 건드리지 않는다.
    private static final RedisScript<Long> REVERT_SCRIPT = new DefaultRedisScript<>(
            """
                local current = tonumber(redis.call('GET', KEYS[1]) or '0')
                if tonumber(ARGV[1]) == current then
                    redis.call('SET', KEYS[1], ARGV[2])
                    return 1
                else
                    return 0
                end
            """,
            Long.class
    );

    /**
     * 캐싱된 가격보다 높은지 즉시 원자적으로 판정하고, 이기면 캐시를 바로 갱신한다.
     * 반환값: 이겼으면 "이전 값"(되돌릴 때 필요), 졌으면 -1, Redis 장애 시 null(모르니 MySQL이 판단).
     */
    public Long tryWinRace(Long auctionId, long price) {
        try {
            return redisTemplate.execute(
                    WIN_RACE_SCRIPT,
                    List.of(key(auctionId)),
                    String.valueOf(price)
            );
        } catch (Exception exception) {
            return null;
        }
    }

    public boolean isLost(Long previousPrice) {
        return previousPrice != null && previousPrice == LOST;
    }

    /** MySQL에서 최종적으로 실패했을 때, 앞서 이겼다고 판정했던 캐시 값을 되돌린다. */
    public void revertIfStillMine(Long auctionId, long myPrice, long previousPrice) {
        try {
            redisTemplate.execute(
                    REVERT_SCRIPT,
                    List.of(key(auctionId)),
                    String.valueOf(myPrice),
                    String.valueOf(previousPrice)
            );
        } catch (Exception exception) {
            // 되돌리기 실패해도 무시 - 다음 정상 요청이 결국 올바른 값으로 다시 덮어씀
        }
    }

    /**
     * 경매 생성 시 시작가로 캐시를 미리 채워둔다. 이게 없으면 첫 요청은 키가 없어 0으로
     * 취급되어, 시작가보다 낮은 가격도 Redis 관문을 통과해버린다(MySQL이 최종 거절하지만
     * 그만큼 왕복이 낭비된다). TTL은 마감 시각 기준으로 둬서 별도 정리 작업 없이 자동 만료된다.
     */
    public void initialize(Long auctionId, long startPrice, LocalDateTime endedAt) {
        try {
            Duration ttl = Duration.between(LocalDateTime.now(), endedAt)
                    .plusHours(EXPIRE_AFTER_ENDED_HOURS);
            if (ttl.isNegative() || ttl.isZero()) {
                return;
            }
            redisTemplate.opsForValue().set(key(auctionId), String.valueOf(startPrice), ttl);
        } catch (Exception exception) {
            // 초기화 실패해도 무시 - 키가 없으면 tryWinRace가 0으로 취급해 안전망(MySQL)이 대신 판단한다.
        }
    }

    private static String key(Long auctionId) {
        return "auction:" + auctionId + ":price";
    }
}
