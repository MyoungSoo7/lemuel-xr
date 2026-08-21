#!/usr/bin/env python3
"""미션 화면이 `scripture_ref` 를 **여는지** 를 잰다 — 그리고 아직 안 여는 것을 센다.

─────────────── 이 검사기가 생긴 이유 ───────────────

2026-08-20 까지, 성경 참조를 두고 **초록이던 검사가 둘** 있었다.

  · `scripture_ref_check.py`  — 시나리오의 참조 60개가 `scripture_passages` 행과
    맞느냐. 인물 전부 PASS.
  · `scripture_text_check.py` — 그 92행의 자구가 `translation` 라벨(KLB/개역개정)과
    맞느냐. 92행 PASS. 이걸 맞추느라 마이그레이션 한 벌(`V20260820113000`)을 썼다.

둘 다 참이었고, 둘 다 **열 수 있다** 까지만 쟀다. 정작 **열린다** 는 아무도 재지
않았다. 그래서 참조 60개와 92행의 축자 정렬이 *화면에 한 번도 도달하지 않은 채*
전부 PASS 였다 — 미션 8개 어느 화면도 `scenePayload.scriptureRef` 를 읽지 않았고,
사용자가 미션을 끝까지 걸어도 성경 본문은 한 줄도 뜨지 않았다. 그 자리에는 yml 의
`static_text`, 즉 *저자가 요약한 산문* 만 있었다.

「검사가 통과했다」와 「기능이 있다」 사이의 거리가 이만큼이다. 그 거리를 이 파일이
잰다.

─────────────── 두 축 ───────────────

**A축 — 배선(인물 전체).** 각 미션 화면이 (1) `ScenePassage` 를 들여오고 (2) payload
최상위에서 `scriptureRef` 를 뽑고 (3) 그 값으로 렌더하고 (4) 그 렌더 자리가
`TriggerWarningGate` **뒤** 인지. (4)는 동의 게이트 안쪽 배치의 *근사* 다 — R4 씬에서
본문이 게이트 바깥에 놓이면 동의 전에 자극 본문이 노출된다.

⚠️ A축의 주장 범위: 소스에 그 배선이 **적혀 있다** 까지다. 배선이 있는데도 렌더가
안 되는 경우(조건이 늘 거짓이라든가)는 여기서 안 잡힌다 — 그건
`frontend/src/app/mission-passage.test.tsx` 가 화면 전부를 실제로 렌더해서 잰다. 구조
검사와 런타임 검사를 둘 다 두는 이유이고, 어느 한쪽만으로는 위의 실패 모드를
못 막았다.

**B축 — 프론트에 손으로 적힌 성경 인용.** `frontend/src/lib/content/*.ts` **코드**에
성구가 직접 문자열로 박혀 있으면 실패다. 그러면 같은 절의 사본이 DB 와 프론트 소스
두 벌이 되고, 한쪽만 고쳐지는 드리프트가 열린다.

이 리포는 지어낸 성구로 한 번 데었다 — `V20260717073355` 의 의역 시드를
`V20260718004903` 이 되돌렸다.

─────────────── B축 이력: 세 빚이 어떻게 갚였나 ───────────────

이 축의 문구는 원래 세 가지를 한꺼번에 주장했다. 셋 다 갚였다. 게이트 문구가 현실을
잘못 말하면 **그 문구 자체가 결함이다** — 사람은 메시지를 읽고 무엇이 남았는지
판단하기 때문이다. 그래서 갚인 만큼 걷어내 왔다.

  · ~~"어떤 원문과도 대조되지 않는다"~~ — 2026-08-21(#83) 갚음.
    `scripts/check_monologue_quotes.py` 가 이 네 파일의 인용을 대한성서공회 개역개정
    정본표와 전수 대조한다(자구 훼손 10 · 무표시 중간절단 5 등 21건을 고쳤다).
  · ~~"API 는 KLB 를 내리는데 이쪽은 개역개정이라 한 화면에 두 역본이 뜬다"~~ —
    2026-08-21 갚음. `V20260821030000` 이 rev(개역개정) 92행을 채우고 런타임 기본값을
    `modern` → `rev` 로 옮겼다. 이제 화면 위아래가 같은 역본이다.
  · ~~"사본이 둘이라는 사실 자체"~~ — 2026-08-21 갚음. 네 모놀로그 모듈에서 자구를
    걷어내고 `/api/scripture` 응답에서 받게 바꿨다(`scripture-quote.ts`). 소스에
    남는 것은 참조 + 낱말 인덱스뿐이다.

─────────────── 그래서 B축은 지금 무엇을 재는가 ───────────────

**회귀만 잰다.** 자구가 다시 코드로 기어들어오면 실패한다. 축이 통과로 넘어간 뒤
이 검사를 지우면 그 회귀가 조용해지므로, 초록이어도 남겨 둔다.

**주석 안은 통과다.** 신학 근거를 적으려면 자구를 인용해야 하고, 주석은 화면에 안
뜨므로 사본이 아니다. 대신 그 인용의 자구가 정본과 맞는지는
`check_monologue_quotes.py` 의 D축이 본다 — 여기서는 위치만 가른다.

주석/코드 판정은 그 파일의 `comment_spans` 를 **빌려 쓴다.** 두 벌로 두면 한쪽만
문자열 리터럴 안의 `//` 를 오인하는 식으로 어긋나고, 어긋난 쪽이 조용히 통과시킨다.
"""
from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from check_monologue_quotes import comment_spans  # noqa: E402  주석 판정을 두 벌 두지 않는다

