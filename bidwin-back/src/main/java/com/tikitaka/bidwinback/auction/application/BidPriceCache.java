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

    // 마감 시각에 딱 맞춰 만료시키면 그 순간 처리 중인 요청이 애매해지므로 여유를 둔다.
    private static final long EXPIRE_AFTER_ENDED_HOURS = 1L;

    private final StringRedisTemplate redisTemplate;

    // 커밋 이벤트의 실행 순서가 뒤바뀌어도 캐시 가격이 후퇴하지 않게 최댓값만 저장한다.
    // Lua number는 BIGINT 전체를 정확히 표현하지 못하므로 양의 정수 문자열의 길이와 사전순으로 비교한다.
    private static final RedisScript<Long> UPDATE_COMMITTED_PRICE_SCRIPT = new DefaultRedisScript<>(
            """
                local current = redis.call('GET', KEYS[1])
                local incoming = ARGV[1]
                if not current or #incoming > #current or (#incoming == #current and incoming > current) then
                    redis.call('SET', KEYS[1], incoming, 'PX', ARGV[2])
                    return 1
                end

                redis.call('PEXPIRE', KEYS[1], ARGV[2])
                return 0
            """,
            Long.class
    );

    /**
     * 커밋된 캐시 가격 이하인 명백한 저가 입찰만 거절한다.
     * 캐시 미존재·오염·Redis 장애 시에는 거절하지 않고 MySQL이 최종 판정한다.
     */
    public boolean isTooLow(Long auctionId, long price) {
        try {
            String committedPrice = redisTemplate.opsForValue().get(key(auctionId));
            return committedPrice != null && price <= Long.parseLong(committedPrice);
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * MySQL에 커밋된 가격만 단조 증가로 반영한다. 캐시는 DB보다 같거나 느리게 갱신되므로,
     * 갱신 실패나 이벤트 역전이 발생해도 정상 입찰을 잘못 거절하지 않는다.
     */
    public void updateCommittedPrice(Long auctionId, long price, LocalDateTime endedAt) {
        try {
            Duration ttl = Duration.between(LocalDateTime.now(), endedAt)
                    .plusHours(EXPIRE_AFTER_ENDED_HOURS);
            if (ttl.isNegative() || ttl.isZero()) {
                return;
            }
            redisTemplate.execute(
                    UPDATE_COMMITTED_PRICE_SCRIPT,
                    List.of(key(auctionId)),
                    String.valueOf(price),
                    String.valueOf(ttl.toMillis())
            );
        } catch (Exception exception) {
            // 캐시가 뒤처지거나 없어도 안전망인 MySQL이 최종 판정한다.
        }
    }

    private static String key(Long auctionId) {
        return "auction:" + auctionId + ":committed-price";
    }
}
