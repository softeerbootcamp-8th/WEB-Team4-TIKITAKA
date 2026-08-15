# 하향 경매 가격 스냅샷 성능 검증 보고서

## 1. 결론

현재 로컬 Docker 구성에서 하향 경매 가격순 조회의 검증된 지속 처리량은 **1,500 RPS**다.

- 캐시 적용 전에는 400 RPS가 안정 구간이었고 500 RPS부터 대기열이 지속적으로 증가했다.
- 요청마다 수행하던 세대별 정확한 `COUNT`를 Redis에 세대별로 캐시했다.
- 캐시 적용 후 1,000 RPS를 3분간 유지하면서 예정된 요청 180,000건을 모두 처리했다.
- Redis count 키가 없는 cold 상태로 시작했고, 15만 행 규모의 스냅샷 적재가 세 번 겹쳤지만 오류와 드롭은 없었다.
- 평균 3.34ms, p95 3.30ms, p99 45.49ms, 최대 315.02ms로 모든 품질 기준을 통과했다.
- 1,250 RPS도 3분간 225,000건을 모두 처리했고 p95 3.88ms, p99 105.84ms로 통과했다.
- capture 직후 count pre-warm과 세대별 single-flight를 적용해 cold-cache stampede를 차단했다.
- 동일한 재기동·예열 절차로 read-only와 일반 트랜잭션을 직접 A/B한 결과 둘 다 1,500 RPS를 통과했고, p99는 각각 189.42ms와 192.07ms로 실질적인 성능 차이가 없었다.
- read-only는 요청마다 `SET SESSION TRANSACTION READ ONLY`와 복구용 `READ WRITE`를 추가했지만, 일반 트랜잭션에서는 두 제어문이 발생하지 않았다.
- 서버 재기동 직후 곧바로 시작한 cold 1,500 RPS는 p99 579.05ms와 238건 드롭으로 실패해 트래픽 투입 전 warm-up이 필요하다.
- 1,750 RPS는 드롭·오류 없이 315,001건을 처리했지만 p99 201.97ms로 지연 기준을 근소하게 초과했다.

`DOWN + 가격순 + 스냅샷 존재` 경로의 가격 계산과 정렬은 스냅샷·커버링 인덱스로 줄였고, 남아 있던 요청별 `COUNT`도 캐시 hit 경로에서 제거했다. ALL/UP 가격순과 스냅샷 부재 폴백은 기존 경로를 유지한다.

## 2. 검증 대상

### 요청

```http
GET /api/v1/auctions?auctionType=DOWN&sort=priceLow&page=1&size=16
```

- 인증하지 않은 공개 목록 요청
- 응답 본문은 k6에서 폐기
- 동일한 하향 경매 가격 오름차순 첫 페이지를 반복 조회
- `asOf`를 전달하지 않아 서버가 최신 스냅샷 세대를 해결하는 실제 첫 요청 경로 사용

### 데이터

- 활성 하향 경매: 세대당 150,000건
- 스냅샷 보존: 약 10세대
- 스냅샷 테이블: 약 1,500,000행
- 측정 당시 테이블 할당량: 데이터 약 63.16MiB, 인덱스 약 76.25MiB
- 캐시 적용 후 500 RPS 측정 직전 데이터: 19세대, 총 2,850,000행

### 실행 환경

| 구성 | 값 |
|---|---:|
| Spring 애플리케이션 컨테이너 메모리 | 1GiB |
| Hikari 최대 커넥션 | 5 |
| Tomcat 최대 스레드 | 100 |
| MySQL | 8.4.10 |
| MySQL 최대 커넥션 | 100 |
| InnoDB 버퍼풀 | 256MiB |
| Redis 최대 메모리 | 64MiB |
| 부하 도구 | k6 `constant-arrival-rate` |
| k6 VU | 사전 500~700, 최대 2,500 |
| 요청 타임아웃 | 10초 |
| 측정 일시 | 2026-08-14~15 KST |

Spring, MySQL, Redis, k6가 같은 개발 장비의 로컬 Docker 환경을 공유했다. 결과는 현재 구성의 상대적 병목과 처리 경계를 보여주지만, 운영 환경의 절대 성능을 보장하지는 않는다.

## 3. 부하 테스트 결과

| 구성 | 목표 RPS | 지속 시간 | 완료 요청 | 드롭 | HTTP 오류 | 실제 RPS | 평균 | p95 | p99 | 최대 | 판정 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 캐시 전 | 400 | 60초 | 24,001 | 0 | 0 | 399.13 | 10.84ms | 13.13ms | 17.91ms | 179.49ms | 통과 |
| 캐시 전 | 400 | 3분 | 72,001 | 0 | 0 | 399.99 | 11.96ms | 11.31ms | 127.59ms | 388.33ms | 통과 |
| 캐시 전 | 450 | 60초 | 27,002 | 0 | 0 | 449.94 | 16.57ms | 34.24ms | 194.67ms | 392.70ms | 경계 |
| 캐시 전 | 500 | 75초 | 37,252 | 249 | 0 | 487.08 | 573.98ms | 1.50초 | 1.62초 | 2.10초 | 실패 |
| 캐시 전 | 1,000 | 20초 | 11,041 | 8,962 | 0 | 475.43 | 2.66초 | 3.24초 | 3.39초 | 3.72초 | 실패 |
| COUNT 캐시 | **500** | **3분** | **90,002** | **0** | **0** | **500.01** | **2.43ms** | **2.99ms** | **11.24ms** | **178.89ms** | **통과** |
| COUNT 캐시 | **1,000** | **3분** | **180,000** | **0** | **0** | **999.99** | **3.34ms** | **3.30ms** | **45.49ms** | **315.02ms** | **통과** |
| COUNT 캐시 | **1,250** | **3분** | **225,000** | **0** | **0** | **1,249.98** | **4.25ms** | **3.88ms** | **105.84ms** | **392.87ms** | **통과** |
| COUNT 캐시 | **1,375** | **3분** | **247,501** | **0** | **0** | **1,374.97** | **6.28ms** | **4.43ms** | **209.74ms** | **445.98ms** | **지연 실패** |
| COUNT 캐시 | **1,500** | **3분** | **240,451** | **29,550** | **0** | **1,315.69** | **354.20ms** | **4.34초** | **6.15초** | **6.94초** | **실패** |
| pre-warm + single-flight + no-tx | **1,375** | **3분** | **247,501** | **0** | **0** | **1,374.99** | **4.23ms** | **3.14ms** | **125.98ms** | **395.08ms** | **통과** |
| pre-warm + single-flight + no-tx | **1,500** | **3분** | **270,001** | **0** | **0** | **1,499.98** | **4.45ms** | **3.06ms** | **140.19ms** | **383.59ms** | **통과** |
| pre-warm + single-flight + no-tx | **1,750** | **3분** | **315,001** | **0** | **0** | **1,748.22** | **5.68ms** | **3.56ms** | **201.97ms** | **426.94ms** | **지연 실패** |
| pre-warm + single-flight + 일반 tx, cold | **1,500** | **3분** | **269,763** | **238** | **0** | **1,498.70** | **17.16ms** | **5.28ms** | **579.05ms** | **1.71초** | **실패** |
| pre-warm + single-flight + 일반 tx, warm | **1,500** | **3분** | **270,001** | **0** | **0** | **1,500.03** | **4.18ms** | **3.23ms** | **118.20ms** | **384.05ms** | **통과** |

