#!/usr/bin/env python3
"""Bidwin 로컬 MySQL에 대용량 정합성 목 데이터를 생성한다.

필수 패키지: pip install Faker

기본 생성량:
  member 100,000 / auction 1,000,000 (OPEN 300,000)
  bid 5,000,000 / sealed_bid 500,000 / image 1,000,000
  auction_trade 700,000 / auction_deposit 700,000

기존 데이터가 있으면 중단한다. 기존 데이터를 지우고 다시 만들 때만 --reset을 사용한다.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from datetime import datetime, timedelta
from typing import Iterable, Iterator

from faker import Faker


MEMBER_COUNT = 100_000
AUCTION_COUNT = 1_000_000
COMPLETED_AUCTION_COUNT = 700_000
ACTIVE_AUCTION_COUNT = 300_000
BID_COUNT = 5_000_000
SEALED_BID_COUNT = 500_000
IMAGE_COUNT = 1_000_000
TRADE_COUNT = 700_000
DEPOSIT_COUNT = 700_000

COMPLETED_UP_COUNT = 600_000
COMPLETED_DOWN_START = 600_001
INITIAL_POINT = 2_000_000
PASSWORD_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO5n8yV7l6y6Y4pT6Vh7Kp2rS3mL8aM1K"

TARGET_TABLES = (
    "member",
    "auction",
    "up_auction",
    "down_auction",
    "bid",
    "sealed_bid",
    "image",
    "auction_trade",
    "auction_deposit",
)

RESET_TABLES = (
    "auction_trade",
    "auction_deposit",
    "sealed_bid",
    "bid",
    "image",
    "up_auction",
    "down_auction",
    "auction",
    "member",
)


def sql_text(value: str) -> str:
    escaped = (
        value.replace("\\", "\\\\")
        .replace("'", "''")
        .replace("\0", "")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
    )
    return f"'{escaped}'"


def sql_datetime(value: datetime) -> str:
    return f"'{value.strftime('%Y-%m-%d %H:%M:%S.%f')}'"


def seller_id(auction_id: int) -> int:
    return (auction_id * 37) % MEMBER_COUNT + 1


def buyer_id(auction_id: int) -> int:
    candidate = (auction_id * 53) % MEMBER_COUNT + 1
    seller = seller_id(auction_id)
    return candidate if candidate != seller else candidate % MEMBER_COUNT + 1


def start_price(auction_id: int) -> int:
    # 입찰 보증금 30%가 정수로 정확히 계산되도록 만 원 단위를 사용한다.
    return 10_000 + (auction_id % 20) * 10_000


def is_up_auction(auction_id: int) -> bool:
    if auction_id <= COMPLETED_UP_COUNT:
        return True
    if auction_id <= COMPLETED_AUCTION_COUNT:
        return False
    return auction_id % 5 != 0


def open_bid_count(auction_id: int) -> int:
    if auction_id <= 100_000:
        return 9
    if auction_id <= COMPLETED_UP_COUNT:
        return 8
    if auction_id <= COMPLETED_AUCTION_COUNT:
        return 1
    return 0


def down_final_price(auction_id: int) -> int:
    price = start_price(auction_id)
    minimum = price // 2
    elapsed_drops = auction_id % 12 + 1
    return max(minimum, price - elapsed_drops * 5_000)


def final_price(auction_id: int) -> int:
    price = start_price(auction_id)
    if auction_id <= COMPLETED_UP_COUNT:
        highest_open = price + open_bid_count(auction_id) * 1_000
        return highest_open + (1_000 if auction_id <= SEALED_BID_COUNT else 0)
    return down_final_price(auction_id)


def trade_status(auction_id: int) -> str:
    if is_up_auction(auction_id):
        remainder = auction_id % 10
        if remainder == 0:
            return "WAITING_CONFIRM"
        if remainder in (1, 2):
            return "CONFIRMED"
        return "COMPLETED"
    return "CONFIRMED" if auction_id % 5 == 0 else "COMPLETED"


def deposit_state(auction_id: int) -> tuple[int, str]:
    status = trade_status(auction_id)
    if status == "WAITING_CONFIRM":
        return start_price(auction_id) * 3 // 10, "HELD"
    if status == "CONFIRMED":
        return final_price(auction_id), "HELD"
    return final_price(auction_id), "USED"


def auction_times(auction_id: int, now: datetime) -> tuple[datetime, datetime, datetime | None]:
    if auction_id <= COMPLETED_AUCTION_COUNT:
        started = now - timedelta(days=2 + auction_id % 180, hours=auction_id % 24)
        ended = started + timedelta(hours=24)
        if is_up_auction(auction_id):
            return started, ended, ended
        purchased = started + timedelta(hours=auction_id % 12 + 1)
        return started, ended, purchased

    started = now - timedelta(minutes=auction_id % 720)
    ended = now + timedelta(days=7)
    return started, ended, None


def calculate_member_balances() -> tuple[list[int], list[int]]:
    available = [INITIAL_POINT] * (MEMBER_COUNT + 1)
    locked = [0] * (MEMBER_COUNT + 1)

    for auction_id in range(1, COMPLETED_AUCTION_COUNT + 1):
        buyer = buyer_id(auction_id)
        seller = seller_id(auction_id)
        amount, deposit_status = deposit_state(auction_id)
        available[buyer] -= amount

        if deposit_status == "HELD":
            locked[buyer] += amount
        else:
            available[seller] += amount

    if min(available[1:]) < 0:
        raise RuntimeError("생성 규칙상 회원 사용 가능 포인트가 음수가 되었습니다.")
    return available, locked


class MysqlClient:
    def __init__(self, args: argparse.Namespace) -> None:
        executable = shutil.which("mysql")
        if executable is None:
            raise RuntimeError("mysql CLI를 찾을 수 없습니다. MySQL client를 설치해 주세요.")
        self.command = [
            executable,
            "--protocol=TCP",
            "--default-character-set=utf8mb4",
            "--batch",
            "--raw",
            "--host",
            args.host,
            "--port",
            str(args.port),
            "--user",
            args.user,
            "--database",
            args.database,
        ]
        self.environment = os.environ.copy()
        if args.password:
            self.environment["MYSQL_PWD"] = args.password

    def query(self, sql: str) -> str:
        result = subprocess.run(
            [*self.command, "--skip-column-names", "--execute", sql],
            env=self.environment,
            text=True,
            capture_output=True,
            check=False,
        )
        if result.returncode != 0:
            message = result.stderr.strip() or "MySQL 쿼리 실행에 실패했습니다."
            raise RuntimeError(message)
        return result.stdout.strip()

    def stream(self) -> "MysqlSink":
        return MysqlSink(self.command, self.environment)


class MysqlSink:
    def __init__(self, command: list[str], environment: dict[str, str]) -> None:
        self.process = subprocess.Popen(
            command,
            env=environment,
            stdin=subprocess.PIPE,
            text=True,
        )
        if self.process.stdin is None:
            raise RuntimeError("mysql stdin을 열 수 없습니다.")
        self.stdin = self.process.stdin
        self.write("SET NAMES utf8mb4; SET time_zone = '+09:00';\n")

    def write(self, sql: str) -> None:
        self.stdin.write(sql)

    def close(self) -> None:
        self.stdin.close()
        return_code = self.process.wait()
        if return_code != 0:
            raise RuntimeError(f"mysql 프로세스가 종료 코드 {return_code}로 실패했습니다.")

    def abort(self) -> None:
        if self.process.poll() is None:
            self.process.terminate()
        self.process.wait()


def write_rows(
    sink: MysqlSink,
    table: str,
    columns: str,
    rows: Iterable[str],
    expected_count: int,
    batch_size: int,
) -> None:
    batch: list[str] = []
    written = 0
    for row in rows:
        batch.append(row)
        if len(batch) == batch_size:
            sink.write(f"INSERT INTO {table} ({columns}) VALUES\n" + ",\n".join(batch) + ";\n")
            written += len(batch)
            batch.clear()
    if batch:
        sink.write(f"INSERT INTO {table} ({columns}) VALUES\n" + ",\n".join(batch) + ";\n")
        written += len(batch)
    if written != expected_count:
        raise RuntimeError(f"{table} 생성량 불일치: expected={expected_count}, actual={written}")
    print(f"{table:16s} {written:>10,} rows", flush=True)


def member_rows(
    fake: Faker,
    now: datetime,
    available: list[int],
    locked: list[int],
) -> Iterator[str]:
    names = [fake.name()[:17] for _ in range(512)]
    for member_id in range(1, MEMBER_COUNT + 1):
        created = now - timedelta(days=member_id % 1_825)
        yield "(" + ",".join((
            str(member_id),
            sql_text(names[member_id % len(names)]),
            sql_text(f"010{member_id:08d}"),
            sql_text(f"회원{member_id:06d}"),
            sql_text(f"member{member_id:06d}@example.com"),
            sql_text(PASSWORD_HASH),
            str(available[member_id]),
            sql_text("profiles/default-profile.png"),
            sql_text("ACTIVE"),
            str(locked[member_id]),
            "0",
            sql_datetime(created),
            sql_datetime(now),
        )) + ")"


def auction_rows(fake: Faker, now: datetime) -> Iterator[str]:
    title_words = [fake.word() for _ in range(512)]
    descriptions = [fake.text(max_nb_chars=120) for _ in range(512)]
    addresses = [fake.address().replace("\n", " ")[:100] for _ in range(512)]
    categories = ("HOUSEHOLD", "FOOD", "FURNITURE")

    for auction_id in range(1, AUCTION_COUNT + 1):
        started, ended, completed = auction_times(auction_id, now)
        auction_type = "UP" if is_up_auction(auction_id) else "DOWN"
        status = "COMPLETED" if completed is not None else "OPEN"
        price = final_price(auction_id) if completed is not None else start_price(auction_id)
        revision = 0
        if completed is not None:
            revision = 1 if auction_type == "DOWN" else open_bid_count(auction_id) + 1
            if auction_id <= SEALED_BID_COUNT:
                revision += 1
        trade_type = "DIRECT" if auction_id % 2 else "DELIVERY"
        contact = (
            f"010{seller_id(auction_id):08d}"
            if trade_type == "DIRECT"
            else addresses[auction_id % len(addresses)]
        )
        title = f"{title_words[auction_id % len(title_words)]} 경매 {auction_id}"[:30]
        completed_sql = "NULL" if completed is None else sql_datetime(completed)
        yield "(" + ",".join((
            str(auction_id),
            str(seller_id(auction_id)),
            sql_text(auction_type),
            sql_text(title),
            sql_text(descriptions[auction_id % len(descriptions)]),
            sql_text(status),
            sql_text(categories[auction_id % len(categories)]),
            str(start_price(auction_id)),
            str(price),
            sql_datetime(ended),
            sql_datetime(started),
            completed_sql,
            str(revision),
            sql_text(trade_type),
            sql_text(contact),
            sql_datetime(started),
            sql_datetime(completed or started),
        )) + ")"


def up_auction_rows() -> Iterator[str]:
    for auction_id in range(1, AUCTION_COUNT + 1):
        if is_up_auction(auction_id):
            yield f"({auction_id},{start_price(auction_id) + 50_000})"


def down_auction_rows() -> Iterator[str]:
    for auction_id in range(COMPLETED_DOWN_START, AUCTION_COUNT + 1):
        if not is_up_auction(auction_id):
            yield f"({auction_id},{start_price(auction_id) // 2},5000,60)"


def bid_rows(now: datetime) -> Iterator[str]:
    for auction_id in range(1, COMPLETED_UP_COUNT + 1):
        started, _, _ = auction_times(auction_id, now)
        for sequence in range(1, open_bid_count(auction_id) + 1):
            created = started + timedelta(hours=sequence)
            yield (
                f"({auction_id},{buyer_id(auction_id)},"
                f"{start_price(auction_id) + sequence * 1_000},'UP',"
                f"{sql_datetime(created)},{sql_datetime(created)})"
            )

    for auction_id in range(COMPLETED_DOWN_START, COMPLETED_AUCTION_COUNT + 1):
        _, _, purchased = auction_times(auction_id, now)
        if purchased is None:
            raise RuntimeError("완료 하향 경매의 구매 시각이 없습니다.")
        yield (
            f"({auction_id},{buyer_id(auction_id)},"
            f"{final_price(auction_id)},'DOWN',"
            f"{sql_datetime(purchased)},{sql_datetime(purchased)})"
        )


def sealed_bid_rows(now: datetime) -> Iterator[str]:
    for auction_id in range(1, SEALED_BID_COUNT + 1):
        _, ended, _ = auction_times(auction_id, now)
        submitted = ended - timedelta(minutes=auction_id % 4 + 1)
        yield (
            f"({auction_id},{auction_id},{buyer_id(auction_id)},"
            f"{final_price(auction_id)},{sql_datetime(submitted)})"
        )


def image_rows() -> Iterator[str]:
    for image_id in range(1, IMAGE_COUNT + 1):
        yield f"({image_id},{image_id},'auctions/{image_id}/mock-{image_id}.webp')"


def deposit_rows(now: datetime) -> Iterator[str]:
    for auction_id in range(1, DEPOSIT_COUNT + 1):
        started, _, completed = auction_times(auction_id, now)
        amount, status = deposit_state(auction_id)
        if is_up_auction(auction_id):
            # 상향 경매 보증금은 첫 공개 입찰 때 생성된다.
            created = started + timedelta(hours=1)
        else:
            # 하향 경매는 즉시구매 시 전액 보증금을 생성한다.
            created = completed or started

        modified = created
        if status == "HELD" and trade_status(auction_id) == "CONFIRMED":
            modified = (completed or created) + timedelta(minutes=10)
        elif status == "USED":
            modified = (completed or created) + timedelta(minutes=20)
        yield (
            f"({auction_id},{buyer_id(auction_id)},{auction_id},{amount},'{status}',"
            f"{sql_datetime(created)},{sql_datetime(modified)})"
        )


def trade_rows(now: datetime) -> Iterator[str]:
    for auction_id in range(1, TRADE_COUNT + 1):
        _, ended, completed = auction_times(auction_id, now)
        purchased = completed or ended
        status = trade_status(auction_id)
        modified = purchased
        if status == "CONFIRMED":
            modified += timedelta(minutes=10)
        elif status == "COMPLETED":
            modified += timedelta(minutes=20)
        yield (
            f"({auction_id},{auction_id},{buyer_id(auction_id)},'{status}',"
            f"{final_price(auction_id)},{sql_datetime(purchased)},"
            f"{sql_datetime(purchased)},{sql_datetime(modified)})"
        )


def reset_database(client: MysqlClient) -> None:
    statements = ["SET FOREIGN_KEY_CHECKS=0"]
    statements.extend(f"TRUNCATE TABLE {table}" for table in RESET_TABLES)
    statements.append("SET FOREIGN_KEY_CHECKS=1")
    client.query(";".join(statements) + ";")


def ensure_target_tables_are_empty(client: MysqlClient) -> None:
    expressions = "+".join(f"EXISTS(SELECT 1 FROM {table} LIMIT 1)" for table in TARGET_TABLES)
    occupied = int(client.query(f"SELECT {expressions};"))
    if occupied:
        raise RuntimeError("대상 테이블에 기존 데이터가 있습니다. 삭제 후 재생성하려면 --reset을 사용하세요.")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Bidwin 대용량 Faker 목 데이터 생성기")
    parser.add_argument("--host", default=os.getenv("DB_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("DB_PORT", "3306")))
    parser.add_argument("--user", default=os.getenv("DB_USERNAME", "root"))
    parser.add_argument("--password", default=os.getenv("DB_PASSWORD", ""))
    parser.add_argument("--database", default=os.getenv("DB_NAME", "bidwin"))
    parser.add_argument("--batch-size", type=int, default=2_000)
    parser.add_argument("--seed", type=int, default=20_260_810)
    parser.add_argument(
        "--reset",
        action="store_true",
        help="관련 테이블의 기존 데이터를 모두 삭제한 뒤 생성",
    )
    args = parser.parse_args()
    if args.batch_size <= 0:
        parser.error("--batch-size는 1 이상이어야 합니다.")
    return args


def main() -> int:
    args = parse_args()
    fake = Faker("ko_KR")
    fake.seed_instance(args.seed)
    now = datetime.now().replace(microsecond=0)
    client = MysqlClient(args)

    # 연결과 마이그레이션 적용 여부를 실제 대상 테이블 조회로 함께 확인한다.
    client.query("SELECT COUNT(*) FROM member LIMIT 1;")
    if args.reset:
        print("기존 관련 데이터를 삭제합니다.", flush=True)
        reset_database(client)
    else:
        ensure_target_tables_are_empty(client)

    print("회원별 사용 가능/잠금 포인트를 거래 상태에서 역산합니다.", flush=True)
    available, locked = calculate_member_balances()
    sink = client.stream()
    try:
        write_rows(
            sink,
            "member",
            "id,name,phone_number,nickname,email,password,total_point,profile_object_key,status,locked_point,auth_version,created_at,last_modified_at",
            member_rows(fake, now, available, locked),
            MEMBER_COUNT,
            args.batch_size,
        )
        write_rows(
            sink,
            "auction",
            "id,seller_id,auction_type,title,description,status,category,start_price,current_price,ended_at,started_at,completed_at,revision,trade_type,contact,created_at,last_modified_at",
            auction_rows(fake, now),
            AUCTION_COUNT,
            args.batch_size,
        )
        write_rows(sink, "up_auction", "auction_id,buy_now_price", up_auction_rows(), 840_000, args.batch_size)
        write_rows(
            sink,
            "down_auction",
            "auction_id,minimum_price,drop_price,price_drop_interval",
            down_auction_rows(),
            160_000,
            args.batch_size,
        )
        write_rows(
            sink,
            "bid",
            "auction_id,bidder_id,price,status,created_at,last_modified_at",
            bid_rows(now),
            BID_COUNT,
            args.batch_size,
        )
        write_rows(
            sink,
            "sealed_bid",
            "id,auction_id,bidder_id,price,submitted_at",
            sealed_bid_rows(now),
            SEALED_BID_COUNT,
            args.batch_size,
        )
        write_rows(sink, "image", "id,auction_id,object_key", image_rows(), IMAGE_COUNT, args.batch_size)
        write_rows(
            sink,
            "auction_deposit",
            "id,member_id,auction_id,reserved_amount,status,created_at,last_modified_at",
            deposit_rows(now),
            DEPOSIT_COUNT,
            args.batch_size,
        )
        write_rows(
            sink,
            "auction_trade",
            "id,auction_id,buyer_id,status,final_price,purchased_at,created_at,last_modified_at",
            trade_rows(now),
            TRADE_COUNT,
            args.batch_size,
        )
        sink.close()
    except BaseException:
        sink.abort()
        raise

    print(
        "생성 완료: member=100,000 auction=1,000,000(open=300,000) "
        "bid=5,000,000 sealed_bid=500,000 image=1,000,000 "
        "auction_trade=700,000 auction_deposit=700,000",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("사용자 요청으로 생성을 중단했습니다.", file=sys.stderr)
        raise SystemExit(130)
    except Exception as exception:
        print(f"생성 실패: {exception}", file=sys.stderr)
        raise SystemExit(1)
