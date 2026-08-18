package com.tikitaka.bidwinback.auction.application.bid;

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

    // Lua의 숫자는 전부 배정밀도 실수(double)라 2^53(약 900조)을 넘는 정수는 반올림된다.
    // 우리 가격은 Long 전 범위(약 900경까지)를 오버플로 없이 다뤄야 해서, tonumber로 비교하면
    // 그 근처의 서로 다른 두 가격이 같은 값으로 뭉개져 정상적인 상향 입찰이 거절될 수 있다.
    // 대신 가격이 항상 "양수, 앞자리 0 없는 순수 숫자 문자열"이라는 걸 우리가 보장하므로,
    // 길이가 길면 더 큰 수이고 길이가 같으면 사전식 비교가 곧 숫자 비교와 같다(문자 '0'~'9'가
    // 바이트 값 순서 그대로 커지므로) - 정밀도 손실 없이 문자열만으로 비교한다.
    //
    // 이겼을 때는 "이전 값" 대신 고정값 1을 반환한다(자바 쪽은 -1인지 아닌지만 구분하면 되고,
    // 이전 값 자체는 안 쓴다) - 반환값에서도 큰 수를 다시 숫자로 바꿀 필요가 없어진다.
    // 키가 아예 없으면(initialize 실패, Redis flush/재시작, 이 기능 도입 전 경매 등) nil을
    // 돌려줘 자바 쪽에서 null(모름)로 처리하게 한다 - 없던 키를 0 기준으로 새로 만들면
    // KEEPTTL이 지킬 기존 만료시간이 없어 영구 키가 생긴다.
    private static final RedisScript<Long> WIN_RACE_SCRIPT = new DefaultRedisScript<>(
            """
                if redis.call('EXISTS', KEYS[1]) == 0 then
                    return nil
                end
                local bid = ARGV[1]
                local current = redis.call('GET', KEYS[1])
                if (#bid > #current) or (#bid == #current and bid > current) then
                    redis.call('SET', KEYS[1], bid, 'KEEPTTL')
                    return 1
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
    // 키가 이미 없다면(만료됐거나 애초에 없었으면) 되돌릴 대상이 없으니 그대로 둔다.
    // 동등 비교는 문자열이 정확히 같은지만 보면 되므로 숫자 변환이 필요 없어 정밀도 문제도 없다.
    private static final RedisScript<Long> RESYNC_SCRIPT = new DefaultRedisScript<>(
            """
                if redis.call('EXISTS', KEYS[1]) == 0 then
                    return 0
                end
                local current = redis.call('GET', KEYS[1])
                if ARGV[1] == current then
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
     * 반환값: 이겼으면 1(고정값), 졌으면 -1, Redis 장애 시 null(모르니 MySQL이 판단).
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
