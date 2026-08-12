# SSE Gatling 부하테스트

Spring 서버와 관측 스택을 먼저 실행한다. 느린 소비자가 있으면 `load-test` 프로필도 함께 켠다.

```bash
docker compose --profile load-test -f compose.local.yaml up -d --build
```

부하 형상과 입찰 계정을 환경변수로 지정한 뒤 저장소 루트에서 실행한다.

```bash
export SSE_MAX_SUBSCRIBERS=1000
export SSE_DURATION=PT10M
export SSE_RAMP_UP=PT1M
export SSE_RAMP_DOWN=PT1M
export SSE_SLOW_CONSUMER_RATIO=0.1
export BID_INTERVAL=PT1S
export AUCTION_IDS=1,2,3
export BID_EMAIL=load-test@example.com
export BID_PASSWORD='replace-me'

./bidwin-back/gradlew -p bidwin-load-test gatlingRun
```

- 시간은 `PT30S`, `PT5M` 같은 ISO-8601 형식이다. `SSE_DURATION`은 최대 동접 유지 시간이며 증가·감소 시간과 별개다.
- `SSE_RAMP_UP=PT0S`이면 모두 동시에 연결하고, `SSE_RAMP_DOWN=PT0S`이면 모두 동시에 종료한다.
- 느린 소비자 수는 `SSE_MAX_SUBSCRIBERS * SSE_SLOW_CONSUMER_RATIO`를 반올림한다. 프록시 속도는 `SSE_SLOW_BYTES_PER_SECOND`로 조정하며 기본값은 `128`이다.
- `AUCTION_IDS`가 하나면 상세 SSE 하나를, 여러 개면 서버의 다중 경매 SSE 하나를 구독한다. 입찰자는 같은 ID들을 순환한다.
- 입찰 계정은 대상 경매의 판매자가 아니어야 하고 충분한 예치금을 가져야 한다. 대상은 테스트 전체 동안 공개 입찰(`OPEN`)이 가능해야 한다.
- 선택 설정은 `BASE_URL`(기본 `http://localhost:8080`), `SLOW_BASE_URL`(기본 `http://localhost:8081`), `SSE_METRICS_PORT`(기본 `9101`), `SSE_EVENT_BUFFER_SIZE`(기본 `1000`)다.

Gatling이 실행되는 동안 `http://localhost:9101/metrics`를 Prometheus가 5초마다 수집한다. Grafana의 `서버 및 SSE 모니터링` 대시보드 하단에서 서버 발행/전송과 클라이언트 수신 수, 수신 지연, 중복·역순 횟수를 함께 확인할 수 있다.
