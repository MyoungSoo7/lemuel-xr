#!/usr/bin/env python3
"""사용자에게 노출되는 텍스트가 하나님을 **"신"** 으로 부르는 자리를 센다.

─────────────── 왜 생겼나 ───────────────

`docs/MVP-JOB.md` §12.6 의 disputed_point 8 이 이 문제를 적어 두었다 — 자막은
"신", 내부 주석은 "하나님". 리포 전역에 호칭 규약이 없다는 지적이었고, 그 행에는
인물별 실측치까지 붙어 있었다: `jesus.yml` 5회 · `job.yml` 4회 · `elijah.yml` 3회 ·
`ruth.yml` 3회 · `solomon.yml` 2회.

**그 숫자들이 틀렸다.** 2026-08-20 에 기계로 다시 세니 노출 텍스트의 실제 분포는
`elijah.yml` 4 · `job.yml` 3 이고 나머지 셋(jesus·ruth·solomon)은 **0** 이다.
원래 숫자는 `신` 을 부분 문자열로 센 결과로 보인다 — 당신·헌신·배신·성육신·신뢰·
신앙·신호가 전부 걸린다. 사람이 눈으로 센 계수는 이렇게 조용히 늙거나 처음부터
틀린다. 그래서 이 파일이 생겼다: **숫자를 문서에 손으로 박지 않고 매 실행마다
센다.**

─────────────── 왜 PASS/FAIL 이 아니라 BLOCKED 인가 ───────────────

이 검사기는 **판정하지 않는다.** 어느 호칭이 옳은지가 아직 정해지지 않았기
때문이다. 그리고 그 결정은 기계가 대신할 수 있는 종류가 아니다 — 양쪽 다 근거가
있다.

  · "하나님" 으로 통일 — 리포 전체가 삼위일체 하나님을 전제로 쓰였고(`ruth.yml`
    머리주석), 내부 주석·문서·시드 자막 대다수가 이미 "하나님" 이다. 한 화면 안에서
    두 호칭이 섞이면 같은 대상을 가리키는지 모호해진다.
  · "신" 을 남긴다 — `docs/MVP-JOB.md` §12.5 의 faith_tone 3단(strong/balanced/
    soft)과 3차원 진입 모드는 **무종교 사용자의 진입** 을 명시적으로 설계 목표로
    둔다. `elijah`·`job` 의 "신은 …" 자막이 그 soft 톤의 미문서화된 선례일 수 있다.

검사기가 한쪽을 골라 FAIL 을 매기면 **그 선택이 곧 규약이 되어 버린다** — 사람이
결정한 적 없는 규약이. 이 리포가 반복해서 잡아 온 실패 모드다(빈 통제: 통제가
있다는 사실이 결정이 있었다는 착각을 만든다). 그래서 결정 전까지 이 축들은
**BLOCKED — 판정 불가** 다. 세되 판정하지 않는다. 규약이 정해지면 그 결정을 여기
적고 축을 PASS/FAIL 로 바꾼다.

BLOCKED 도 기준선에 박히므로, 그 사이에 숫자가 움직이면 `ci_gates.py` 가 드리프트로
띄운다. 결정이 없어도 **더 나빠지는 것은 잡힌다.**

─────────────── 무엇을 세고 무엇을 빼나 ───────────────

조사가 붙은 단독 명사만 센다(`신은`·`신이`·`신의`·`신께`·`신에게` …). 앞 글자가
한글이면 합성어로 보고 뺀다 — 당신·헌신·배신·성육신·신뢰·신앙·신호가 여기서
걸러진다.

조사를 요구하므로 조사 없이 홀로 선 "신" 은 원리상 놓친다. 2026-08-20 에 그 자리를
따로 훑어 확인했다 — 7건이 나왔고 **전부 주석** 이었다(`job.yml:4`·`:102` ·
`ruth.yml:167` · `moses/page.tsx` 의 "신 벗기" 설명 4건). 노출 텍스트에는 없다.
이 사각지대는 알고 남겨 둔 것이지 안 재 본 것이 아니다.

**신발의 "신" 은 뺀다.** `moses` 의 "신을 벗기"·"신을 벗어라" 는 호칭이 아니라
출애굽기 3:5 의 자구다(시드 `V20260820051500` 의 KLB 본문에도 그대로 있다).
호칭 문제로 세면 모세가 매번 오탐으로 걸리고, 오탐이 섞인 계수는 아무도 안 본다.

세는 면은 넷이고 성격이 다르다.

  1. **시나리오 값** — 사용자가 실제로 읽는 자막·산문.
  2. **시나리오 주석** — 저자가 읽는 글. 노출되지 않으므로 별도로 센다.
  3. **프론트 소스 산문** — 화면 컴포넌트에 직접 박힌 문장. yml 을 안 거치므로
     시나리오만 세면 통째로 빠진다.
  4. **시드 SQL** — `/topics` 계열 본문. 같은 문장이 여기에도 산다.

테스트 픽스처(`*.test.tsx`)는 위 넷을 흉내 낸 사본이라 따로 세어 참고로만 찍는다 —
정본을 고치면 따라 고쳐야 하는 자리가 몇 군데인지 미리 보이게 하려는 것이다.

⚠️ **1번과 2번을 가르는 기준은 렌더 여부가 아니라 YAML 문법이다.** 2번은 `#` 로 시작하는
줄만이고, 값으로 적힌 저작자용 가드 필드(`*_note`)는 전부 1번으로 센다. 그래서 화면에
안 나가는 문장이 「노출」에 섞인다. 2026-08-23 실측으로 라합
`scenes[4].extras.closing_note` 한 건이 그 경우다 — `frontend/src/app/rahab/page.tsx`
머리주석 3)이 `*_note` 를 렌더하지 않는다고 못박고 있고 실제로 참조하는 코드가 없다.
즉 그 시점 계수 15 중 실제 노출은 14다. 고치지 않고 적어만 두는 이유: 어느 키가
노출인지는 인물마다 프론트가 정하고, 그 계약을 이 러너가 추측하기 시작하면
「세기만 한다」는 성격이 깨진다. 호칭 규약을 정할 때 이 한 건을 빼고 읽어라.

rc: 0 = 드리프트 판단은 `ci_gates.py` 의 일. 이 러너 자체는 FAIL 축이 없으면 0.
"""
from __future__ import annotations

