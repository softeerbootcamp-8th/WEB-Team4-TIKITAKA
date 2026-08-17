#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
backend_dir="$(cd "${script_dir}/.." && pwd)"
repository_dir="$(cd "${backend_dir}/.." && pwd)"

mysql_container="${MYSQL_CONTAINER:-bidwin-local-mysql-1}"
redis_container="${REDIS_CONTAINER:-bidwin-local-redis-1}"
mysql_port="${MYSQL_PORT:-3307}"
mysql_root_password="${MYSQL_ROOT_PASSWORD:-root-local}"
load_database="bidwin_load_$(date +%Y%m%d%H%M%S)_$$"

if [[ ! "${load_database}" =~ ^bidwin_load_[A-Za-z0-9_]+$ ]]; then
    echo "안전하지 않은 부하테스트 DB 이름입니다: ${load_database}" >&2
    exit 1
fi

database_created=false

cleanup() {
    if [[ "${database_created}" != true ]]; then
        return
    fi
    docker exec \
        -e MYSQL_PWD="${mysql_root_password}" \
        "${mysql_container}" \
        mysql -uroot \
        -e "DROP DATABASE IF EXISTS \`${load_database}\`"
}

trap cleanup EXIT

docker compose \
    -f "${repository_dir}/compose.local.yaml" \
    up -d mysql redis

mysql_ready=false
for _ in {1..30}; do
    if docker exec \
        -e MYSQL_PWD="${mysql_root_password}" \
        "${mysql_container}" \
        mysqladmin ping -uroot --silent >/dev/null 2>&1; then
        mysql_ready=true
        break
    fi
    sleep 1
done

if [[ "${mysql_ready}" != true ]]; then
    echo "MySQL 컨테이너가 준비되지 않았습니다: ${mysql_container}" >&2
    exit 1
fi

redis_ready=false
for _ in {1..30}; do
    if [[ "$(docker exec "${redis_container}" redis-cli ping 2>/dev/null)" == PONG ]]; then
        redis_ready=true
        break
    fi
    sleep 1
done

if [[ "${redis_ready}" != true ]]; then
    echo "Redis 컨테이너가 준비되지 않았습니다: ${redis_container}" >&2
    exit 1
fi

docker exec \
    -e MYSQL_PWD="${mysql_root_password}" \
    "${mysql_container}" \
    mysql -uroot \
    -e "CREATE DATABASE \`${load_database}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
database_created=true

echo "경매 마감 부하테스트를 시작합니다. database=${load_database}"

(
    cd "${backend_dir}"
    DB_URL="jdbc:mysql://127.0.0.1:${mysql_port}/${load_database}?connectionTimeZone=%2B09:00&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8" \
    DB_USERNAME=root \
    DB_PASSWORD="${mysql_root_password}" \
    REDIS_HOST=127.0.0.1 \
    REDIS_PORT="${REDIS_PORT:-6379}" \
    AUCTION_CLOSING_BATCH_SIZE="${AUCTION_CLOSING_BATCH_SIZE:-100}" \
    AUCTION_LOAD_COUNT="${AUCTION_LOAD_COUNT:-100}" \
    AUCTION_LOAD_LOSERS_PER_AUCTION="${AUCTION_LOAD_LOSERS_PER_AUCTION:-20}" \
    AUCTION_LOAD_DEPOSIT_AMOUNT="${AUCTION_LOAD_DEPOSIT_AMOUNT:-30000}" \
    AUCTION_LOAD_POISON_AUCTION_ID="${AUCTION_LOAD_POISON_AUCTION_ID:-1}" \
    ./gradlew auctionClosingLoadTest --console=plain
)

echo "결과: ${backend_dir}/build/reports/auction-closing-load/result.json"
