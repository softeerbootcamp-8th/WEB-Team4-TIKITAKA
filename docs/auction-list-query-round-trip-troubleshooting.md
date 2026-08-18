# 경매 목록 조회 DB Round Trip 최적화

> 이 문서는 문제 발견부터 가설, 구현, 검증, 결론까지의 과정을 순서대로 기록하는 실험 노트다. 측정 전 값은 결론으로 사용하지 않는다.

| 항목 | 내용 |
| --- | --- |
| 상태 | 1차 실험 완료 — 가격 외 정렬 최적화 채택 |
| 시작일 | 2026-08-18 |
| 대상 API | `GET /api/v1/auctions` |
| 기준 브랜치 | `dev-8` |
| 기준 커밋 | `c351b197527ae7c15a7c19fcd8cf3e1a901576e0` |
| 비교 방식 | 동일 환경의 Before/After 비교 부하 테스트 |

## 0. 30초 요약

`W`는 k6 스크립트에서 사용한 workload 식별자다. W2와 W3는 도메인 용어가 아니라, 반복 측정을 위해 붙인 다음 두 요청 조건의 이름이다.

| 식별자 | 사람이 읽는 이름 | 실제 요청 조건 | 선택 이유 |
| --- | --- | --- | --- |
| W2 | 최신순 활성 경매 전체 조회 | `sort=latest`, `status=ACTIVE`, `auctionType` 생략, page 1, size 16 | 기존 후보 SELECT 4회를 통합했을 때 비포화 구간에서도 개선되는지 확인한다. |
| W3 | 마감임박순 종료 경매 전체 조회 | `sort=deadline`, `status=ENDED`, `auctionType` 생략, page 1, size 16 | 기존 후보 SELECT가 6회로 가장 많은 가격 외 정렬의 포화 양상을 확인한다. |

`auctionType`을 생략한 `ALL`은 `UP`과 `DOWN` 경매를 모두 조회한다. 이 문서에서 A는 기존 QueryDSL JPA 분기 조회, B는 QueryDSL SQL `UNION ALL`로 후보 조회를 통합한 개선 버전이다.

| 구분 | 내용 |
| --- | --- |
| 문제 | 목록 한 건마다 조회하는 N+1은 아니지만, 상태·경매 유형 분기마다 후보 SELECT를 따로 실행해 요청당 최대 10회의 SQL이 발생했다. |
| 변경 | QueryDSL SQL의 `UNION ALL`로 가격 외 정렬의 후보 SELECT를 한 statement로 통합했다. API 계약과 후속 조회는 유지했다. |
| 결과 | 최신순 활성 전체 조회(W2)는 SQL 8→5회와 p95 8.24→6.44ms, 마감임박순 종료 전체 조회(W3)는 SQL 10→5회와 p95 688.82→11.69ms, dropped iteration 183→0을 기록했다. |
| 해석 | W2는 비포화 구간의 응답시간 개선이고, W3의 큰 차이는 개별 쿼리의 59배 가속이 아니라 DB round trip과 커넥션 점유 감소로 대기 증폭이 사라진 양상이다. |
| 한계 | 로컬 목표 400 RPS 실험이며 Hikari pending과 DB CPU를 수집하지 않았다. 운영에서 같은 수치가 나온다고 보장하거나 커넥션 대기를 직접 증명한 결과는 아니다. |

핵심은 “QueryDSL SQL이라서 빨라졌다”가 아니라 **한 요청에서 순차 실행하던 후보 SELECT 수를 줄여 DB와 애플리케이션 사이의 왕복 및 커넥션 점유를 줄였다**는 것이다.

## 1. 문제 정의

경매 목록 조회는 상태와 경매 유형별 후보를 여러 SQL로 조회한 뒤 애플리케이션에서 병합한다. 이후 가격·입찰 지표, 목록 상세, 대표 이미지를 각각 추가 조회하므로 한 API 요청이 여러 번의 DB round trip을 만든다.

현재 확인한 구현 특성은 다음과 같다.

- [`AuctionListService`](../bidwin-back/src/main/java/com/tikitaka/bidwinback/auction/application/AuctionListService.java)는 목록 조회 시작 시 DB 기준 시각을 1회 조회한다.
- [`QuerydslAuctionListQueryRepository`](../bidwin-back/src/main/java/com/tikitaka/bidwinback/auction/infrastructure/QuerydslAuctionListQueryRepository.java)는 `JPAQueryFactory`를 사용한다.
- 인덱스를 활용하기 위해 상태의 `OR` 조건을 서로 겹치지 않는 분기로 나눈다.
- 현재 사용하는 QueryDSL JPA 7.5의 공식 fluent API에는 `UNION ALL` 조합 API가 없어 분기마다 SQL을 실행하고 Java에서 정렬·병합한다.
- 경매 유형을 지정하지 않은 `ALL` 조회는 `UP`, `DOWN` 후보도 따로 조회한 뒤 병합한다.
- 페이지 후보를 구한 뒤 지표, 상세 정보, 대표 이미지를 별도 SQL로 조회한다.
- 기준 버전 빌드에는 `querydsl-jpa`만 있고 QueryDSL SQL 의존성과 설정은 없었다.

정적 분석 뒤 실제 요청을 계측한 결과, W2는 8회, W3는 10회의 SELECT statement를 실행했다. 따라서 문제를 단순한 가능성이 아니라 **상태·유형 분기만큼 반복되는 DB round trip**으로 확정했다.

이 문제는 결과 행마다 추가 조회하는 전형적인 N+1이 아니다. 인덱스를 활용하기 위해 정렬·상태·유형별로 나눈 **bounded query fan-out**과 가격순의 데이터 의존적 반복 조회가 원인이다.

## 2. Before/After 조회 흐름

### A: 기존 QueryDSL JPA 분기 조회

가격 외 정렬의 기존 정상 응답 경로는 다음과 같다.

```text
요청
 ├─ DB 현재 시각 조회                         1회
 ├─ 상태 분기 × 경매 유형 분기 후보 조회      b × t회
 ├─ 페이지 후보의 가격·입찰 지표 조회          1회
 ├─ 목록 상세 조회                            1회
 └─ 대표 이미지 조회                          1회
```

