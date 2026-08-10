# Bidwin 종합 관측성 환경

Grafana의 **Bidwin / Bidwin 종합 관측성 대시보드**에서 다음 신호를
같은 시간축으로 확인할 수 있습니다.

- 서비스: UP/DOWN, RPS, HTTP 4xx/5xx, p50/p95/p99, URI별 처리량
- 웹/DB pool: Tomcat busy/current/max thread, Hikari active/idle/pending/max,
  connection 획득 지연과 timeout
- JVM: heap/non-heap, GC 횟수와 pause, process CPU, live/peak thread
- MySQL: QPS, connection, slow query, buffer pool hit ratio, deadlock, row lock wait,
  현재 보유 락과 blocking transaction 관계
- 인프라: 호스트 CPU/RAM/disk I/O와 Prometheus scrape 상태
- 실패 분석: 애플리케이션 오류 로그와 Prometheus firing alert

기본 화면은 결과를 요약하고, 아래 섹션으로 내려갈수록 원인을 좁히는 구조입니다.

## 실행

저장소 루트에서 MySQL을 먼저 실행한 뒤 애플리케이션과 모니터링 환경을 실행합니다.
두 Compose 프로젝트는 `bidwin-network`를 통해 통신합니다.

현재 터미널 위치가 `bidwin-back`이면 먼저 저장소 루트로 이동합니다.

```bash
cd ..
```

```bash
docker volume create bidwin_mysql-data
docker compose -f compose.mysql.yaml up -d
docker compose up --build -d
```

각 환경의 상태를 따로 확인할 수 있습니다.

```bash
docker compose -f compose.mysql.yaml ps
docker compose ps
```

| 구성 요소 | 주소 | 용도 |
| --- | --- | --- |
| Spring Actuator | http://localhost:8080/actuator/health | 애플리케이션 상태 |
| Grafana | http://localhost:3000 | 종합 대시보드와 로그 |
| Prometheus | http://localhost:9090 | 메트릭 수집과 rule 평가 |
| Loki | http://localhost:3100/ready | 로그 저장소 상태 |
| Alloy | http://localhost:12345 | 컨테이너 로그 수집 파이프라인 |
| mysqld_exporter | http://localhost:9104/metrics | MySQL 시계열 메트릭 |
| MySQL | localhost:3306 | 로컬 데이터베이스 |

Grafana의 기본 계정은 `admin` / `admin`입니다. 필요한 경우 실행 전에
`GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`, `MYSQL_ROOT_PASSWORD`,
`MYSQL_EXPORTER_PASSWORD`, `MYSQL_GRAFANA_PASSWORD` 환경 변수를 설정합니다.

Prometheus, Loki, MySQL `performance_schema` 데이터 소스와 대시보드는 자동으로
등록됩니다. 모니터링 계정 비밀번호는 저장소에 기록하지 않고 환경 변수로 주입합니다.
`mysql-monitoring-user` one-shot 컨테이너가 기존 MySQL 볼륨에도 최대 3개 연결만
허용하는 계정을 멱등하게 구성합니다. Grafana 계정은 `performance_schema`만 읽을 수
있고, mysqld_exporter 계정은 exporter 공식 권장 권한으로 분리합니다.

## Alert 기준

다음 조건은 Prometheus rule로 평가되며 대시보드의 `발화 중 Alert`에 표시됩니다.

| 신호 | 기준 |
| --- | --- |
| 애플리케이션 | 1분간 scrape 실패 |
| HTTP 실패 | 5분간 5xx 비율 5% 초과 |
| HTTP 지연 | 5분간 p95 1초 초과 |
| Hikari | pending connection이 2분 이상 존재 |
| JVM heap | 10분간 85% 초과 |
| MySQL | 5분 내 deadlock 발생 또는 row lock wait 1분 지속 |
| 서버 | CPU 또는 RAM이 10분간 90% 초과 |

현재는 대시보드 표시까지만 구성되어 있습니다. Slack, 이메일 등 외부 통지는 별도
Alertmanager 연결이 필요합니다.

## 데이터 해석 시 주의점

Spring 컨테이너는 `t4g.micro`와 같은 ARM64, 2 vCPU, RAM 1 GiB로 실행되며
`memswap_limit: 3g`를 통해 swap 2 GiB를 추가로 사용할 수 있습니다. 실제 swap을
사용하려면 Docker 호스트에도 swap이 활성화되어 있어야 합니다. 이 구성은 EC2의
CPU credit, network 및 EBS 성능까지 모사하지는 않습니다. ARM64 emulation을
지원하지 않는 개발 환경에서는 `APP_PLATFORM=linux/amd64`로 실행할 수 있습니다.

Docker Desktop에서 node_exporter는 macOS 자체가 아니라 Docker Linux VM의 CPU,
RAM, disk를 보여줍니다. EC2 Linux에서 동일 구성을 실행하면 실제 서버 지표입니다.
`현재 보유 락 수`와 `현재 blocking 관계`는 조회 순간의 `performance_schema`
snapshot이고, deadlock 및 lock wait 패널은 Prometheus에 저장된 추세입니다.

## 종료

애플리케이션과 모니터링 환경만 종료합니다.

```bash
docker compose down
```

MySQL과 mysqld_exporter는 별도로 종료합니다. 외부 볼륨인 `bidwin_mysql-data`는
종료 후에도 보존됩니다.

```bash
docker compose -f compose.mysql.yaml down
```

MySQL 데이터 초기화가 필요할 때만 컨테이너를 내린 후
`docker volume rm bidwin_mysql-data`를 실행합니다.