캐시 전 500/1,000 RPS에서는 k6가 목표 도착률을 유지하지 못했다. 단순 캐시 적용 후에는 1,250 RPS까지 통과했고 1,375 RPS부터 지연 기준을 초과했다. pre-warm과 single-flight를 적용한 뒤 no-tx와 일반 트랜잭션 warm 구성 모두 1,500 RPS를 통과했다. 일반 트랜잭션은 정합성을 유지하면서 p99 118.20ms로 가장 좋은 1,500 RPS 결과를 냈다.

### 판정 기준

- 오류와 드롭이 없어야 한다.
- p95가 100ms 이하, p99가 200ms 이하인 상태를 기본 품질 기준으로 본다.
- 단발성 통과보다 스냅샷 적재가 여러 번 겹치는 지속 측정을 우선한다.

일반 트랜잭션 warm 구성은 1,500 RPS에서 드롭·오류·지연 기준과 3분 지속 조건을 모두 충족했다. 다만 cold 시작은 같은 부하에서 실패했으므로 운영 트래픽 투입 전 warm-up이나 점진적 ramp-up이 필요하다.

## 4. DB 조회 최적화 효과

### 4.1 스냅샷 인덱스

MySQL Performance Schema 누적 통계는 다음과 같다.

| 작업 | 호출 수 | 평균 | 최대 | 누적 검사 행 |
|---|---:|---:|---:|---:|
| 최신 스냅샷 `MAX(snapshot_at)` | 535,329 | 0.056ms | 15.268ms | 535,322 |
| 세대별 정확한 `COUNT` | 265,249 | 7.782ms | 18.547ms | 39,785,400,036 |
| 가격 오름차순 페이지 조회 | 535,239 | 0.785ms | 35.209ms | 2,545,321,940 |
| 스냅샷 `INSERT ... SELECT` | 309 | 423.042ms | 2.259초 | 80,700,266 |
| 오래된 세대 `DELETE` | 270 | 285.485ms | 5.699초 | 38,550,003 |

`EXPLAIN` 결과:

```text
세대별 COUNT
key: PRIMARY
Extra: Using index

가격순 페이지 조회
key: idx_down_price_snapshot_price_asc
Extra: Using index
```

확인된 개선 효과는 다음과 같다.

1. 요청 시점마다 시간 함수를 계산하며 후보를 Top-K 정렬하던 작업이 사라졌다.
2. 최신 세대 해결은 PK 선두 컬럼을 사용해 누적 평균 0.056ms로 끝난다.
3. 가격순 페이지는 전용 커버링 인덱스를 사용해 누적 평균 0.785ms로 끝난다.
4. 15만 건의 가격 계산은 요청마다 수행하지 않고 분당 한 번의 단일 `INSERT ... SELECT`로 상각된다.

즉, **가격 계산과 정렬 hot path는 DB에서 충분히 줄였다**고 설명할 수 있다.

### 4.2 세대별 COUNT 캐시

no-keyword 하향 가격순 요청은 다음 키로 정확한 전체 건수를 캐시한다.

```text
auction:down-price-snapshot:count:<snapshotAt>
```

스냅샷 세대는 생성 후 불변이므로 같은 `snapshotAt`의 전체 건수도 변하지 않는다. Redis 값은 문자열 `long`으로 저장하고 스냅샷 retention과 같은 TTL을 사용한다.

실제 런타임 검증 결과:

- 동일 세대 100회 요청: MySQL `COUNT`는 1회만 증가
- Redis 값: `150000`
- 같은 세대의 실제 MySQL 행 수: `150000`
- 500 RPS 3분, 총 90,002회 요청: MySQL `COUNT`는 12회만 증가
- 요청별 `COUNT` 제거율: 약 99.987%
- cold cache에서 시작한 1,000 RPS 3분, 총 180,000회 요청: MySQL `COUNT`는 17회만 증가
- 1,000 RPS 측정의 요청별 `COUNT` 제거율: 약 99.991%
- cold cache에서 시작한 1,250 RPS 3분, 총 225,000회 요청: MySQL `COUNT`는 17회만 증가
- 1,250 RPS 측정의 요청별 `COUNT` 제거율: 약 99.992%
- cold cache에서 시작한 1,375 RPS 3분, 총 247,501회 요청: MySQL `COUNT`는 15회만 증가
- 1,375 RPS 측정의 요청별 `COUNT` 제거율: 약 99.994%
- cold cache에서 시작한 1,500 RPS 3분, 총 240,451회 실행 요청: MySQL `COUNT`는 14회만 증가
- 1,500 RPS 측정의 요청별 `COUNT` 제거율: 약 99.994%
- pre-warm + single-flight 적용 후 1,375/1,500/1,750 RPS 각 3분 측정: MySQL `COUNT` 증가는 모두 0회
- 최종 1,500 RPS 측정: 270,001회 요청, 최대 VU 101, 드롭·오류 0