- `b`: 상태 분기 수
  - `RECOMMENDED`: `ACTIVE=1`, `ENDED=2`
  - `LATEST`, `DEADLINE`, 가격순: `ACTIVE=2`, `ENDED=3`
- `t`: 경매 유형 분기 수
  - 유형 지정: `1`
  - 유형 미지정(`ALL`): `2`

결과가 존재하는 가격 외 정렬의 요청당 예상 SQL 수는 `b × t + 4`다.

| 정렬 | 상태 | 경매 유형 | 후보 조회 | 요청 전체 예상 SQL |
| --- | --- | --- | ---: | ---: |
| `RECOMMENDED` | `ACTIVE` | `ALL` | 2 | 6 |
| `RECOMMENDED` | `ENDED` | `ALL` | 4 | 8 |
| `LATEST` / `DEADLINE` | `ACTIVE` | `ALL` | 4 | 8 |
| `LATEST` / `DEADLINE` | `ENDED` | `ALL` | 6 | 10 |
| `RECOMMENDED` | `ACTIVE` | 단일 유형 | 1 | 5 |
| `LATEST` / `DEADLINE` | `ACTIVE` | 단일 유형 | 2 | 6 |

결과가 없으면 지표, 상세, 대표 이미지 조회가 생략되므로 위 예상값에서 3회가 감소한다. `keyword`, `category` 조건은 SQL 내용에는 영향을 주지만 호출 횟수는 바꾸지 않는다.

가격순은 후보를 최대 1,000개씩 반복 조회하므로 데이터 분포와 요청 페이지에 따라 SQL 수가 달라진다.

| 경로 | 후보 조회 예상 SQL |
| --- | --- |
| `UP` 가격순 | 상태 분기마다 1회 (`b`) |
| `DOWN` + `PRICE_LOW` | 배치당 후보 ID와 상세 조회 2회 (`2k`) |
| `DOWN` + `PRICE_HIGH` | 배치당 상태 분기 수만큼 조회 (`b × k`) |
| 동일 가격 경계 소진 | 필요한 배치마다 1회 추가 (`e`) |

- `k`: 하향 경매 후보 배치 조회 횟수
- `e`: 1,000개 경계에서 동일 가격 후보를 추가 조회한 횟수
- `u`: 상향 경매 후보 조회 수. `UP` 또는 `ALL`이면 `b`, `DOWN`이면 `0`
- 최종 결과가 존재하면 입찰 요약, 상세, 대표 이미지 조회 3회와 DB 현재 시각 조회 1회가 추가된다.

따라서 결과가 존재할 때 가격순 요청의 예상 SQL 수는 다음과 같다.

```text
PRICE_LOW  = 1 + u + 2k + e + 3
PRICE_HIGH = 1 + u + (b × k) + e + 3
```

1개 배치, 동일 가격 경계 추가 조회 없음, `ALL` 조건의 예시는 다음과 같다.

| 정렬 | 상태 | 요청 전체 예상 SQL |
| --- | --- | ---: |
| `PRICE_LOW` | `ACTIVE` | 8 |
| `PRICE_LOW` | `ENDED` | 9 |
| `PRICE_HIGH` | `ACTIVE` | 8 |
| `PRICE_HIGH` | `ENDED` | 10 |

가격 외 정렬의 대표 경로는 다음과 같이 교차 확인했다.

- W2 기준 버전: Hibernate Statistics `prepareStatementCount` 8회
- W3 기준 버전: Performance Schema digest 증가량 기준 SELECT 10회
- 개선 버전 W1/W2/W3: Hibernate Statistics `prepareStatementCount` 각각 5회
- Performance Schema에는 W3 SELECT 10회 외에 `SET autocommit`, transaction mode, `COMMIT` 5회도 잡혔다. 결과 표의 SQL/요청은 애플리케이션이 실행한 SELECT 기준이다.

가격순 공식은 아직 코드 호출 경로로 계산한 예상값이며 이번 1차 실험 범위에서는 실제 계측하지 않았다.

### B: QueryDSL SQL 후보 통합

개선 후에는 상태·유형별 후보를 branch로 유지하되, 각 branch를 `UNION ALL`로 묶어 한 statement로 실행한다. 최종 후보 정렬과 페이지네이션은 DB에서 수행하고 이후 지표·상세·대표 이미지 조회는 기존과 동일하게 유지한다.

```text
A: DB 현재 시각 1회
   → 후보 branch SQL 4~6회
   → Java 정렬·병합
   → 지표·상세·대표 이미지 3회

B: DB 현재 시각 1회
   → UNION ALL 후보 SQL 1회
   → DB 최종 정렬·페이지네이션
   → 지표·상세·대표 이미지 3회
```

대표 시나리오의 SQL 수 구성은 다음과 같다. 개선 후에도 DB 현재 시각과 응답 조립에 필요한 후속 조회가 남으므로 전체 SQL 수는 1회가 아니라 5회다.

| 시나리오 | 버전 | DB 현재 시각 | 후보 SELECT | 후속 조회 | 합계 |
| --- | --- | ---: | ---: | ---: | ---: |
| W2: `LATEST`, `ACTIVE`, `ALL` | A | 1 | 4 | 3 | 8 |
| W2: `LATEST`, `ACTIVE`, `ALL` | B | 1 | 1 | 3 | 5 |
| W3: `DEADLINE`, `ENDED`, `ALL` | A | 1 | 6 | 3 | 10 |
| W3: `DEADLINE`, `ENDED`, `ALL` | B | 1 | 1 | 3 | 5 |

후속 조회 3회는 페이지 후보의 가격·입찰 지표, 목록 상세, 대표 이미지 조회다. 이번 변경은 fan-out의 원인이었던 후보 SELECT만 통합해 변경 범위를 제한했다.

## 3. 가설

### 주가설

QueryDSL SQL의 `UNION ALL`로 상태·경매 유형별 후보 조회를 하나의 SQL statement로 통합하면 요청당 DB round trip과 커넥션 점유 시간이 감소하고, 동일 부하에서 응답시간과 처리량이 개선될 것이다.

