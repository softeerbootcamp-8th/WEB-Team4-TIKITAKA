# 로컬 Docker 테스트 환경

팀원이 저장소를 받은 뒤 MySQL, Redis, Spring 서버, Prometheus, Loki, Alloy, Grafana를 한 번에 실행하는 구성이다.

## 실행

기본값만 사용할 때는 별도 환경 파일이 필요 없다.

```bash
docker compose -f compose.local.yaml up -d --build
```

메일/S3 설정이나 포트를 변경하려면 예제 파일을 복사한 뒤 `--env-file`을 사용한다.

```bash
cp deploy/.env.example deploy/.env
docker compose --env-file deploy/.env -f compose.local.yaml up -d --build
```

- Spring API: http://localhost:8080
- Spring Actuator: http://localhost:9292/actuator/prometheus
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (`admin` / `admin`)
- MySQL: `localhost:3307` (`bidwin` / `bidwin-local`)
- Redis: `localhost:6379` (입찰가 캐시, 비밀번호 없음)

Spring Actuator와 Prometheus는 기본적으로 `127.0.0.1`에만 바인딩된다. Loki와 Alloy 포트는 Compose 내부에서만 열리며 Grafana datasource로 자동 등록된다. Alloy는 `bidwin-local` Compose 프로젝트의 컨테이너 로그만 Loki로 전송한다.

## 종료

컨테이너만 종료하면 데이터 볼륨은 유지된다.

```bash
docker compose -f compose.local.yaml down
```

테스트 데이터를 포함한 볼륨까지 삭제할 때만 다음 명령을 사용한다.

```bash
docker compose -f compose.local.yaml down -v
```
