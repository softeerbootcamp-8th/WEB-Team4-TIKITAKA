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
    // 이겼을 때 "이전 값"을 같이 반환해, 실패 시 되돌려야 하는 선점이었는지 판단하는 데 쓴다.
    // KEEPTTL을 안 붙이면 SET이 initialize()에서 걸어둔 만료시간을 지워버려, 경매가 끝나도
    // 키가 영구히 남는다.
    private static final RedisScript<Long> WIN_RACE_SCRIPT = new DefaultRedisScript<>(
            """
                local current = tonumber(redis.call('GET', KEYS[1]) or '0')
                if tonumber(ARGV[1]) > current then
                    redis.call('SET', KEYS[1], ARGV[1], 'KEEPTTL')
                    return current
                else
                    return -1
                end
            """,
            Long.class
    );

    // 내가 선점한 값이 그 사이 아무도 안 건드려서 아직 그대로일 때만, 그 순간 DB에 커밋된
    // 진짜 현재가로 다시 맞춘다. "내가 기억해둔 이전 값"으로 되돌리지 않는 이유: 그 이전 값도
    // 다른 요청이 Redis에서만 선점했다가 DB에서 실패한 값일 수 있어서, 체인으로 되돌리면
    // 실제 현재가보다 높은 가짜 값이 남을 수 있다(예: 판매자가 동시에 두 번 자기 경매에
    // 입찰하는 경우). DB 현재가로 재동기화하면 몇 번을 겹쳐도 항상 진짜 값으로 수렴한다.
    private static final RedisScript<Long> RESYNC_SCRIPT = new DefaultRedisScript<>(
            """
                local current = tonumber(redis.call('GET', KEYS[1]) or '0')
                if tonumber(ARGV[1]) == current then
                    redis.call('SET', KEYS[1], ARGV[2], 'KEEPTTL')
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

    /**
     * MySQL에서 최종적으로 실패했을 때(트랜잭션 롤백 포함), 앞서 선점했던 값을 그 시점 기준
     * DB 현재가(actualPrice)로 재동기화한다. myPrice는 "내가 선점한 값이 아직 그대로인지"
     * 확인하는 용도일 뿐, 되돌릴 값 자체로는 쓰지 않는다.
     */
    public void resyncToActualPrice(Long auctionId, long myPrice, long actualPrice) {
        try {
            redisTemplate.execute(
                    RESYNC_SCRIPT,
                    List.of(key(auctionId)),
                    String.valueOf(myPrice),
                    String.valueOf(actualPrice)
            );
        } catch (Exception exception) {
            // 재동기화 실패해도 무시 - 다음 정상 요청이 결국 올바른 값으로 다시 덮어씀
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