### 보조 가설

- 가격순의 반복 후보 조회도 분기 통합 또는 derived table을 사용하면 round trip을 줄일 수 있다.
- 후보 조회만 통합하는 작은 변경으로도 기본 목록 조회는 다음 수준까지 감소할 수 있다.
  - `LATEST` / `DEADLINE`, `ACTIVE`, `ALL`: 예상 8회 → 최대 5회
  - `RECOMMENDED`, `ACTIVE`, `ALL`: 예상 6회 → 최대 5회
- round trip 감소가 DB 내부 정렬·임시 테이블 비용보다 클 때만 최종 성능이 개선된다.

QueryDSL SQL 자체가 성능을 보장하는 것은 아니다. 핵심 독립 변수는 **QueryDSL 구현체가 아니라 요청당 SQL statement 수**다.

### 대안 검토

| 대안 | 장점 | 한계와 판단 |
| --- | --- | --- |
| 기존 QueryDSL JPA + Java 병합 유지 | 기존 인덱스와 구현을 그대로 유지한다. | 상태·유형 분기만큼 DB round trip이 반복되므로 문제를 해결하지 못한다. |
| 분기 조건을 하나의 `OR`로 결합 | SQL statement를 한 번만 실행할 수 있다. | branch별 index hint, 정렬, Top-K `LIMIT`을 그대로 보존하기 어렵고 실행 계획이 달라질 위험이 있어 이번 실험에서 제외했다. |
| Jakarta Persistence 3.2 Criteria 또는 Hibernate HQL | JPA 3.2의 `CriteriaBuilder.unionAll()`이나 Hibernate HQL로 set operation을 표현할 수 있다. | 표준 Criteria만으로는 branch별 `LIMIT`과 물리 index hint를 같은 수준으로 제어하기 어렵고, Hibernate 확장을 사용하면 provider 종속성이 생긴다. 이번 실험에서는 구현·측정하지 않았다. |
| Native SQL 직접 작성 | `UNION ALL`과 derived table을 바로 표현할 수 있다. | 동적 `keyword`·`category` 조건과 매핑을 문자열로 관리해야 해 기존 QueryDSL 구조보다 변경·검증 비용이 크다. |
| QueryDSL SQL의 `UNION ALL` | 동적 조건을 타입이 있는 표현식으로 유지하면서 branch별 인덱스·정렬·`LIMIT`을 표현할 수 있다. | SQL 모듈 의존성, 수동 물리 컬럼 선언, MySQL 종속성이 생긴다. 이 비용을 후보 조회 컴포넌트 하나로 제한하고 채택했다. |

QueryDSL SQL은 성능 최적화 도구 자체가 아니라, 기존 동적 조회 구조를 크게 바꾸지 않고 branch별 `LIMIT`·index hint를 포함한 `UNION ALL` SQL을 명시적으로 제어하기 위한 선택이다. JPA 기반 대안이 불가능해서 선택한 유일한 해법은 아니다.

## 4. 검증할 질문

- [x] 대표 가격 외 조건별 실제 요청당 SQL 수는 몇 회인가?
- [x] 상태·유형 분기 통합 후 기존 인덱스와 branch별 `LIMIT`을 유지할 수 있는가?
- [x] `UNION ALL` 결과의 정렬, offset, ID 동률 처리 결과가 기존 구현과 완전히 같은가?
- [ ] 가격순 반복 횟수 `k`, 경계 추가 조회 횟수 `e`는 실제 데이터에서 어떻게 분포하는가?
- [x] round trip 감소가 API p95/p99와 처리량에 유의미한 개선을 만드는가?
- [ ] DB CPU와 Hikari pending을 별도 계측해 개선 원인을 더 세분화할 수 있는가?
- [x] QueryDSL SQL 도입 비용을 작은 범위로 제한할 수 있는가?

## 5. 작업 계획

### Phase 1. 기준선 측정

- [x] 통합 테스트에 이미 활성화된 Hibernate Statistics의 `prepareStatementCount`로 W2의 SQL 수를 자동 검증한다.
- [x] W1과 W3의 개선 후 SQL 수를 자동 검증하고 W3 기준값을 Performance Schema로 교차 확인한다.
- [x] 대표 데이터셋의 규모와 분포를 고정하고 기록한다.
- [x] W3 `UNION ALL` SQL에 `EXPLAIN ANALYZE`를 실행해 실행 계획을 보관한다.
- [x] 현재 구현(A)의 W2/W3 부하 테스트를 각각 3회 수행한다.

### Phase 2. 최소 구현

- [x] `querydsl-sql` 의존성과 `JPASQLQuery` 기반 후보 조회를 추가한다.
- [x] 우선 가격 외 정렬의 후보 분기만 `UNION ALL`로 통합한다.
- [x] 기존 repository 인터페이스와 API 응답 계약은 변경하지 않는다.
- [x] 기존 정렬·필터·페이지네이션 통합 테스트를 그대로 통과시킨다.
- [x] A/B 결과로 1차 범위를 종료하고 가격순은 후속 실험으로 분리한다.

### Phase 3. 비교 검증

- [x] 개선 구현(B)의 W2/W3 부하 테스트를 같은 조건으로 각각 3회 수행한다.
- [x] 기능 결과와 쿼리 수를 A와 비교한다.
- [x] API 지표와 실행 계획을 비교한다.
- [ ] 애플리케이션 CPU·GC·Hikari와 DB CPU 지표를 함께 수집한다.
- [x] 실행 계획 악화나 특정 조건의 회귀가 없는지 확인한다.
- [x] 1차 범위의 변경을 채택한다.

### 구현 형태

별도 `DataSource`와 `SQLQueryFactory`를 구성하지 않고, 기존 `EntityManager` 트랜잭션 안에서 `JPASQLQuery`를 사용했다. 물리 테이블에서 후보 정렬에 필요한 컬럼만 선언한 `RelationalPathBase`를 두어 SQL Q 타입 생성 단계도 추가하지 않았다.