import pathlib
import re
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCENARIOS = ROOT / "backend/src/main/resources/scenarios"
MIGRATIONS = ROOT / "backend/src/main/resources/db/migration"
FRONT = ROOT / "frontend/src"

# 조사가 붙은 단독 명사 '신'. 앞 글자가 한글이면 합성어다.
DIVINE_RE = re.compile(r"(?<![가-힣])신(?=[은이가을의께과와도만에라])")

# 신발. 출 3:5 의 자구라 호칭 계수에서 빼야 한다.
SHOE_RE = re.compile(r"신(을|발)?\s*(벗|신)")


def divine_hits(text: str) -> list[int]:
    """호칭으로 쓰인 '신' 의 위치. 신발 용례는 뺀다."""
    out = []
    for m in DIVINE_RE.finditer(text):
        if SHOE_RE.match(text, m.start()):
            continue
        out.append(m.start())
    return out


def snippet(text: str, at: int) -> str:
    return text[max(0, at - 16) : at + 18].replace("\n", " ").strip()


def walk_values(node: object, out: list[tuple[str, str]], path: str = "") -> None:
    if isinstance(node, dict):
        for k, v in node.items():
            walk_values(v, out, f"{path}.{k}")
    elif isinstance(node, list):
        for i, v in enumerate(node):
            walk_values(v, out, f"{path}[{i}]")
    elif isinstance(node, str):
        out.append((path, node))