기존 단순 캐시에서는 세대가 바뀌는 순간 동시 miss로 소수의 중복 `COUNT`가 발생했다. 최종 구성은 capture 직후 pre-warm하고 예외적인 miss를 세대별 single-flight로 합쳐 이 중복을 제거했다. Redis miss·장애·잘못된 값은 기존 정확한 DB `COUNT`로 폴백한다. keyword 요청은 제목 필터 결과가 별도이므로 캐시하지 않는다.

## 5. 캐시 적용 전 처리량 병목

### 5.1 매 요청의 정확한 COUNT

세대별 `COUNT`는 인덱스만 읽지만 한 세대 약 15만 행을 매 요청마다 검사한다.

- 평균 `COUNT`: 7.782ms
- 가격 페이지 조회: 1.321ms
- 최신 세대 조회와 상세 조립 쿼리를 포함한 JDBC 커넥션 점유 시간: 약 10.4ms
- Hikari 최대 커넥션: 5

단순한 처리량 상한은 다음과 비슷하다.

```text
5 connections / 0.0104 seconds ≈ 480 requests/second
```

캐시 적용 전 1,000 RPS 측정에서 약 475 RPS에 머문 결과와 일치한다. 캐시 적용 후에는 이 `COUNT`가 정상 hit 요청에서 실행되지 않는다.

### 5.2 요청당 여러 DB 왕복

가격순 목록 한 건은 대략 다음 DB 작업을 수행한다.

1. DB 현재 시각 조회
2. 최신 스냅샷 세대 조회
3. 전체 건수 조회
4. 가격순 페이지 조회
5. 입찰 요약 조회
6. 경매 상세 조회
7. 썸네일 조회

가격 정렬 쿼리 하나만 빨라져도 전체 요청이 DB 커넥션을 점유하는 시간은 남는다.

## 6. 지연 피크 원인 분석

### 6.1 캐시 적용 전 500 RPS: 스냅샷 이전부터 대기열 누적

500 RPS 측정의 초별 평균 지연은 스냅샷 적재 전에 이미 계속 증가했다.

| 시각 | 평균 | 최대 |
|---|---:|---:|
| 13:52:10 | 14.47ms | 30.08ms |
| 13:52:11 | 18.69ms | 48.95ms |
| 13:52:12 | 32.70ms | 64.39ms |
| 13:52:13 | 92.38ms | 243.88ms |
| 13:52:14 | 172.70ms | 496.00ms |
| 13:52:15 | 238.14ms | 444.28ms |
| 13:52:38 | 1.11초 | 1.49초 |

스냅샷 적재 시각은 13:52:39.724였다. 따라서 500 RPS 실패의 근본 원인은 스냅샷 작업 하나가 아니라 지속 요청량이 약 480 RPS의 DB 처리 한계를 초과한 것이다.

적재 이후에는 기존 대기열 위에 백그라운드 쓰기가 겹쳤다.

| 시각 | 평균 | 최대 |
|---|---:|---:|
| 13:52:40 | 1.42초 | 1.88초 |
| 13:52:41 | 1.48초 | 2.10초 |

### 6.2 450 RPS: 스냅샷 작업이 순간 피크를 증폭

450 RPS에서는 평상시 지연이 약 10ms로 유지됐다. 스냅샷 적재 시각 13:58:44.772 직후 다음 변화가 관찰됐다.

| 시각 | 완료 요청 | 평균 | 최대 |
|---|---:|---:|---:|
| 13:58:40 | 450 | 10.45ms | 16.58ms |
| 13:58:43 | 450 | 9.70ms | 13.13ms |
| 13:58:44 | 426 | 13.38ms | 68.30ms |
| 13:58:45 | 389 | 187.27ms | 392.70ms |
| 13:58:46 | 497 | 156.58ms | 371.06ms |
| 13:58:47 | 488 | 36.38ms | 161.40ms |
| 13:58:48 | 450 | 9.72ms | 16.76ms |

스케줄러는 같은 실행 안에서 다음 작업을 순서대로 수행한다.

1. 새 세대 `INSERT ... SELECT`
2. 보존 기간이 지난 세대 `DELETE`

두 작업 모두 일반 API와 같은 Hikari 풀을 사용한다. 처리량 경계인 450 RPS에서 다섯 개 중 한 커넥션을 수백 ms 동안 점유하면 API 대기열이 순간적으로 생긴다.

### 6.3 배제된 원인

| 지표 | 관찰값 | 판단 |
|---|---:|---|
| MySQL data lock wait | 0 | 락 경합 아님 |
| 현재 data lock | 0 | 장기 미완료 트랜잭션 징후 없음 |
| InnoDB 버퍼풀 논리 읽기 | 3,797,805,089 |  |
| InnoDB 물리 읽기 | 639,885 |  |
| 버퍼풀 적중률 | 약 99.983% | 물리 디스크 미스가 주원인 아님 |
| Hikari timeout | 0 | 서버가 받은 요청은 커넥션 타임아웃 없이 처리 |

MySQL slow query log는 `OFF`, `long_query_time`은 10초였다. 이번 문제는 대부분 수십~수백 ms의 누적 지연이므로 slow query log보다 Performance Schema와 애플리케이션 풀 지표가 더 적합하다.

### 6.4 풀과 스레드 상태