```sql
SELECT candidate.auction_id, candidate.sort_at
FROM (
    (SELECT id AS auction_id, ended_at AS sort_at
     FROM auction USE INDEX (idx_auction_snapshot_deadline)
     WHERE ...
     ORDER BY ended_at, id
     LIMIT :branchLimit)
    UNION ALL
    ...
) candidate
ORDER BY candidate.sort_at, candidate.auction_id
LIMIT :limit OFFSET :offset
```

branch마다 `offset + limit`개만 남긴 뒤 derived table에서 최종 정렬과 페이지네이션을 수행한다. 상태 분기와 `UP`/`DOWN` 분기는 서로 겹치지 않으므로 `UNION ALL`의 중복 제거 비용이 필요 없다.

## 6. 부하 테스트 설계

실제 운영 트래픽을 분할하지 않으므로 여기서 말하는 A/B는 **동일 환경에서 A와 B를 번갈아 실행하는 통제된 Before/After 비교**다.

### 비교 대상

- A: 기준 커밋 `c351b197527ae7c15a7c19fcd8cf3e1a901576e0`의 QueryDSL JPA 분기 조회
- B: `querydsl-sql`과 `JPASQLQuery`로 가격 외 후보 분기를 통합한 구현

실행 JAR SHA-256은 A `5b045ad1968018b90c4f0aa766aadeb39b041393e5f97112451e20eed9d0d8b6`, B `91ae1ef1b0990f98d2afbf760174d1485f56a234a6f8bb9555e61413144d39b7`다.

### 측정 환경

| 항목 | 값 |
| --- | --- |
| 호스트 | macOS arm64, 애플리케이션·k6·Docker가 같은 호스트에서 실행 |
| Java | Temurin OpenJDK 21.0.11 |
| JVM | `-Xms256m -Xmx512m` |
| DB | Docker MySQL 8.4.10, `127.0.0.1:3307` |
| 커넥션 풀 | Hikari maximum pool size 5 |
| 부하 도구 | k6 2.1.0, `constant-arrival-rate` |
| 성능 DB | 격리 스키마 `bidwin_perf_dev8` |
| 데이터 | 경매 100,000건, `UP` 50,000 / `DOWN` 50,000 |
| 상태 분포 | ACTIVE 33,334 / 종료 완료 33,334 / 종료 대기 33,332 |
| 이미지·입찰 | 이미지와 입찰 이력 없음. 후보 조회와 목록 projection 비교에 집중 |

시드는 [`auction-list-seed.sql`](../load-tests/sql/auction-list-seed.sql)로 생성했다. `ALL`은 API에서 `auctionType` 파라미터를 생략해 표현한다.

### 통제 조건

- 같은 애플리케이션·DB 인스턴스 사양
- 같은 DB 스냅샷과 인덱스
- 같은 JVM 옵션과 커넥션 풀 설정
- 같은 부하 발생기와 네트워크 위치
- 애플리케이션 교체 때 서버를 재시작하고 100 RPS로 10~15초 워밍업
- MySQL은 본 측정 사이에 재시작하지 않고 warm buffer pool 상태를 유지
- 최종 채택 데이터의 실행 순서 `A1 → A2 → A3 → B1 → B2 → B3 → A-late-control`
- B가 뒤에 실행된 순서 효과를 확인하기 위해 마지막에 A를 W2/W3 각각 1회 추가 측정
- 각 시나리오를 3회 반복하고 3회 중앙값으로 비교

### 시나리오

| ID | 요청 조건 | 검증 목적 | 1차 실험 |
| --- | --- | --- | --- |
| W1 | `RECOMMENDED`, `ACTIVE`, `ALL`, page 1, size 16 | 기본 요청 | SQL 수만 검증 |
| W2 | `LATEST`, `ACTIVE`, `ALL`, page 1, size 16 | 상태·유형 분기 통합 효과 | A/B 완료 |
| W3 | `DEADLINE`, `ENDED`, `ALL`, page 1, size 16 | 분기가 가장 많은 가격 외 정렬 | A/B 완료 |
| W4 | `PRICE_LOW`, `ACTIVE`, `ALL`, page 1, size 16 | 상·하향 가격 후보 병합 | 후속 범위 |
| W5 | `PRICE_HIGH`, `ACTIVE`, `ALL`, page 50, size 100 | 반복 Top-K 조회 비용 | 후속 범위 |
| W6 | 실제 예상 비율의 혼합 요청 | 전체 시스템 효과 | 후속 범위 |

예비 테스트에서 50, 500, 600, 800, 1,500 RPS를 탐색했다. 1,500 RPS는 로컬 MySQL 컨테이너 용량을 넘어 컨테이너가 재시작됐으므로 해당 실행과 그 직후 실행은 모두 폐기했다. 데이터 100,000건이 유지된 것을 확인하고 서버를 새로 띄운 뒤, 본 측정은 다음 조건으로 고정했다.

첫 B 구현은 기준 버전의 `USE INDEX`보다 강한 `FORCE INDEX`를 사용한 사실을 최종 diff 검토에서 발견했다. round trip 이외의 변수를 제거하기 위해 그 B 결과를 전부 폐기하고, `USE INDEX`로 맞춘 최종 JAR를 새로 빌드해 B 3회를 다시 측정했다.

- 목표 요청률: 400 RPS
- 측정 시간: 실행당 30초
- `preAllocatedVUs=100`, `maxVUs=300`
- 각 응답의 HTTP 200과 API `success=true` 검증
- 실행 스크립트: [`auction-list.js`](../load-tests/k6/auction-list.js)

### 재현 절차

1. Flyway migration이 적용된 빈 `bidwin_perf_dev8` 스키마에 고정 시드를 한 번 적재한다.
2. A 또는 B 애플리케이션을 위 측정 환경과 같은 JVM·Hikari 설정으로 실행한다.
3. 각 시나리오를 100 RPS로 10~15초 워밍업한다.
4. 400 RPS로 30초 측정하고 summary를 저장한다.
5. W2와 W3을 각각 3회 실행한 뒤 애플리케이션을 교체하고 같은 순서를 반복한다. DB는 재시작하거나 다시 시드하지 않는다.

