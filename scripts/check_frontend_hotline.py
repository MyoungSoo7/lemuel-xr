#!/usr/bin/env python3
"""프론트엔드 위기 상담 번호 하드코딩 판정기.

배경
────
백엔드는 2026-08-02 부터 번호를 yml 에 적지 않는다. 시나리오는
`{{crisis_resources.default}}` 토큰만 쓰고 실제 번호는 런타임에 DB 정본에서
주입되며(`CrisisTokenResolver`), `ScenarioHotlineRatchetTest` 가 그 규율을 강제한다.

프론트는 그 규율 바깥에 있었다. 2026-08-11 실측으로 9개 파일이 번호를 각자
문자열로 들고 있었다. 그 9벌은 서로를 모른다 — 2024-01-01 자살예방 상담 109 통합
같은 개정이 또 오면 아홉 군데를 다 찾아야 하고, 한 군데를 놓치면 **위기 상태의
사용자가 죽은 번호를 받는다.**

이 실패는 화면이 깨지지 않는다. 통화만 조용히 안 된다. 그래서 사람 눈이 아니라
기계가 지켜야 한다.

이 판정기가 재는 것 (두 축)
─────────────────────────────
  A. 번호 리터럴이 정본 모듈 **바깥** 에 있는가.
     정본은 frontend/src/lib/crisis-resources.ts 하나다.
  B. 정본 모듈의 기본 번호가 백엔드 대체값과 **같은가**.
     backend/src/main/resources/application.yml 의
     `safety.crisis.default-contact` 기본값과 대조한다. 프론트는 정적 미러이므로
     갈라질 수 있고, 갈라지는 것이 바로 이 판정기가 잡아야 할 사건이다.

이 판정기가 재지 **않는** 것
────────────────────────────
  · DB `crisis_resources` 테이블의 실제 행. 진짜 정본은 거기다. 여기서 재는 것은
    두 소스코드가 서로 어긋나지 않았다는 것까지다. DB 가 셋과 다르면 이 판정기는
    초록인 채로 틀린다.
  · 번호가 실제로 통화되는지. 어떤 정적 검사로도 알 수 없다.
  · 렌더 여부. 상수를 import 해 놓고 화면에 안 그려도 여기서는 통과한다.

rc: 0 = 위반 없음 / 1 = 위반 있음. (이 리포의 다른 게이트와 달리 기준선 대조가
아니라 진짜 합격·불합격이다 — 정리 후 실제로 초록이 되는 축이기 때문이다.)
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FRONTEND = ROOT / "frontend"
CANON = FRONTEND / "src" / "lib" / "crisis-resources.ts"
BACKEND_APP_YML = ROOT / "backend" / "src" / "main" / "resources" / "application.yml"

SCAN_DIRS = [FRONTEND / "src", FRONTEND / "tests"]
SCAN_SUFFIXES = {".ts", ".tsx", ".js", ".jsx", ".mjs"}

# 대시가 있는 번호와 폐지된 구 번호는 문맥 없이도 상담번호가 확실하다.
# 1393 은 2024-01-01 통합으로 정번호가 아니게 된 구 번호다 — 남아 있으면 그 자체가 결함.
HARD_PATTERNS = [
    (re.compile(r"1577-0199"), "정신건강위기상담전화 번호"),
    (re.compile(r"1588-9191"), "생명의전화 번호"),
    (re.compile(r"\b1393\b"), "폐지된 구 자살예방 번호 (2024-01-01 109 로 통합)"),
    (re.compile(r"tel:\s*[\d-]+"), "tel: 링크에 박힌 번호"),
]

# 109·129 는 짧아서 다른 뜻으로도 나온다(배열 길이·픽셀값 등). 위기 문맥이
# 같은 줄에 있을 때만 위반으로 센다. 문맥 없이 109 를 쓰는 위기 카피는 없다.
SOFT_PATTERNS = [
    (re.compile(r"\b109\b"), "자살예방 상담전화 번호"),
    (re.compile(r"\b129\b"), "보건복지상담센터 번호"),
]
CRISIS_CONTEXT = re.compile(r"위기|상담|자살|자해|정신건강|생명의전화|crisis|hotline|tel:")


def scan_files() -> list[Path]:
    out: list[Path] = []
    for d in SCAN_DIRS:
        if not d.is_dir():
            continue
        for p in sorted(d.rglob("*")):
            if p.suffix in SCAN_SUFFIXES and p.resolve() != CANON.resolve():
                out.append(p)
    return out


def backend_default_contact() -> str | None:
    """`default-contact: ${SAFETY_CRISIS_DEFAULT_CONTACT:109}` 에서 109 를 뽑는다.

    PyYAML 로 읽지 않는다 — 값이 스프링 플레이스홀더 문자열이라 파싱해도 그대로
    문자열 한 덩어리이고, 우리가 원하는 건 그 안의 *기본값* 이다.
    """
    if not BACKEND_APP_YML.is_file():
        return None
    text = BACKEND_APP_YML.read_text(encoding="utf-8")
    m = re.search(r"default-contact:\s*\$\{[A-Z_]+:([^}]+)\}", text)
    if m:
        return m.group(1).strip()
    m = re.search(r"default-contact:\s*([^\s#]+)", text)
    return m.group(1).strip() if m else None


def canon_default_tel() -> str | None:
    """정본 모듈의 CRISIS_RESOURCES 첫 항목 tel 값."""
    if not CANON.is_file():
        return None
    text = CANON.read_text(encoding="utf-8")
    m = re.search(r"CRISIS_RESOURCES[^=]*=\s*\[(.*?)\n\]", text, re.S)
    if not m:
        return None
    first = re.search(r'tel:\s*"([^"]+)"', m.group(1))
    return first.group(1) if first else None


def main() -> int:
    violations: list[str] = []
    notes: list[str] = []

    # ── 축 A — 정본 바깥의 번호 리터럴 ────────────────────────────────────────
    if not CANON.is_file():
        print(f"FAIL  정본 모듈이 없다: {CANON.relative_to(ROOT)}")
        return 1

    files = scan_files()
    for p in files:
        rel = p.relative_to(ROOT)
        for lineno, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
            for pat, why in HARD_PATTERNS:
                if pat.search(line):
                    violations.append(f"{rel}:{lineno}  {why}\n        {line.strip()}")
            if CRISIS_CONTEXT.search(line):
                for pat, why in SOFT_PATTERNS:
                    if pat.search(line):
                        violations.append(
                            f"{rel}:{lineno}  {why} (위기 문맥)\n        {line.strip()}"
                        )

    # ── 축 B — 정본 모듈 ↔ 백엔드 대체값 ──────────────────────────────────────
    front = canon_default_tel()
    back = backend_default_contact()
    if front is None:
        violations.append(
            "frontend/src/lib/crisis-resources.ts  CRISIS_RESOURCES[0].tel 을 읽지 못했다"
        )
    elif back is None:
        notes.append(
            "backend application.yml 의 safety.crisis.default-contact 를 읽지 못해 "
            "대조를 건너뛰었다 — 판정불가이고 통과가 아니다"
        )
    elif front != back:
        violations.append(
            f"기본 번호가 갈라졌다: 프론트 {front!r} vs 백엔드 대체값 {back!r}\n"
            f"        frontend/src/lib/crisis-resources.ts ↔ "
            f"backend/src/main/resources/application.yml"
        )
    else:
        notes.append(f"기본 번호 일치: 프론트 == 백엔드 대체값 ({front})")

    # ── 보고 ──────────────────────────────────────────────────────────────────
    print(f"프론트 위기번호 하드코딩 검사 — 대상 {len(files)} 파일 "
          f"(정본 {CANON.relative_to(ROOT)} 제외)")
    print()
    for n in notes:
        print(f"  [NOTE] {n}")
    if notes:
        print()

    if violations:
        for v in violations:
            print(f"  [FAIL] {v}")
        print()
        print(f"--- 위반 {len(violations)} ---")
        print("  번호는 @/lib/crisis-resources 에서 import 한다. 화면에도 테스트에도")
        print("  주석에도 숫자를 다시 적지 않는다 — 주석도 낡는다.")
        return 1

    print("--- 위반 0 ---")
    print("  ⚠️ 이 초록의 주장 범위: 소스 두 곳이 서로 어긋나지 않았다 까지다.")
    print("     DB crisis_resources 의 실제 행도, 번호가 실제로 통화되는지도 재지 않는다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