| 부하 | Hikari active/idle/pending | Tomcat busy/max | 해석 |
|---|---|---|---|
| 캐시 전 500 RPS | 5 / 0 / 4 | 10 / 100 | DB 풀 포화 및 대기 시작 |
| 캐시 전 1,000 RPS | 5 / 0 / 95 | 100 / 100 | DB 풀과 요청 스레드 모두 포화 |
| COUNT 캐시 500 RPS | 주로 0~1 / 5~4 / 0 | 주로 0~2 / 100 | DB 풀 여유 유지 |
| COUNT 캐시 1,000 RPS | 최대 4 / 최소 1 / 0 | 최대 5 / 100 | 세대 전환 중에도 풀 대기 없음 |
| COUNT 캐시 1,250 RPS | 최대 4 / 최소 1 / 0 | 최대 5 / 100 | 세대 전환 중에도 풀 대기 없음 |
| COUNT 캐시 1,375 RPS | 5 / 0 / 최대 96 | 100 / 100 | 순간 포화 후 복구, p99 기준 초과 |
| COUNT 캐시 1,500 RPS | 5 / 0 / 최대 96 | 100 / 100 | 일시 처리 저하가 지속 대기열로 증폭 |
| 부하 종료 후 | 0 / 5 / 0 | 0 / 100 | 대기열과 커넥션 정상 회복 |

### 6.5 캐시 적용 후 1,250 RPS: 순간 피크 흡수

1,250 RPS는 cold cache로 시작해 15만 행 스냅샷 적재가 세 번 겹쳤다. 세대 전환 시 VU가 최대 338까지 일시 증가했지만 1초 안에 평상시 2~3으로 복귀했다. Hikari pending과 timeout은 계속 0이었고, Tomcat busy도 최대 5/100에 머물렀다.

p99 105.84ms는 1,000 RPS의 45.49ms보다 높아졌지만 기준인 200ms 이하다. 요청 드롭과 HTTP 오류 없이 목표 도착률을 끝까지 유지했으므로 현재 구성의 새 검증 안정 구간으로 판정한다.

### 6.6 캐시 적용 후 1,375 RPS: 처리량 유지, 지연 기준 초과

1,375 RPS는 247,501건을 모두 실행해 드롭과 HTTP 오류가 없었다. 그러나 스냅샷 세대 전환 중 Hikari pending 96, Tomcat busy 100/100을 순간적으로 기록했고 p99가 209.74ms로 기준 200ms를 초과했다.

포화는 1초 안에 해소되어 1,500 RPS처럼 지속 대기열로 증폭되지는 않았다. 따라서 처리량 기준은 통과했지만 품질 기준상 안정 구간에는 포함하지 않는다.

### 6.7 캐시 적용 후 1,500 RPS: 순간 처리 저하가 대기열로 증폭

1,500 RPS는 평상시 Hikari active 2~3, pending 0으로 처리됐다. 그러나 약 67초부터 Hikari와 Tomcat이 포화됐고 k6는 최대 2,500 VU에 도달했다.

- Hikari pending 최대: 96
- Tomcat busy 최대: 100/100
- Hikari timeout: 0
- k6 최대 VU: 2,500
- 부하 중 생성된 15만 행 스냅샷: 3세대
- 스냅샷 capture 스케줄러 관찰 최대 실행시간: 4.202초

포화 구간은 스냅샷 작업과 겹쳤지만 해당 작업 하나만을 단독 원인으로 단정할 수는 없다. 1,500 RPS에서는 순간적인 DB 처리시간 증가를 흡수할 여유가 부족했고, 요청 대기 증가가 커넥션 풀과 Tomcat 스레드 포화로 증폭됐다. 포화가 풀린 뒤에는 즉시 1,500 RPS 처리로 돌아왔지만 이미 발생한 드롭과 장꼬리 지연 때문에 지속 처리량 기준에는 실패했다.

부하 종료 직후 Hikari 0/5, pending 0, Tomcat busy 0으로 복구됐고 모든 컨테이너는 healthy 상태를 유지했다.

### 6.8 목록 read-only 트랜잭션 제거 재실험: stampede 차단 후 개선

첫 실험에서는 `AuctionListService.getList()`의 `@Transactional(readOnly = true)`만 제거하고 같은 1,375 RPS를 3분간 재측정했다.

| 항목 | 트랜잭션 유지 | 트랜잭션 제거 |
|---|---:|---:|
| 완료 요청 | 247,501 | 247,146 |
| 드롭 | 0 | 355 |
| HTTP 오류 | 0 | 0 |
| 평균 | 6.28ms | 17.82ms |
| p95 | 4.43ms | 5.45ms |
| p99 | 209.74ms | 634.43ms |
| 최대 | 445.98ms | 1.08초 |
| DB `COUNT` 증가 | 15회 | 149회 |

트랜잭션을 제거하자 API 요청에 의한 `SET SESSION TRANSACTION READ ONLY/READ WRITE`, `SET autocommit`, `COMMIT`은 사실상 사라졌다. 그러나 서버 재기동 직후 cold cache에서 동시 요청이 모두 Redis miss를 확인한 뒤 DB `COUNT`로 진입하면서 stampede가 발생했다.

기존 **read-only 트랜잭션은 첫 DB 조회부터 요청 종료까지 Hikari 커넥션을 점유해 동시 cache loader 수를 풀 크기 5개 수준으로 제한하는 부수 효과**가 있었다. 트랜잭션을 제거하면 이 제한이 사라져 `COUNT` 실행 수가 약 10배로 증가했고, 시작 직후 Hikari pending 89와 Tomcat busy 100/100을 기록했다.

이를 해결하기 위해 다음 두 가지를 적용했다.

1. 스냅샷 capture 트랜잭션이 커밋된 뒤 반환한 `snapshotAt`과 적재 행 수를 Redis에 즉시 pre-warm한다.
2. Redis miss가 발생해도 동일 JVM·동일 세대에서는 single-flight로 DB loader를 한 번만 실행한다.

그 뒤 목록 read-only 트랜잭션을 다시 제거하고 재측정했다.