```bash
mysql -h 127.0.0.1 -P 3307 -u root -p bidwin_perf_dev8 \
  < load-tests/sql/auction-list-seed.sql

BASE_URL=http://localhost:18080 \
WORKLOAD=W2 RATE=100 DURATION=15s \
PRE_ALLOCATED_VUS=100 MAX_VUS=300 \
k6 run load-tests/k6/auction-list.js

BASE_URL=http://localhost:18080 \
WORKLOAD=W2 RATE=400 DURATION=30s \
PRE_ALLOCATED_VUS=100 MAX_VUS=300 \
k6 run --summary-export=w2-summary.json load-tests/k6/auction-list.js
```

W3은 `WORKLOAD=W3`로 바꿔 같은 절차를 실행한다. `BASE_URL`과 DB 계정은 실행 환경에 맞게 변경한다. A/B 실행 파일이 뒤바뀌지 않도록 측정 전에 SHA-256을 위 기록과 대조한다. 원본 결과의 파일명 규칙과 채택 범위는 [`결과 README`](../load-tests/results/2026-08-18/README.md)에 기록했다.

### 측정 지표

| 계층 | 지표 | 수집 상태 |
| --- | --- | --- |
| API | 성공 RPS, 오류율, p50, p95, p99, 최대 응답시간 | 측정 완료 |
| 애플리케이션 | CPU, heap, GC pause, 실행 스레드, Hikari active/pending connection | 미계측 |
| DB | 요청당 SQL 수, 실행 계획, rows examined/returned | 부분 측정 |
| DB | CPU, connection 수, 임시 테이블 누적 지표 | 미계측 |
| 네트워크 | 애플리케이션↔DB 전송량과 패킷 단위 round trip | 미계측 |

`prepareStatementCount`는 애플리케이션의 SQL 실행 횟수를 측정하는 지표이며 네트워크 패킷 단위 round trip과 완전히 같지는 않다. 이 문서에서는 우선 일관된 비교 지표로 사용하고, 필요하면 JDBC fetch와 네트워크 계측을 추가한다.

#### 지표 용어

| 지표 | 의미 | 이 문서에서 읽는 방법 |
| --- | --- | --- |
| SQL/요청 | API 요청 한 건을 처리하며 실행한 애플리케이션 SELECT statement 수 | 값이 작을수록 DB 호출 횟수가 적다. `SET`, `COMMIT` 같은 세션·트랜잭션 제어문은 제외한다. |
| 성공 RPS | k6가 완료한 초당 HTTP 요청 수. 이번 결과에서는 HTTP 200과 API `success=true` check가 모두 통과해 성공 RPS로 기록했다. | 목표는 400 RPS다. 400보다 작으면 오류율뿐 아니라 dropped iteration도 함께 확인한다. |
| 평균 | 전체 요청 응답시간의 산술평균 | 일부 느린 요청의 영향을 크게 받으므로 p95·p99와 함께 본다. |
| p50 | 요청의 50%가 이 시간 안에 완료되는 중앙값 | 일반적인 요청의 응답시간을 나타낸다. |
| p95 | 요청의 95%가 이 시간 안에 완료되는 값 | 나머지 느린 5%를 제외한 상위 응답시간 경계다. 이 문서의 주 비교 지표다. |
| p99 | 요청의 99%가 이 시간 안에 완료되는 값 | 지연 꼬리 구간이 악화됐는지 확인한다. |
| 오류율 | 시작된 HTTP 요청 중 전송 또는 기대한 HTTP 응답에 실패한 비율 | API `success` check 결과와 함께 확인한다. 0%라도 시작하지 못한 요청이 있을 수 있으므로 dropped iteration과 별개다. |
| dropped iteration | `constant-arrival-rate`가 예정한 시각에 k6가 시작하지 못한 iteration 수 | 서버가 HTTP 오류를 반환한 횟수가 아니다. 응답 지연으로 `maxVUs`가 부족해 목표 요청률을 만들지 못한 신호다. |

### 결과 기록표

결과를 읽을 때 W2와 W3는 다음 조건의 요청을 의미한다. 두 시나리오 모두 `auctionType` 파라미터를 생략한 `ALL` 조회이므로 `UP`과 `DOWN` 경매를 함께 조회한다.

| 시나리오 | 정확한 조회 조건 | 경로 특성 |
| --- | --- | --- |
| W2 | `LATEST`, `ACTIVE`, `ALL`, page 1, size 16 | 최신순 활성 경매 조회. 기존 후보 SELECT 4회를 1회로 통합한 경로다. |
| W3 | `DEADLINE`, `ENDED`, `ALL`, page 1, size 16 | 마감임박순 종료 경매 조회. 기존 후보 SELECT가 6회로 가격 외 정렬 중 분기가 가장 많은 경로다. |

아래 값은 각 버전 3회 결과의 중앙값이다. 시간 단위는 ms이며, SQL/요청은 트랜잭션 제어문이 아닌 애플리케이션 SELECT statement 수다.

| 시나리오 | 버전 | SQL/요청 | 성공 RPS | 오류율 | 평균 | p50 | p95 | p99 | dropped iterations |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| W2 | A | 8 | 399.96 | 0% | 5.91 | 5.71 | 8.24 | 10.48 | 0 |
| W2 | B | 5 | 399.99 | 0% | 4.48 | 4.34 | 6.44 | 7.71 | 0 |
| W3 | A | 10 | 384.95 | 0% | 353.25 | 326.24 | 688.82 | 705.63 | 183 |
| W3 | B | 5 | 399.90 | 0% | 9.76 | 9.57 | 11.69 | 13.61 | 0 |

| 시나리오 | SQL 감소 | 평균 개선 | p95 개선 | p99 개선 | 처리 결과 |
| --- | ---: | ---: | ---: | ---: | --- |
| W2 | 37.5% | 24.1% | 21.9% | 26.5% | 양쪽 모두 목표 400 RPS, 드롭·오류 없음 |
| W3 | 50.0% | 97.2% | 98.3% | 98.1% | 성공 RPS 3.9% 증가, 중앙 dropped 183 → 0 |

