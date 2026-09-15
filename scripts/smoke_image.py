"""
이미지를 실제로 띄워, 배포 구성이 쓰는 헬스 체크 명령을 컨테이너 안에서 그대로 돌려 본다.

테스트는 코드를 보고 이미지는 보지 않는다. 이미지가 뜨지 못하거나 헬스 체크가 이미지에
없는 도구를 쓰면, 테스트는 초록인데 배포만 실패한다. CI 는 이미지를 올리기 전에 이것을 돌린다.

이 서버는 DB 와 캐시가 있어야 뜬다. 개발 프로파일로 띄우고, 둘을 임시 컨테이너로 함께 띄운다.

사용: python scripts/smoke_image.py <이미지>
"""

import json
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SERVICE_IMAGE = "safehome-api"
PREFIX = "safehome-api-smoke"
NETWORK = f"{PREFIX}-net"
DB = f"{PREFIX}-db"
CACHE = f"{PREFIX}-cache"
APP = f"{PREFIX}-app"
TIMEOUT_SECONDS = 240


def healthchecks() -> dict:
    """배포 구성 파일마다 이 서비스의 헬스 체크 명령을 뽑는다."""
    found = {}
    for compose in sorted((ROOT / "docker").glob("*/docker-compose.yml")):
        image = None
        for line in compose.read_text(encoding="utf-8").splitlines():
            m = re.match(r"^\s+image:\s*(\S+)", line)
            if m:
                image = m.group(1)
            m = re.match(r"^\s+test:\s*(\[.*\])\s*$", line)
            if m and image and re.search(rf"/{SERVICE_IMAGE}:", image):
                found[compose.relative_to(ROOT).as_posix()] = json.loads(m.group(1))
    return found


def to_exec(test: list) -> list:
    if test[0] == "CMD":
        return test[1:]
    if test[0] == "CMD-SHELL":
        return ["sh", "-c", test[1]]
    raise SystemExit(f"알 수 없는 헬스 체크 형식: {test}")


def run(args: list) -> subprocess.CompletedProcess:
    return subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")


def wait_until(label: str, args: list, seconds: int) -> bool:
    deadline = time.time() + seconds
    while time.time() < deadline:
        if run(args).returncode == 0:
            return True
        time.sleep(2)
    print(f"{label} 가 준비되지 않았다")
    return False


def cleanup() -> None:
    run(["docker", "rm", "-f", APP, DB, CACHE])
    run(["docker", "network", "rm", NETWORK])


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    image = sys.argv[1]
    checks = healthchecks()
    if not checks:
        print("배포 구성에서 헬스 체크를 하나도 찾지 못했다. 검사할 것이 없으면 통과가 아니라 실패다.")
        return 1

    cleanup()
    try:
        run(["docker", "network", "create", NETWORK])
        run(["docker", "run", "-d", "--name", DB, "--network", NETWORK,
             "-e", "POSTGRES_DB=safehome", "-e", "POSTGRES_USER=safehome", "-e", "POSTGRES_PASSWORD=smoke",
             "postgres:17"])
        run(["docker", "run", "-d", "--name", CACHE, "--network", NETWORK, "redis:7-alpine"])
        if not wait_until("DB", ["docker", "exec", DB, "pg_isready", "-U", "safehome", "-d", "safehome"], 90):
            return 1
        if not wait_until("캐시", ["docker", "exec", CACHE, "redis-cli", "ping"], 60):
            return 1

        started = run(["docker", "run", "-d", "--name", APP, "--network", NETWORK,
                       "-e", "SPRING_PROFILES_ACTIVE=dev",
                       "-e", "DB_NAME=safehome", "-e", "DB_USERNAME=safehome", "-e", "DB_PASSWORD=smoke",
                       "-e", f"DB_WRITE_HOST={DB}", "-e", "DB_WRITE_PORT=5432",
                       "-e", f"DB_READ_HOST={DB}", "-e", "DB_READ_PORT=5432",
                       "-e", f"REDIS_HOST={CACHE}",
                       image])
        if started.returncode != 0:
            print("컨테이너를 띄우지 못했다:", started.stderr.strip())
            return 1

        pending = dict(checks)
        deadline = time.time() + TIMEOUT_SECONDS
        while pending and time.time() < deadline:
            if run(["docker", "inspect", "-f", "{{.State.Running}}", APP]).stdout.strip() != "true":
                break
            for source, test in list(pending.items()):
                if run(["docker", "exec", APP, *to_exec(test)]).returncode == 0:
                    print(f"통과: {source} 의 헬스 체크")
                    del pending[source]
            time.sleep(3)
        if not pending:
            return 0
        print("실패한 헬스 체크:")
        for source, test in pending.items():
            result = run(["docker", "exec", APP, *to_exec(test)])
            print(f"  {source}: {test}")
            print("   ", (result.stdout + result.stderr).strip()[-400:])
        logs = run(["docker", "logs", "--tail", "80", APP])
        print("컨테이너 로그 끝부분:")
        print((logs.stdout + logs.stderr).strip())
        return 1
    finally:
        cleanup()


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main())
