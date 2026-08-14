package com.tikitaka.bidwinback.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.FlushMode;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisSessionMapper;
import org.springframework.session.data.redis.RedisSessionRepository;

@Configuration
public class SessionConfig {

    /**
     * 기본값(ON_SAVE)은 setAttribute() 시점에 Redis에 쓰지 않고 응답 커밋 시점까지 미룬다.
     * 그러면 로그인/비밀번호변경 컨트롤러의 try-catch가 실행을 마친 뒤에야 실제 쓰기가
     * 일어나서, Redis 장애를 못 잡고 이미 성공 응답을 내려준 뒤가 되어버린다.
     * setAttribute() 호출 즉시 반영시켜, 실패가 그 try-catch 안에서 확실히 드러나게 한다.
     * spring.session.store-type=redis(repository-type 기본값 "default")는 Indexed가
     * 아닌 RedisSessionRepository를 등록하므로 이 타입을 커스터마이즈해야 실제로 적용된다.
     */
    @Bean
    public SessionRepositoryCustomizer<RedisSessionRepository> sessionFlushModeCustomizer() {
        return repository -> repository.setFlushMode(FlushMode.IMMEDIATE);
    }

    /**
     * 만료/삭제와 HSET 저장이 겹치면 Redis 세션 해시가 creationTime 같은 필수 필드 없이
     * 부분적으로만 남을 수 있다. 기본 RedisSessionMapper는 이 경우 IllegalStateException을
     * 던지는데(공식 문서에 명시), findById()는 매퍼가 null을 반환하면 그 키를 스스로 지우고
     * "세션 없음"으로 처리해준다. 그래서 예외를 잡아 null로 바꿔주기만 하면, 손상된 세션이
     * 예외로 새어나가는 대신 자동으로 정리된다.
     */
    @Bean
    public SessionRepositoryCustomizer<RedisSessionRepository> sessionMapperCustomizer() {
        RedisSessionMapper defaultMapper = new RedisSessionMapper();
        return repository -> repository.setRedisSessionMapper((sessionId, entries) -> {
            try {
                return defaultMapper.apply(sessionId, entries);
            } catch (IllegalStateException exception) {
                return null;
            }
        });
    }
}