중앙값만으로 실행 간 변동을 숨기지 않도록 본 측정 3회의 범위도 함께 기록한다.

| 시나리오 | 버전 | p95 범위(ms) | 성공 RPS 범위 | dropped iteration 범위 |
| --- | --- | ---: | ---: | ---: |
| W2 | A | 8.18~8.80 | 399.96~399.97 | 0 |
| W2 | B | 6.41~6.49 | 399.96~399.99 | 0 |
| W3 | A | 475.07~710.38 | 384.17~392.55 | 98~194 |
| W3 | B | 11.67~11.83 | 399.90~399.91 | 0 |

#### 측정 사실과 원인 해석의 경계

| 구분 | 내용 |
| --- | --- |
| 직접 측정한 사실 | W2/W3의 요청당 SELECT 수, HTTP 응답시간, 성공 RPS, 오류율, dropped iteration과 B 후보 SQL의 실행 계획 |
| 측정 결과로부터 한 해석 | SQL 및 커넥션 점유 감소가 포화 구간의 DB 커넥션 대기 증폭을 해소해 W3의 응답시간과 처리량이 회복됐다는 것 |
| 아직 직접 확인하지 못한 것 | Hikari active/pending·connection acquire 시간, DB CPU·connection 수, A/B 개별 SQL 누적 실행시간과 운영 트래픽에서의 개선율 |

따라서 요청당 SQL 감소와 해당 실험 환경의 성능 개선은 측정 결과로 확정한다. 반면 W3의 정확한 병목 구성과 운영 효과는 후속 계측이 필요한 해석으로 구분한다.

#### W2(최신순 활성 전체 조회) 해석: 비포화 구간의 개선

W2는 A와 B 모두 목표 400 RPS를 드롭 없이 처리했다. 이미 부하를 감당하던 경로에서도 SQL을 8회에서 5회로 줄이자 p95가 8.24ms에서 6.44ms로 21.9% 감소했다. 따라서 W2는 대기열 포화 효과가 개입되지 않은 상태에서도 round trip 감소가 응답시간을 개선한 결과로 해석한다.

#### W3(마감임박순 종료 전체 조회) 해석: 쿼리 자체의 59배 가속이 아닌 대기 증폭 해소

W3의 p95가 688.82ms에서 11.69ms로 감소한 것을 `UNION ALL` SQL 한 개가 기존 SQL보다 약 59배 빨라졌다는 의미로 해석하면 안 된다. 이 값은 개별 쿼리 실행시간이 아니라 HTTP 요청 전체 응답시간의 p95다.

A는 요청마다 SELECT 10회를 실행했고 커넥션 풀이 5개인 환경에서 목표 400 RPS를 유지하지 못했다. 요청 처리시간이 길어질수록 커넥션을 기다리는 요청이 늘고, 그 대기가 다시 전체 응답시간을 키우는 포화 구간에 들어간 것으로 해석한다. B는 후보 조회를 한 statement로 통합해 요청당 SELECT를 5회로 줄였고, 그 결과 성공 RPS가 384.95에서 399.90으로 회복되며 dropped iteration이 183에서 0으로 감소했다. 즉 W3의 큰 차이는 **개별 쿼리의 59배 가속이 아니라 DB round trip과 커넥션 점유 감소로 요청 대기열의 증폭이 사라진 결과**다.

다만 이번 실험에서는 Hikari pending connection과 DB CPU를 직접 수집하지 않았다. 따라서 커넥션 대기 해소는 SQL 수, 응답시간, 처리량, dropped iteration의 동시 변화를 근거로 한 해석이며 직접 계측으로 확정한 원인이라고 주장하지 않는다.

W3의 A는 HTTP 요청 오류가 난 것이 아니라 k6가 `maxVUs` 안에서 목표 도착률을 만들지 못해 일부 iteration을 시작하지 못했다. 따라서 오류율 0%와 dropped iteration 183을 함께 봐야 한다.

#### 수치의 적용 범위

이 결과는 로컬 단일 호스트에서 MySQL 커넥션 풀 5개, 경매 100,000건, 목표 400 RPS로 실행한 통제 실험의 값이다. 운영 환경의 네트워크 지연, 데이터 분포, 이미지·입찰 이력, 인스턴스 사양과 동시 트래픽은 다르므로 운영에서도 같은 응답시간이나 개선율이 재현된다고 보장할 수 없다. 이 실험으로 확정할 수 있는 것은 동일 조건에서 요청당 SQL 수가 감소했고, 그에 따라 W2와 W3의 성능 및 안정성이 개선됐다는 사실이다. 운영 효과는 staging과 운영 지표로 별도 확인해야 한다.

후반 A 대조군은 W2 p95 8.12ms·드롭 0, W3 p95 404.02ms·드롭 70으로 기존 A와 같은 방향을 보였다. 대조군은 시간대 drift 확인에만 사용하고 위 3회 중앙값에는 포함하지 않았다.

W3 A의 p95는 본 측정과 후반 대조군을 합쳐 404.02~710.38ms로 변동이 컸지만, 최종 B 3회는 11.67~11.83ms로 좁게 유지됐다. 따라서 로컬 환경에서의 정확한 개선율보다 병목 제거 방향과 반복 안정성을 핵심 결과로 본다.

원본 12개 본 측정 summary, 2개 후반 대조군 summary와 계측 결과는 [`load-tests/results/2026-08-18`](../load-tests/results/2026-08-18/README.md)에 보관했다.

### 실행 계획 확인

W3 B의 `EXPLAIN ANALYZE` 결과는 다음을 확인했다.

- 모든 branch가 `idx_auction_snapshot_deadline` covering index를 사용했다.
- 각 branch의 `LIMIT 16`이 유지됐다.
- 결과가 있는 네 branch에서 총 64행만 materialize했고 outer sort도 64행을 대상으로 했다.
- 전체 후보 SQL 실제 시간은 11.4ms였다.
- `completed_at <= asOf` 두 branch는 각각 16,667행을 읽고 정렬했다. 이 비용은 기존 A에도 있던 비용이며 후속 인덱스 최적화 후보로 남긴다.