ROOT = pathlib.Path(__file__).resolve().parent.parent
APP = ROOT / "frontend/src/app"
CONTENT = ROOT / "frontend/src/lib/content"
SCENARIOS = ROOT / "backend/src/main/resources/scenarios"
RUNTIME_TEST = APP / "mission-passage.test.tsx"

IMPORT_RE = re.compile(r'import\s*\{\s*ScenePassage\s*\}\s*from\s*"@/components/ScenePassage"')
DECL_RE = re.compile(r"const\s+scriptureRef\s*=\s*payload\.scriptureRef")
ADD_DECL_RE = re.compile(r"const\s+additionalRefs\s*=")
# 두 prop 을 다 요구한다. `reference` 만 넘기면 `additional_refs` 를 단 씬(david·job·
# ruth) 이 화면에 **1절만** 띄우고 나머지는 조용히 사라진다 — 그리고 그 상태는
# `scripture_ref_check.py` 에서 여전히 초록이다(그쪽은 시드에 행이 있는지만 본다).
RENDER_RE = re.compile(
    r"<ScenePassage\s+reference=\{scriptureRef\}\s+additional=\{additionalRefs\}\s*/>",
    re.S,
)
GATE_RE = re.compile(r"<TriggerWarningGate")
YML_REF_RE = re.compile(r"^\s*scripture_ref:\s*\S+", re.M)
YML_ADD_RE = re.compile(r"^\s*additional_refs:\s*\[([^\]]*)\]", re.M)

# 닫는 따옴표 바로 뒤에 오는 성구 표기 — 즉 *인용된* 본문. 따옴표 없는 참조
# (예: "…시편적 정직함(시 13, 22)")는 인용이 아니라 각주라 세지 않는다.
QUOTE_CITE_RE = re.compile(r'(?:\\"|["“”\'])\s*\(\s*[가-힣]{1,3}\s*\d+[:：]\d+')


def characters() -> list[str]:
    """인물 목록은 시나리오 yml 이 정한다 — 화면 목록을 손으로 적으면, 새 인물이
    화면 없이 추가돼도 이 검사는 8개만 세고 초록이 된다."""
    return sorted(p.stem for p in SCENARIOS.glob("*.yml"))