| 항목 | 트랜잭션 유지 | 제거만 적용 | pre-warm + single-flight + 제거 |
|---|---:|---:|---:|
| 목표 RPS | 1,375 | 1,375 | 1,375 |
| 완료 요청 | 247,501 | 247,146 | 247,501 |
| 드롭 | 0 | 355 | 0 |
| 평균 | 6.28ms | 17.82ms | 4.23ms |
| p95 | 4.43ms | 5.45ms | 3.14ms |
| p99 | 209.74ms | 634.43ms | 125.98ms |
| 최대 | 445.98ms | 1.08초 | 395.08ms |
| DB `COUNT` 증가 | 15회 | 149회 | 0회 |

최종 1,375 RPS 측정 중 `SET autocommit`은 16회, `COMMIT`은 8회만 증가했고 `SET SESSION TRANSACTION READ ONLY`와 snapshot `COUNT`는 증가하지 않았다. 증가한 SET/COMMIT은 같은 구간에 네 번 실행된 capture/cleanup 트랜잭션과 정확히 일치하며 API 요청 수 247,501건에는 비례하지 않았다.

따라서 회귀 원인은 트랜잭션 제거 자체가 아니라 제거로 드러난 cold-cache stampede였고, 이를 차단한 최종 구성에서는 트랜잭션 유지보다 평균과 p99가 모두 개선됐다.

### 6.9 일반 트랜잭션 실험: 정합성 유지와 read-only 토글 제거

pre-warm과 single-flight를 유지한 채 `AuctionListService.getList()`에 일반 `@Transactional`을 적용했다. 기본 MySQL 격리 수준의 동일 트랜잭션 안에서 count와 페이지를 읽으므로 no-tx에서 사라졌던 조회 정합성을 복구한다.

일반 트랜잭션에서는 API 요청에 비례해 `SET autocommit`과 `COMMIT`은 실행됐지만 `SET SESSION TRANSACTION READ ONLY`와 `SET SESSION TRANSACTION READ WRITE` digest는 생성되지 않았다. warm 1,500 RPS 측정에서 `COMMIT`은 270,007회, `SET autocommit`은 540,009회 증가했고 두 트랜잭션 모드 제어문과 snapshot `COUNT` 증가는 모두 0회였다.

| 항목 | cold 시작 | warm 재측정 | no-tx warm |
|---|---:|---:|---:|
| 목표 RPS | 1,500 | 1,500 | 1,500 |
| 완료 요청 | 269,763 | 270,001 | 270,001 |
| 드롭 | 238 | 0 | 0 |
| HTTP 오류 | 0 | 0 | 0 |
| 평균 | 17.16ms | 4.18ms | 4.45ms |
| p95 | 5.28ms | 3.23ms | 3.06ms |
| p99 | 579.05ms | 118.20ms | 140.19ms |
| 최대 | 1.71초 | 384.05ms | 383.59ms |
| 최대 VU | 896 | 215 | 101 |

cold 측정은 서버 재기동 직후 시작해 첫 5초 동안 최대 VU 896까지 대기열이 증가했다가 정상화됐다. 이 초기 구간 때문에 전체 p99와 드롭 기준을 실패했다. 동일 서버가 완전히 warm-up된 뒤 다시 측정하자 예정된 270,001건을 모두 처리했고 p99 118.20ms로 no-tx보다도 21.99ms 낮았다.

따라서 일반 트랜잭션은 현재 측정 중 정합성과 warm 상태 성능을 함께 만족한 운영 후보다. 단, 재기동 직후 1,500 RPS를 즉시 받는 상황은 통과하지 못했으므로 readiness 이후 별도 warm-up이나 점진적 트래픽 전환이 필요하다.

### 6.10 read-only와 일반 트랜잭션 동일 조건 A/B

기존 측정은 캐시 구현과 warm 상태가 달라 `readOnly` 여부만의 차이를 설명할 수 없었다. 이를 분리하기 위해 다음 조건을 두 구성에 동일하게 적용하고 애노테이션만 변경했다.

1. 서버 컨테이너 재기동
2. health 응답 확인
3. 500 RPS로 30초 warm-up
4. 1,500 RPS로 3분 본 측정
5. pre-warm, single-flight, Hikari 5, Tomcat 100 및 테스트 데이터는 동일하게 유지

| 항목 | `@Transactional(readOnly = true)` | `@Transactional` | 차이 |
|---|---:|---:|---:|
| 완료 요청 | 270,001 | 270,001 | 동일 |
| 드롭 / HTTP 오류 | 0 / 0 | 0 / 0 | 동일 |
| 실제 RPS | 1,500.03 | 1,499.26 | -0.05% |
| 평균 | 5.93ms | 5.60ms | plain 0.33ms 낮음 |
| p95 | 4.61ms | 3.80ms | plain 0.81ms 낮음 |
| p99 | 189.42ms | 192.07ms | read-only 2.65ms 낮음 |
| 최대 | 426.26ms | 469.10ms | read-only 42.84ms 낮음 |
| 관찰 최대 VU | 382 | 199 | plain 183 낮음 |
| snapshot `COUNT` 증가 | 0 | 0 | 동일 |

평균과 p95는 일반 트랜잭션이 조금 낮고 p99와 최대는 read-only가 조금 낮았다. 스냅샷 적재 시점에 발생하는 수백 ms 피크가 전체 꼬리 지연을 좌우하고, 기존 일반 트랜잭션 반복 측정의 p99도 118.20~192.07ms로 변했다. 따라서 이번 한 쌍의 측정으로 어느 애노테이션이 더 빠르다고 볼 수 없으며, **1,500 RPS 처리 성능은 동등한 수준**으로 판정한다.

DB 동작 차이는 명확했다.

| 본 측정 중 증가 | read-only | 일반 tx |
|---|---:|---:|
| `SET autocommit` | 약 540,005 | 540,001 |
| `COMMIT` | 약 270,004 | 270,009 |
| `SET SESSION TRANSACTION READ ONLY` | 약 269,970 | 0 |
| `SET SESSION TRANSACTION READ WRITE` | 약 269,994 | 0 |

