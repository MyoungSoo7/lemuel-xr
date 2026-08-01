#!/usr/bin/env bash
# 게이트 러너 자체 테스트. python3 로만 돌아간다(grep 의존 없음 — ㉔㉘).
set -u
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "$DIR/run.py" "$@"
