# 경매 마감 배치 부하·장애 주입 시나리오

이 테스트는 종료된 상향 경매를 대량 생성한 뒤 실제 MySQL에서 경매 마감 스케줄러를 호출한다.
정상 데이터와 잠금 포인트가 부족한 경매 한 건을 넣은 데이터를 같은 규모로 실행해 실패 전파 범위를 비교한다.
동시 사용자나 여러 스케줄러의 p95/p99를 측정하는 범용 부하테스트가 아니라, 단일 스케줄 실행의 처리량과 실패 격리를 재현하는 통합 시나리오다.

## 실행

Docker와 Java 21이 필요하다. 저장소 루트에서 다음 명령을 실행한다.

```bash
./bidwin-back/scripts/run-auction-closing-load-test.sh
```

스크립트는 다음 작업을 자동으로 처리한다.

1. 로컬 MySQL·Redis 컨테이너 실행
2. `bidwin_load_` 접두사의 격리 데이터베이스 생성
3. Flyway 마이그레이션 적용 및 전용 Gradle 테스트 실행
4. 결과 파일 생성
5. 테스트 데이터베이스 삭제

기본 설정은 경매 100개, 경매당 비낙찰자 20명, 마감 배치 크기 100이다.

```bash
AUCTION_LOAD_COUNT=500 \
AUCTION_LOAD_LOSERS_PER_AUCTION=50 \
AUCTION_CLOSING_BATCH_SIZE=100 \
./bidwin-back/scripts/run-auction-closing-load-test.sh
```

지원하는 환경변수는 다음과 같다.

| 환경변수 | 기본값 | 설명 |
|---|---:|---|
| `AUCTION_LOAD_COUNT` | `100` | 생성할 종료 경매 수 |
| `AUCTION_LOAD_LOSERS_PER_AUCTION` | `20` | 경매당 비낙찰자 수 |
| `AUCTION_CLOSING_BATCH_SIZE` | `100` | 한 트랜잭션이 선점하는 경매 수 |
| `AUCTION_LOAD_DEPOSIT_AMOUNT` | `30000` | 참여자별 보증금 (`2`~`2000000`) |
| `AUCTION_LOAD_POISON_AUCTION_ID` | `1` | 잠금 포인트 부족 오류를 주입할 경매 ID |
| `MYSQL_PORT` | `3307` | 로컬 MySQL 포트 |
| `MYSQL_ROOT_PASSWORD` | `root-local` | 로컬 MySQL root 비밀번호 |

보증금 fixture는 로컬 메모리 오용을 막기 위해 최대 200,000건으로 제한한다. 경매 수는 한 번의 스케줄 실행 한도인 `배치 크기 × 100` 이하여야 한다.

## 결과 해석

콘솔과 다음 파일에서 결과를 확인한다.

```text
bidwin-back/build/reports/auction-closing-load/result.json
```

주요 값은 다음과 같다.

- `baseline.completedAuctions`: 정상 데이터에서 완료된 경매 수
- `failureInjected.completedAuctions`: 오류 한 건이 섞였을 때 완료된 경매 수
- `failureInjected.pendingAuctions`: 오류 실행 후 아직 `BID_ONGOING`인 경매 수
- `healthyAuctionsBlocked`: 오류 경매 외에 함께 처리되지 못한 정상 경매 수
- `endToEndThroughputPerSecond`: 마감 트랜잭션과 커밋 후 SSE·Redis 발행까지 포함해 초당 완료한 경매 수

현재처럼 배치 전체가 하나의 트랜잭션이면 기본 설정에서 오류 한 건 때문에 정상 경매 99건도 함께 롤백될 수 있다.
경매별 실패 격리가 적용되면 같은 시나리오에서 오류 경매 한 건만 남고 정상 경매 99건은 완료되는 것이 목표다.

```text
현재 예상: completed=0, pending=100, healthyAuctionsBlocked=99
개선 목표: completed=99, pending=1, healthyAuctionsBlocked=0
```

실행 시간은 로컬 장비와 DB 캐시의 영향을 받으므로 한 번의 절대값보다 동일 환경에서 5회 이상 반복한 중앙값을 비교한다. 스크립트는 실행할 때마다 한 쌍의 시나리오만 수행하므로 5회 비교가 필요하면 명령을 직접 5회 실행한다. 실패 격리 개선 전후에는 정상 시나리오 처리량도 함께 비교해 트랜잭션 분리 비용을 확인한다.

`durationMillis`와 `endToEndThroughputPerSecond`에는 `AFTER_COMMIT` 이벤트가 수행하는 상태 재조회와 Redis 발행도 포함된다. 따라서 장애 시나리오의 시간이 정상 시나리오보다 짧다고 해서 DB 마감 자체가 더 빠르다는 의미는 아니다. 실패 전파 개선의 핵심 지표는 `completedAuctions`, `pendingAuctions`, `healthyAuctionsBlocked`다.

## 안전장치

전용 테스트는 `bidwin_load_` 접두사이며 경매·회원 fixture 테이블이 비어 있는 데이터베이스에서만 실행된다. 기본 `test`와 `check`에는 포함되지 않으며 `auctionClosingLoadTest` 태스크로만 실행된다.