같은 차이는 Grafana MySQL QPS에서도 확인됐다. no-tx는 별도 측정의 1,500 RPS 구간이고, 일반 tx와 read-only는 동일 조건 A/B 구간이다.

| Grafana QPS | no-tx | 일반 tx | read-only |
|---|---:|---:|---:|
| total Queries / client Questions | 9.0K ops/s | 13.5K ops/s | 16.5K ops/s |
| `select` | 9.0K ops/s | 9.0K ops/s | 9.0K ops/s |
| `set_option` | 0.309 ops/s | 3.0K ops/s | 6.0K ops/s |
| `commit` | 0.0364 ops/s | 1.5K ops/s | 1.5K ops/s |

캐시 hit인 이 엔드포인트는 요청당 약 6개의 `SELECT`를 실행한다. 따라서 no-tx 스크린샷의 `9,000 SELECT/s ÷ 6 SELECT/request`로 해당 지점이 약 1,500 RPS 구간임을 역산할 수 있다. no-tx에 남은 소수의 `set_option`과 `commit`은 API 요청이 아니라 스냅샷·경매 종료 같은 백그라운드 트랜잭션과 메트릭 집계 구간의 흔적이다.

일반 트랜잭션은 요청마다 autocommit 전환 두 번과 `COMMIT` 한 번을 추가해 총 QPS가 13.5K가 된다. read-only는 여기에 `READ ONLY`와 `READ WRITE`를 하나씩 더 추가하므로 `set_option`이 3.0K 증가하고 총 QPS가 16.5K가 된다. 실제 데이터를 읽는 `select`는 세 구성 모두 9.0K ops/s로 동일하다. 따라서 no-tx와 일반 tx의 4.5K QPS 차이 및 일반 tx와 read-only의 3.0K QPS 차이는 핵심 조회 부하 감소가 아니라 트랜잭션·세션 제어 명령의 유무다.

read-only가 추가한 두 세션 제어문의 Performance Schema 누적 DB 실행시간은 약 8.89초였고, 요청당 약 0.031ms다. 로컬 Docker에서는 이 비용이 전체 지연 편차보다 작았다. 일반 트랜잭션도 같은 기본 격리 수준과 트랜잭션 경계를 사용하므로 이 목록의 읽기 정합성은 유지하면서 세션 모드 전환만 생략한다.

현재 1,500 RPS에서는 이 QPS 차이가 처리량이나 응답 지연의 유의미한 개선으로 이어지지 않았다. 더 높은 RPS, 원격 DB 네트워크 또는 MySQL CPU 한계 구간에서는 추가 왕복 제거가 의미를 가질 수 있지만, 현재 결과는 일반 트랜잭션 변경을 대규모 성능 최적화가 아니라 불필요한 프로토콜 왕복과 모니터링 노이즈 제거로 해석해야 한다.

따라서 현재 경로에서는 성능을 이유로 `readOnly`를 넣거나 뺄 근거가 없다. read-only 의미와 Hibernate의 변경 감지 억제를 우선하면 `readOnly = true`, 매 요청의 불필요한 세션 모드 전환 제거를 우선하면 일반 `@Transactional`을 선택할 수 있다. 현재 구현은 투영 중심 조회이고 plain tx에서도 성능·정합성을 충족하므로 일반 `@Transactional`을 유지한다.

## 7. 별도 백그라운드 부하

`AuctionClosingScheduler`의 종료 대상 조회도 Performance Schema 누적 상위 작업이었다.

| 항목 | 값 |
|---|---:|
| 호출 수 | 33,294 |
| 평균 | 136.176ms |
| 누적 실행시간 | 4,533.857초 |
| 누적 검사 행 | 9,988,200,066 |
| 실행 계획 | `Using where; Using index; Using filesort` |

이 쿼리는 스냅샷 조회 경로와 별개지만 같은 MySQL과 커넥션 풀을 사용하므로 간헐적인 추가 지연 요인이 될 수 있다. Performance Schema의 최대값은 장기간 누적치이므로 특정 k6 피크의 직접 원인으로 단정하지 않는다.

## 8. 운영 권장치

현재 측정 환경 기준 권장은 다음과 같다.

- 캐시 적용 전 검증된 지속 처리량: **400 RPS**
- 일반 `@Transactional` warm 구성의 검증된 지속 처리량: **1,500 RPS**
- 일반 `@Transactional` warm 1,500 RPS 반복 결과: 3분, 270,001건, 드롭 0, 오류 0, p99 118.20~192.07ms
- 동일 조건 read-only 1,500 RPS 결과: 3분, 270,001건, 드롭 0, 오류 0, p99 189.42ms
- 서버 재기동 직후 cold 1,500 RPS 결과: 3분, 269,763건, 드롭 238, 오류 0, p99 579.05ms
- no-tx 1,500 RPS 결과: 3분, 270,001건, 드롭 0, 오류 0, p99 140.19ms
- 1,750 RPS 결과: 3분, 315,001건, 드롭 0, 오류 0, p99 201.97ms로 지연 실패

따라서 현재 운영 후보는 일반 `@Transactional`이며, warm 상태의 검증된 안정 구간은 1,500 RPS다. 1,750 RPS부터는 품질 상한 밖이고 cold 상태에서 1,500 RPS를 즉시 받는 것도 실패했다. 운영에서는 readiness 통과만으로 곧바로 최대 트래픽을 연결하지 말고 warm-up 또는 점진적 전환을 적용해야 한다. 이 수치는 단일 엔드포인트를 대상으로 한 로컬 측정치이므로 다른 API, 배치, 네트워크, Redis 장애 폴백, 장애 복구 여유를 포함한 혼합 트래픽으로 다시 검증해야 한다.

Hikari 풀 크기만 늘리는 방식은 권장하지 않는다. 애플리케이션 대기는 줄 수 있지만 MySQL에 더 많은 동시 스캔을 밀어 넣어 CPU와 쿼리 경쟁을 악화시킬 수 있다.

