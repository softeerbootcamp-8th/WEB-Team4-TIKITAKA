# 백엔드 운영 로그 정책

## 출력과 검색 필드

- 애플리케이션 로그는 stdout에 Logstash JSON 한 줄로 출력한다. 파일 저장과 로테이션은 실행 환경이 담당한다.
- 운영 이벤트에는 검색 기준인 `event`를 `snake_case`로 기록한다.
- 관련 식별자는 `auctionId`, `bidId`, `tradeId`, `memberId`, `objectKey`처럼 `lowerCamelCase` 필드로 기록한다.
- 로그 메시지는 상황을 설명하고, 검색·집계할 값은 메시지 문자열이 아니라 구조 필드에 둔다.

```java
log.atWarn()
        .addKeyValue("event", "auction_state_publish_failed")
        .addKeyValue("auctionId", auctionId)
        .setCause(exception)
        .log("커밋된 경매 상태를 Redis로 발행하지 못했습니다.");
```

## 레벨

| 레벨 | 기준 |
| --- | --- |
| `ERROR` | 현재 요청이나 비동기 작업이 실패했고 내부 복구 경로가 없는 경우 |
| `WARN` | fallback, 다음 스케줄, 재연결 등으로 복구 가능하지만 운영 확인이 필요한 경우 |
| `INFO` | 배치 처리 건수처럼 운영 상태 확인에 필요한 정상 이벤트 |
| `DEBUG` | 개발 및 한시적 장애 분석용 상세 정보. 운영 기본 출력 대상이 아님 |

운영 기본값은 root와 애플리케이션 모두 `INFO`다. 장애 분석 시 `LOG_LEVEL_APP=DEBUG`만 한시적으로 적용하고, 분석 후 `INFO`로 복구한다. 프레임워크 및 SQL 바인딩 로그는 애플리케이션 레벨과 별도로 제한한다.

## 예외 기록 위치

- 예상 가능한 `BusinessException`과 입력 검증 실패는 응답 변환 경계에서 기록하지 않는다.
- 같은 예외를 하위 계층과 상위 예외 처리기에서 반복 기록하지 않는다.
- 복구를 결정하거나 최종 실패 응답을 만드는 한 지점에서만 기록한다.
- stack trace가 필요한 경우에도 메서드 인자, 요청 객체 또는 요청 본문을 함께 기록하지 않는다.

## 민감정보

다음 값은 어떤 레벨에서도 기록하지 않는다.

- 비밀번호와 비밀번호 해시
- 이메일 인증·비밀번호 재설정 토큰 및 토큰 해시
- 세션 ID, 쿠키, `Authorization` 헤더
- 요청·응답 본문 전체, HTTP 헤더 전체, query string 전체
- 이메일 주소, 전화번호, presigned URL

민감한 요청을 식별해야 하면 `memberId` 같은 내부 식별자만 사용한다. 저장소 정리 실패의 `objectKey`는 객체 식별에 필요한 범위에서만 기록하고 URL이나 서명 query는 기록하지 않는다.