def scan_scenarios() -> tuple[list[tuple[str, str, str]], list[tuple[str, str, str]]]:
    values: list[tuple[str, str, str]] = []
    comments: list[tuple[str, str, str]] = []
    for p in sorted(SCENARIOS.glob("*.yml")):
        raw = p.read_text(encoding="utf-8")
        vals: list[tuple[str, str]] = []
        walk_values(yaml.safe_load(raw) or {}, vals)
        for path, s in vals:
            for at in divine_hits(s):
                values.append((p.stem, path.lstrip("."), snippet(s, at)))
        for i, line in enumerate(raw.splitlines(), 1):
            if not line.strip().startswith("#"):
                continue
            for at in divine_hits(line):
                comments.append((p.stem, f"L{i}", snippet(line, at)))
    return values, comments


def scan_files(paths: list[pathlib.Path]) -> list[tuple[str, str, str]]:
    out: list[tuple[str, str, str]] = []
    for f in paths:
        for i, line in enumerate(
            f.read_text(encoding="utf-8", errors="replace").splitlines(), 1
        ):
            for at in divine_hits(line):
                out.append((str(f.relative_to(ROOT)), f"L{i}", snippet(line, at)))
    return out


def main() -> int:
    scen_values, scen_comments = scan_scenarios()

    front_all = sorted(
        f for f in FRONT.rglob("*") if f.suffix in {".ts", ".tsx"} and f.is_file()
    )
    front_src = scan_files([f for f in front_all if ".test." not in f.name])
    front_test = scan_files([f for f in front_all if ".test." in f.name])
    seeds = scan_files(sorted(MIGRATIONS.glob("*.sql")))

    print()
    print("면                       건수")
    print("-" * 34)
    print(f"{'시나리오 값(노출)':<22s} {len(scen_values):4d}")
    print(f"{'시나리오 주석(비노출)':<20s} {len(scen_comments):4d}")
    print(f"{'프론트 소스 산문':<22s} {len(front_src):4d}")
    print(f"{'시드 SQL':<24s} {len(seeds):4d}")
    print(f"{'(참고) 테스트 픽스처':<21s} {len(front_test):4d}")
    print()

    # 인물별 분포 — §12.6 disputed_point 8 이 손으로 박아 둔 숫자를 대신한다.
    per: dict[str, int] = {}
    for name, _, _ in scen_values:
        per[name] = per.get(name, 0) + 1
    if per:
        print("  인물별(시나리오 값): " + " · ".join(f"{k} {v}" for k, v in sorted(per.items())))
        print()

    for label, rows in (
        ("시나리오 값", scen_values),
        ("프론트 소스", front_src),
        ("시드 SQL", seeds),
    ):
        for where, at, ctx in rows:
            print(f"    · [{label}] {where} {at} — …{ctx}…")
    if scen_values or front_src or seeds:
        print()

    exposed = len(scen_values) + len(front_src) + len(seeds)

    # 판정하지 않는다 — 머리말 참조. 규약이 정해지면 이 축을 PASS/FAIL 로 바꾼다.
    print(
        f"  [BLOCKED] divine-title:exposed — 노출 텍스트에서 하나님을 '신' 으로 부르는"
        f" 자리 {exposed}건 (시나리오 {len(scen_values)} + 프론트 {len(front_src)}"
        f" + 시드 {len(seeds)}). 호칭 규약이 아직 없어 옳고 그름을 판정하지 않는다"
    )
    print(
        f"  [BLOCKED] divine-title:mirrors — 같은 문장을 흉내 내는 테스트 픽스처"
        f" {len(front_test)}건. 정본을 고치면 따라 고쳐야 하는 자리다"
    )

    print("\n--- PASS 0 / FAIL 0 / BLOCKED 2 ---")
    print("  ⚠️ 이 검사는 **세기만 한다.** 어느 호칭이 옳은지는 사람이 정할 일이고,")
    print("     검사기가 한쪽에 FAIL 을 매기면 그 선택이 결정 없이 규약이 된다.")
    print("     결정 전까지 숫자는 기준선에 박혀 있고, 움직이면 드리프트로 뜬다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