## 9. Redis 캐시 적용 결과와 정합성

이번 변경은 가격이나 완성된 API 응답이 아니라 세대별 정확한 `totalCount`만 캐시했다. 기존 API 응답과 페이지 조립의 실시간성을 유지하면서 가장 큰 DB 스캔만 제거하기 위해서다.

적용된 동작:

1. `keyword == null`이면 `snapshotAt`별 Redis count를 조회한다.
2. hit이면 DB `COUNT` 없이 값을 사용한다.
3. capture 커밋 직후 적재 행 수를 해당 세대 키에 pre-warm한다.
4. 예외적인 miss에서는 세대별 single-flight로 정확한 DB `COUNT`를 한 번만 실행해 저장한다.
5. Redis 읽기·쓰기 실패 또는 잘못된 값은 예외를 전파하지 않고 DB로 폴백한다.
6. keyword가 있으면 기존 제목 조인 `COUNT`를 그대로 사용한다.

적용된 키:

```text
auction:down-price-snapshot:count:{snapshotAt}
```

캐시는 권위 데이터가 아니다. Redis가 재시작되거나 LRU로 키를 제거해도 DB 폴백으로 동일한 응답을 만든다. 세대별 값은 작고 보존기간 TTL로 자동 제거되므로 현재 64MiB Redis에서 추가 메모리 사용량은 미미하다.

single-flight는 JVM 단위다. 다중 애플리케이션 인스턴스에서도 capture 직후 Redis pre-warm이 정상 동작하면 DB loader는 실행되지 않지만, Redis 장애나 pre-warm 실패가 여러 인스턴스에서 동시에 발생하면 인스턴스당 한 번의 DB 폴백은 가능하다. 운영에서 이 상황까지 한 번으로 제한해야 한다면 유한 TTL 분산 lock을 추가 검토한다.

프론트가 응답 `asOf`를 다음 페이지 요청에 그대로 보내므로, 캐시 키에 `snapshotAt`을 포함하면 기존 세대의 페이지 일관성을 유지할 수 있다.

일반 `@Transactional` warm 1,500 RPS 측정은 세 번의 세대 전환을 포함해 통과했다. 세대별 snapshot `COUNT` 누적값은 측정 전후 동일했고 최대 VU는 215였다. 같은 pre-warm·single-flight를 사용한 no-tx 측정의 최대 VU는 101이었다.

## 10. 삭제 주기 변경 검토

스냅샷을 하루 동안 전부 쌓고 새벽 4시에 한 번에 `DELETE`하는 방식은 권장하지 않는다.

```text
150,000 rows/generation × 1,440 generations/day
= 216,000,000 rows/day
```

현재 약 10세대, 150만 행이 약 139MiB를 사용하므로 단순 선형 환산 시 하루 약 20GiB에 가까워질 수 있다. 대량 `DELETE`는 다음 비용을 한 시점에 집중시킨다.

- undo/redo 로그 증가
- purge 지연
- 버퍼풀 오염
- 인덱스 유지 비용
- 긴 트랜잭션과 복구 시간 증가

정리 작업의 요청 영향만 줄이는 목적이라면 다음 순서가 안전하다.

1. 조회 캐시로 API와 스냅샷 쓰기의 커넥션 경쟁을 분리한다.
2. 정리를 적재 직후가 아닌 별도 스케줄로 분리한다.
3. 보존 세대 수는 작게 유지한다.
4. 대규모 장기 보존이 필요하면 시간 파티셔닝 후 `DROP PARTITION`을 검토한다.

## 11. 다음 최적화 우선순위

1. 배포 직후 warm-up 또는 점진적 트래픽 전환을 포함해 cold-start 1,500 RPS 재검증
2. 1,500 RPS를 운영과 유사한 혼합 트래픽과 다중 애플리케이션 인스턴스 환경에서 재검증
3. Redis 장애와 pre-warm 실패 상태에서 single-flight DB 폴백 성능 검증
4. 1,500 RPS 초과가 목표라면 가격 페이지·상세 조립 DB 왕복과 snapshot/cleanup 경합을 단계적으로 최적화
5. `AuctionClosingScheduler` 조회의 성능 저하를 별도 부하 테스트

## 12. 최종 평가

`DOWN + 가격순 + 스냅샷 존재` 정상 경로에서는 시간 함수 기반 가격 계산과 인메모리 Top-K 정렬을 우회한다. ALL/UP 가격순과 스냅샷 부재 폴백은 기존 `AuctionPricePageQuery`를 그대로 사용한다. 최신 세대와 가격 페이지 조회는 모두 커버링 인덱스를 사용하며 각각 누적 평균 0.056ms, 0.785ms로 확인됐다.

세대별 정확한 `COUNT`를 Redis에 캐시한 결과, 1,000 RPS 3분 측정에서 요청 180,000건을 드롭·오류 없이 처리했고 p99는 45.49ms였다. 캐시 전 1,000 RPS가 약 475 RPS 처리와 8,962건 드롭에 머문 것과 비교하면 병목 원인과 개선 효과가 모두 실측으로 확인됐다.

capture 직후 count pre-warm과 세대별 single-flight를 적용한 뒤 일반 `@Transactional` warm 구성은 1,500 RPS에서 270,001건을 드롭·오류 없이 처리했다. 동일 조건 A/B에서 read-only의 p99는 189.42ms, 일반 트랜잭션의 p99는 192.07ms였고 평균과 p95는 반대로 일반 트랜잭션이 낮았다. 따라서 처리 성능은 동등하고, 일반 트랜잭션은 read-only가 요청마다 추가하는 두 세션 모드 제어문 없이 읽기 정합성을 유지한다. 1,750 RPS는 no-tx 측정에서 처리량을 유지했지만 p99 201.97ms로 지연 기준을 근소하게 초과했다.