재현 SQL은 [`explain-w3-union.sql`](../load-tests/sql/explain-w3-union.sql), 원본 계획은 [`b-w3-explain-analyze.txt`](../load-tests/results/2026-08-18/b-w3-explain-analyze.txt)에서 확인할 수 있다.

## 7. 성공 기준

- [x] 기존 필터, 정렬, 페이지네이션, `asOf` 기준 결과가 동일하다.
- [x] W2와 W3의 요청당 SQL 수가 감소한다.
- [x] 동일 400 RPS에서 B의 p95와 p99가 개선되고 오류율이 증가하지 않는다.
- [x] A가 목표 도착률을 놓친 W3에서 B는 400 RPS를 드롭 없이 처리한다.
- [x] branch 인덱스와 `LIMIT`, 작은 outer materialization을 실행 계획으로 확인한다.
- [ ] DB CPU, Hikari pending, GC를 포함한 운영 유사 환경 검증을 완료한다.

기능·SQL 수·API 성능 기준은 통과했다. DB 자원 지표는 미계측이므로 운영 반영 전 staging 관찰 항목으로 남긴다.

## 8. 예상 위험과 확인 사항

- `UNION ALL` derived table은 materialize되지만 W3 page 1에서는 branch `LIMIT` 덕분에 64행으로 제한됐다. 큰 offset에서는 `offset + limit`만큼 커지므로 별도 검증이 필요하다.
- 수동 `RelationalPathBase`의 물리 컬럼명이 migration과 어긋날 수 있다. MySQL 통합 테스트를 변경 감지 장치로 유지한다.
- 구현은 `MySQLTemplates`와 index hint를 사용하므로 DB 벤더 종속적이다.
- `completed_at <= asOf` branch는 현재 인덱스에서도 16,667행을 읽는다. 이번 변경은 round trip을 줄였지만 rows examined 자체를 해결하지는 않았다.
- 로컬 단일 호스트 측정은 운영 네트워크, 데이터 분포, CPU 경합을 재현하지 못한다.
- 이미지와 입찰 이력이 없는 데이터셋이라 목록 후속 projection 비용이 운영보다 작을 수 있다. 다만 A/B에는 동일 데이터가 사용됐다.
- 본 측정은 `A1 → A2 → A3 → B1 → B2 → B3` 순서라 완전한 무작위 교차 실험이 아니다. 후반 A 대조군으로 시간 순서 효과를 확인했지만 잔여 캐시·호스트 변동을 완전히 제거하지는 못한다.
- 30초 실행 3회의 중앙값과 범위는 로컬 비교의 반복성을 보여주지만 통계적 유의성이나 장시간 안정성을 보장하지 않는다.
- 가격순의 동적 현재가, 반복 Top-K, 동일 가격 경계는 변경하지 않았고 별도 실험이 필요하다.
- DB CPU와 Hikari pending을 수집하지 않았으므로 개선 원인을 round trip 하나로 완전히 분해해 주장하지 않는다.

### 후속 계측 계획

운영 반영 전에는 동일 A/B 조건 또는 staging의 대표 트래픽에서 다음 지표를 같은 시간축으로 수집한다.

| 계층 | 추가 지표 | 확인하려는 내용 |
| --- | --- | --- |
| Hikari | active·idle·pending connection, connection acquire p95/p99 | A의 W3에서 pending과 획득 대기가 증가하고 B에서 감소하는지 직접 확인한다. |
| 애플리케이션 | CPU, heap, GC pause, 실행 스레드 | 병목이 애플리케이션 CPU나 GC로 이동하지 않았는지 확인한다. |
| MySQL | CPU, `Threads_running`, connection 수, statement 실행시간 | DB 내부 실행 비용과 커넥션 경합을 round trip 효과와 분리한다. |
| 트래픽 | W1~W5 혼합 비율, 큰 offset, 실제 이미지·입찰 분포 | 단일 page 1 시나리오의 개선이 실제 목록 트래픽에서도 유지되는지 확인한다. |

A에서 Hikari pending 또는 acquire 시간이 증가하고 B에서 SQL 수와 함께 감소하면 DB 커넥션 대기 해소라는 원인 설명을 직접 강화할 수 있다. 변화가 없다면 DB 실행시간, 애플리케이션 스레드 또는 로컬 자원 경합을 다시 분석한다.

## 9. 의사결정 로그

| 날짜 | 결정 | 근거 |
| --- | --- | --- |
| 2026-08-18 | QueryDSL SQL 도입을 결론이 아닌 검증할 가설로 둔다. | round trip 감소와 실제 성능 개선은 별도 문제이므로 부하 테스트가 필요하다. |
| 2026-08-18 | 가격 외 후보 조회 통합부터 검토한다. | 쿼리 수가 조건만으로 결정되어 효과를 분리해 측정하기 쉽고 변경 범위가 작다. |
| 2026-08-18 | 실트래픽 A/B가 아닌 통제된 Before/After 부하 테스트를 수행한다. | 현재 계획은 운영 트래픽 분할 실험이 아니기 때문이다. |
| 2026-08-18 | `SQLQueryFactory` 대신 기존 `EntityManager`를 쓰는 `JPASQLQuery`를 선택한다. | 별도 DataSource·transaction wiring 없이 기존 read-only transaction을 공유하고 변경 범위를 줄일 수 있다. |
| 2026-08-18 | SQL code generation 대신 후보 컬럼만 수동 `RelationalPathBase`로 선언한다. | 새 Gradle codegen 단계 없이 한 테이블의 9개 컬럼만 관리하면 된다. 스키마 drift 위험은 통합 테스트로 방어한다. |
| 2026-08-18 | 기존과 동일하게 `USE INDEX`를 유지하고 `FORCE INDEX` 예비 결과는 폐기한다. | optimizer hint 강도가 달라지면 round trip 외 변수가 A/B에 섞이기 때문이다. |
| 2026-08-18 | 1차 변경을 채택하고 가격순은 건드리지 않는다. | W2/W3가 성공 기준을 통과했고 가격순은 알고리즘과 검증 범위가 다르다. |