def check_wiring(name: str) -> tuple[str, str]:
    page = APP / name / "page.tsx"
    if not page.exists():
        return "FAIL", f"{name} — 미션 화면이 없다 ({page.relative_to(ROOT)})"

    src = page.read_text(encoding="utf-8")
    yml = (SCENARIOS / f"{name}.yml").read_text(encoding="utf-8")
    n_ref = len(YML_REF_RE.findall(yml))
    n_add = sum(len(v.split(",")) for v in YML_ADD_RE.findall(yml))

    missing = []
    if not IMPORT_RE.search(src):
        missing.append("import")
    if not DECL_RE.search(src):
        missing.append("payload.scriptureRef 추출")
    if not ADD_DECL_RE.search(src):
        missing.append("additional_refs 추출")
    render = RENDER_RE.search(src)
    if not render:
        missing.append("<ScenePassage reference additional> 렌더")
    if missing:
        return "FAIL", (
            f"{name} — 참조 {n_ref + n_add}개를 선언하는데 화면이 안 연다:"
            f" {', '.join(missing)}"
        )

    gate = GATE_RE.search(src)
    if gate and render.start() < gate.start():
        return "FAIL", (
            f"{name} — 본문 렌더가 TriggerWarningGate 보다 앞에 있다"
            " (동의 전 노출 가능)"
        )

    return "PASS", (
        f"{name} — 참조 {n_ref + n_add}개(scripture_ref {n_ref} + additional {n_add}),"
        " 화면이 payload 참조로 본문을 연다"
    )


def check_runtime_test() -> tuple[str, str]:
    """런타임 검사가 **존재하고 인물을 다 걸고 있는지.** 구조 검사만 남고 런타임
    검사가 조용히 지워지면, 배선은 있는데 렌더가 안 되는 상태가 다시 초록이 된다."""
    if not RUNTIME_TEST.exists():
        return "FAIL", f"runtime-test — {RUNTIME_TEST.relative_to(ROOT)} 가 없다"
    src = RUNTIME_TEST.read_text(encoding="utf-8")
    missing = [c for c in characters() if f'["{c}"' not in src and f'"{c}",' not in src]
    if missing:
        return "FAIL", f"runtime-test — 런타임 검사에 빠진 인물: {', '.join(missing)}"
    return "PASS", f"runtime-test — 인물 {len(characters())}명 전부 실제 렌더로 잰다"


def check_hardcoded_quotes() -> tuple[str, str]:
    """코드에 박힌 성구를 센다. 주석 안의 인용은 사본이 아니라 근거라 통과."""
    hits: list[tuple[str, int]] = []
    in_comment = 0
    for f in sorted(CONTENT.glob("*.ts")):
        if f.name.endswith(".test.ts"):
            continue
        src = f.read_text(encoding="utf-8")
        spans = comment_spans(src)
        code = [
            m
            for m in QUOTE_CITE_RE.finditer(src)
            if not any(a <= m.start() < b for a, b in spans)
        ]
        in_comment += len(QUOTE_CITE_RE.findall(src)) - len(code)
        if code:
            hits.append((f.name, len(code)))
    total = sum(n for _, n in hits)
    if not total:
        return "PASS", (
            "frontend-quotes — 코드에 박힌 성경 자구가 없다"
            f" (주석 안의 근거 인용 {in_comment}건은 check_monologue_quotes.py 가 정본 대조)"
        )
    where = ", ".join(f"{f}:{n}" for f, n in hits)
    return "FAIL", (
        f"frontend-quotes — 성경 자구가 코드에 {total}건 박혀 있다 ({where})."
        " 사본이 DB 와 프론트 두 벌이 되고 한쪽만 고쳐지는 드리프트가 열린다 —"
        " ScenePassage 원칙 1(본문은 오직 API 응답에서만 온다). 자구 대신 참조를 두고"
        " q(\"창 45:5\", [\"gen-45:5\", 0, 6]) 형태로 /api/scripture 에서 받아라"
        " (frontend/src/lib/content/scripture-quote.ts)"
    )


def main() -> int:
    results: list[tuple[str, str]] = [check_wiring(c) for c in characters()]
    results.append(check_runtime_test())
    results.append(check_hardcoded_quotes())

    print()
    for status, msg in results:
        print(f"  [{status:<7}] {msg}")

    tally = [sum(1 for s, _ in results if s == k) for k in ("PASS", "FAIL", "BLOCKED")]
    print(f"\n--- PASS {tally[0]} / FAIL {tally[1]} / BLOCKED {tally[2]} ---")
    print("  ⚠️ 이 검사의 주장 범위: 소스에 배선이 적혀 있느냐 까지다. 배선이 있는데도")
    print("     렌더가 안 되는 경우는 frontend/src/app/mission-passage.test.tsx 가 잡는다.")
    print("     자구가 맞는지는 scripts/scripture_text_check.py 의 일이다.")
    return 1 if tally[1] else 0


if __name__ == "__main__":
    sys.exit(main())