반면 서버 재기동 직후 일반 트랜잭션 1,500 RPS는 p99 579.05ms와 238건 드롭으로 실패했다. 따라서 현재 하향 경매 no-keyword 가격순 조회는 로컬 Docker의 **warm 상태에서 1,500 RPS까지 검증**됐으며, 운영 적용 전 배포 warm-up 절차가 추가로 필요하다.

캐시 적용 후 전체 Gradle 테스트와 Docker 기동 검증을 통과했다.

## 부록 A. 스냅샷 구현 구성

### A.1 테이블과 인덱스

Flyway V9에서 다음 테이블과 인덱스를 생성한다.

```sql
CREATE TABLE down_price_snapshot (
    snapshot_at DATETIME(6) NOT NULL,
    auction_id  BIGINT      NOT NULL,
    price       BIGINT      NOT NULL,
    PRIMARY KEY (snapshot_at, auction_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX idx_down_price_snapshot_price_asc
    ON down_price_snapshot (snapshot_at, price ASC, auction_id DESC);

CREATE INDEX idx_down_price_snapshot_price_desc
    ON down_price_snapshot (snapshot_at, price DESC, auction_id DESC);
```

- `(snapshot_at, auction_id)` PK는 같은 세대에서 경매별로 한 행만 허용한다.
- 두 가격 인덱스는 한 세대를 가격 오름차순 또는 내림차순으로 읽고, 동률이면 `auction_id DESC`로 정렬한다.
- 세 컬럼이 모두 인덱스에 포함돼 no-keyword 가격 페이지는 테이블을 읽지 않는 커버링 인덱스 스캔으로 끝난다.
- `snapshot_at`이 PK 선두이므로 `MAX(snapshot_at) WHERE snapshot_at <= :asOf`로 세대를 해결할 수 있다.
- 스냅샷은 파생 데이터이므로 `auction` FK와 생성·수정 시각을 두지 않는다. 제목 검색에 필요한 JPA 연관은 `insertable = false, updatable = false`인 읽기 전용이다.

### A.2 가격 계산과 적재 대상

적재는 Spring Data JPA `@Modifying` 네이티브 쿼리의 단일 `INSERT ... SELECT`로 수행한다.

```sql
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
  AND a.ended_at > :snapshotAt;
```

가격은 다음 순서로 계산한다.

1. 시작 시각부터 `snapshotAt`까지 지난 분을 구하고 음수는 0으로 보정한다.
2. 지난 분을 가격 하락 주기로 나눠 완료된 하락 횟수만 `FLOOR`로 구한다.
3. `start_price - 하락 횟수 × drop_price`를 계산한다.
4. 결과가 `minimum_price`보다 작으면 최저가로 제한한다.

적재 대상은 `DOWN`이면서 완료되지 않았고, `started_at <= snapshotAt < ended_at`인 진행 중 경매다. `snapshotAt`은 행마다 평가되는 `NOW()`나 `SYSDATE()`가 아니라 동일한 바인딩 파라미터를 두 위치에 사용한다. 따라서 한 적재 문이 만든 모든 행의 세대와 가격 계산 기준 시각이 같다. 배치로 분리하지 않고 한 문으로 실행하므로 적재 도중 시간 경계를 넘어도 세대가 나뉘지 않는다.

### A.3 스케줄러 주기와 보존 기간

```yaml
app:
  auction:
    down-price-snapshot-interval: ${AUCTION_DOWN_PRICE_SNAPSHOT_INTERVAL:1m}
    down-price-snapshot-retention: ${AUCTION_DOWN_PRICE_SNAPSHOT_RETENTION:10m}
```

스케줄러는 `initialDelay = 0`으로 부팅 직후 실행하고, 작업 완료 후 기본 1분을 기다리는 fixed delay 방식이다. 한 번의 실행 흐름은 다음과 같다.

1. DB 현재 시각을 epoch 밀리초와 맞도록 밀리초 단위로 절삭해 `snapshotAt`을 만든다.
2. 해당 시각의 진행 중 하향 경매를 단일 문으로 적재한다.
3. 커밋된 세대의 `snapshotAt`과 적재 건수를 Redis count 키에 즉시 pre-warm한다.
4. 별도 호출로 `DB 현재 시각 - retention`보다 오래된 세대를 삭제한다.

기본 retention은 10분이므로 정상 상태에서는 최근 약 10세대만 유지한다. `snapshotAt`을 분 단위로 절삭하지 않는 이유는 가격 계산 기준 시각의 오차를 만들지 않으면서, API가 epoch 밀리초로 왕복한 `asOf`가 동일 세대를 다시 찾게 하기 위해서다.

### A.4 트랜잭션 경계와 실패 처리

| 경계 | 처리 방식 | 실패 시 동작 |
|---|---|---|
| `capture()` | 별도 `@Transactional`, 단일 `INSERT ... SELECT` | 문 전체가 실패하고 해당 세대는 생성되지 않음 |
| Redis pre-warm | capture 서비스 호출이 반환돼 커밋된 뒤 실행 | 저장 실패를 삼키고 다음 요청이 정확한 DB `COUNT`로 폴백 |
| `deleteOlderThan()` | capture와 분리된 별도 `@Transactional` | 정리만 실패하며 이미 커밋된 새 세대는 유지 |
| 스케줄러 | capture와 cleanup을 각각 독립된 try/catch로 실행 | 한 작업 실패가 다른 작업이나 다음 스케줄 실행을 중단하지 않음 |
| 목록 조회 | 일반 `@Transactional` 안에서 세대·페이지·행 조립 조회 | 기본 격리 수준의 읽기 정합성을 유지하고 read-only 세션 모드 전환은 생략 |

Redis는 권위 저장소가 아니다. 읽기 실패·잘못된 값·쓰기 실패는 API 실패로 전파하지 않고 DB `COUNT`로 폴백한다. 동일 JVM에서 같은 세대의 동시 cache miss는 single-flight로 하나의 loader만 실행한다. DB 조회 자체가 실패하면 해당 요청에는 오류를 전파하며, 잘못된 count를 캐시하지 않는다.