## 10. 진행 로그

### 2026-08-18 — 조사 시작

- 대상 API와 Controller → Service → Repository 호출 경로를 확인했다.
- QueryDSL JPA의 `UNION ALL` 제약 때문에 상태·유형 분기를 여러 SQL로 조회하는 구조를 확인했다.
- 코드 호출 경로를 기준으로 조건별 예상 SQL 수를 계산했다.
- 전형적인 N+1이 아니라 요청당 분기 수가 제한된 bounded query fan-out으로 분류했다.

### 2026-08-18 — 첫 번째 기준선 확정

- Hibernate Statistics의 `prepareStatementCount`로 Service 전체 호출을 계측하는 통합 테스트를 추가했다.
- `LATEST`, `ACTIVE`, `ALL`, page 1, size 16 조건에서 SQL 8회 실행을 실제 MySQL 8.4 환경에서 확인했다.
- 테스트 데이터는 결과가 존재하도록 `UP`, `DOWN` 경매를 각각 1건 생성했다.
- 로컬 Compose의 MySQL 포트는 3307이므로 애플리케이션 기본값 3306 대신 `DB_URL`을 명시해야 했다.

### 2026-08-18 — 최소 구현

- `querydsl-sql:7.5` 의존성을 추가했다.
- `LATEST`, `DEADLINE`, `RECOMMENDED` 후보 조회를 `JPASQLQuery`의 `UNION ALL` 한 statement로 통합했다.
- branch별 index hint, 정렬, `offset + limit` 제한과 outer 정렬·offset·limit을 유지했다.
- 기존 repository 인터페이스와 응답 DTO는 변경하지 않았다.
- W1/W2/W3의 개선 후 SQL 수가 모두 5회임을 통합 테스트로 고정했다.
- 기존 정렬, 필터, 페이지네이션, tie-break 통합 테스트를 통과했다.

### 2026-08-18 — 데이터와 예비 부하

- Flyway v17까지 적용한 격리 스키마 `bidwin_perf_dev8`를 만들었다.
- 시드 SQL로 경매 100,000건을 생성하고 유형과 상태 분포를 기록했다.
- 1,500 RPS 예비 부하는 로컬 MySQL 컨테이너를 재시작시킬 정도로 과도했다. 이 실행과 직후 결과를 폐기하고 데이터 보존과 DB health를 확인했다.
- 안정 구간을 다시 탐색해 본 측정 조건을 400 RPS, 30초로 고정했다.

### 2026-08-18 — A/B 3회 반복

- W2 A/B는 모두 목표 400 RPS와 오류율 0%를 유지했다.
- W3 A는 중앙 384.95 RPS, p95 688.82ms, dropped iteration 183이었다.
- 최종 검토에서 index hint 강도 차이를 발견해 최초 B 결과를 폐기하고 `USE INDEX`로 다시 측정했다.
- 최종 B에서 W2 중앙 p95는 21.9% 감소했다.
- W3 B는 중앙 399.90 RPS, p95 11.69ms, dropped iteration 0이었다.
- W3 A의 Performance Schema digest를 요청 전후로 비교해 후보 6회와 후속 4회, 총 SELECT 10회를 확인했다.
- W3 B 실행 계획에서 covering index, branch `LIMIT`, 64행 materialization을 확인했다.

### 2026-08-18 — 회귀 검증

- 기준 버전과 개선 버전의 W2 smoke 응답에서 `items` 전체가 동일함을 확인했다.
- `QuerydslAuctionListQueryRepositoryIntegrationTest` 전체가 통과했다.
- MySQL 8.4를 사용한 백엔드 `./gradlew test` 전체가 통과했다.

## 11. 최종 결론

가격 외 목록 조회에 QueryDSL SQL `UNION ALL`을 적용하는 변경을 채택한다.

- W2(`LATEST`, `ACTIVE`, `ALL`)는 SQL 8→5회, p95 8.24→6.44ms로 개선됐다.
- W3(`DEADLINE`, `ENDED`, `ALL`)는 SQL 10→5회로 감소했고, 포화 양상에서 벗어나며 p95 688.82→11.69ms, dropped iteration 183→0을 기록했다.
- 기능 계약과 정렬·필터·페이지네이션 결과는 유지됐다.
- outer materialization 비용은 page 1 기준 64행으로 제한됐으며 기존 인덱스를 사용했다.

결론은 “QueryDSL SQL이라서 빨라졌다”가 아니라, **한 요청에서 순차 실행하던 상태·유형 후보 SELECT를 한 statement로 합쳐 커넥션을 점유한 채 반복하던 round trip을 줄였고, 같은 데이터와 부하에서 그 효과를 관측했다**는 것이다.

Jakarta Persistence 3.2와 Hibernate도 set operation을 지원하므로 QueryDSL SQL만 가능한 것은 아니다. 현재 QueryDSL JPA 7.5 fluent API가 이를 직접 제공하지 않고, 이번 쿼리의 branch별 `LIMIT`·MySQL index hint·outer pagination을 명시적으로 제어하기 위해 QueryDSL SQL을 선택했다. JPA Criteria/HQL 대안은 이번 A/B 실험에서 구현하거나 비교하지 않았다.

W3의 수치는 개별 쿼리가 약 59배 빨라졌다는 뜻이 아니다. 요청당 SQL과 커넥션 점유가 줄면서 DB 커넥션 대기 증폭이 해소된 양상과 일치한다. 다만 Hikari pending을 수집하지 않았으므로 이 원인 해석에는 계측상 한계가 있다. 또한 모든 성능 수치는 로컬 목표 400 RPS 실험에 한정되며 운영 환경의 동일한 개선율을 보장하지 않는다.

가격순은 이번 변경에 포함하지 않는다. 다음 단계는 staging에서 DB CPU·Hikari pending·GC를 함께 관찰하고, 실제 트래픽 비율의 혼합 시나리오와 큰 offset을 검증하는 것이다.
