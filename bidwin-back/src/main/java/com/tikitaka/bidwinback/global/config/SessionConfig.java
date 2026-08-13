package com.tikitaka.bidwinback.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.FlushMode;
import org.springframework.session.config.SessionRepositoryCustomizer;
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
}
