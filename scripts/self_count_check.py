#!/usr/bin/env python3
"""문서가 **자기 자신을 세는 주장**을 대조한다 — 배제 문자열 계수.

`occurrence_check.py` 는 「정본을 세어서 하는 주장」을 본다(그 낱말이 정본 몇 절에
있는가). 그것이 못 보는 네 번째 종류가 있다: **이 문서 안에 그 문자열이 몇 번
나오는가**를 이 문서가 스스로 적어 둔 주장이다.

    "「수 6:21」 4회 · 「히 11:31」 18회 · 「그의 맏아들을 잃을 것이요」 3회"

이 주장은 **적는 순간 자기가 자기를 바꾼다.** 예시로 인용한 그 한 번이 계수 대상에
들어가기 때문이다. rev.11 이 1회·2회로 적어 둔 값이 rev.12 에서 4회·3회가 된 것이
그것이고(정정 X), **어느 도구도 울지 않았다** — 자구는 한 글자도 안 틀렸고 나열도
없어서 `occurrence_check` 의 개수 검사에 걸리지 않는다.

검사하는 것 (네 축):

1. `x-total`  — "`exclusions` 는 **N항**" 이 `scripts/gates/rahab.yml` 의 실제 항 수와 맞는가.
2. `x-scope`  — scope 분포 주장(`verse_ref` A · `verse_text` B · `content_leaf` C)이 맞는가.
3. `x-kinds`  — "N 중 M종 실재" 의 M 이 **대상 문서에 실제로 나오는 배제 문자열 종수**와 맞는가.
4. `x-count`  — 「S」 N회 주장마다, S 가 배제 목록에 있는 문자열이면 실제 출현 수와 맞는가.

못 잡는 것: 배제 목록에 **없는** 문자열의 계수 주장은 보지 않는다(대조 기준이 없다 —
아무 낱말이나 세면 「N회」라는 말이 들어간 산문 전부가 주장이 된다). 그런 주장은
조용히 통과시키지 않고 **BLOCKED 로 찍는다.**

🚨 **주장이 하나도 없으면 PASS 가 아니라 BLOCKED(rc 2) 다.** 「셀 것이 없어서 초록」은
이 리포가 반복해 온 실패 형태다.

    python3 scripts/self_count_check.py docs/SEED-RAHAB.md
    python3 scripts/self_count_check.py --selftest
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
YML = ROOT / "scripts/gates/rahab.yml"

EXCL = re.compile(r'value:\s*"([^"]+)",\s*scope:\s*(\w+)')
C_TOTAL = re.compile(r'`exclusions` 는 \*\*(\d+)항\*\*')
# 🚨 주장이 줄바꿈을 건너 이어질 수 있다(실제로 §7-3-a 가 그렇다) — ` · ` 를
# 리터럴로 두면 조용히 「주장 없음」이 되고, 주장 없음은 이 도구에서 초록이 아니다.
C_SCOPE = re.compile(
    r'`verse_ref`\s*(\d+)\s*·\s*`verse_text`\s*(\d+)\s*·\s*`content_leaf`\s*(\d+)')
C_KINDS = re.compile(r'배제 문자열이 (\d+) 중 (\d+)종 실재')
C_COUNT = re.compile(r'「([^」]+)」\s*(\d+)회')


def exclusions(yml: Path):
    """(값, scope) 목록. 파일이 없으면 BLOCKED 로 갈 수 있게 None 을 준다."""
    if not yml.exists():
        return None
    return EXCL.findall(yml.read_text(encoding="utf-8"))


def check(target: Path, yml: Path = YML) -> tuple[int, list[str]]:
    if not target.exists():
        print(f"⊘ 대상 없음: {target}")
        return 2, []
    ex = exclusions(yml)
    if ex is None:
        print(f"⊘ 배제 목록 없음: {yml} — 대조 기준이 없다")
        return 2, []

    text = target.read_text(encoding="utf-8")
    lines = text.splitlines()
    values = [v for v, _ in ex]
    scopes = {}
    for _, s in ex:
        scopes[s] = scopes.get(s, 0) + 1
    # 종수 — 이 문서에 실제로 나오는 배제 문자열이 몇 종인가.
    kinds = sum(1 for v in values if v in text)

    claims = 0
    fails: list[str] = []
    blocked: list[str] = []

    def note(ok: bool, key: str, msg: str):
        nonlocal claims
        claims += 1
        if ok:
            print(f"  ✓ {key}  {msg}")
        else:
            fails.append(key)
            print(f"  ✗ {key}  {msg}")

    m = C_TOTAL.search(text)
    if m:
        note(int(m.group(1)) == len(ex), "x-total",
             f"주장 {m.group(1)}항 · 실측 {len(ex)}항")
    m = C_SCOPE.search(text)
    if m:
        want = tuple(int(g) for g in m.groups())
        got = (scopes.get("verse_ref", 0), scopes.get("verse_text", 0),
               scopes.get("content_leaf", 0))
        note(want == got, "x-scope", f"주장 {want} · 실측 {got}")
    m = C_KINDS.search(text)
    if m:
        ok = int(m.group(1)) == len(ex) and int(m.group(2)) == kinds
        note(ok, "x-kinds",
             f"주장 {m.group(1)} 중 {m.group(2)}종 · 실측 {len(ex)} 중 {kinds}종")

    for lineno, raw in enumerate(lines, 1):
        for s, n in C_COUNT.findall(raw):
            if s not in values:
                # 배제 목록에 없는 문자열은 대조 기준이 없다. 조용히 넘기지 않는다.
                blocked.append(f"{target.name}:{lineno}  「{s}」 {n}회 — 배제 목록 밖")
                continue
            note(int(n) == text.count(s), f"x-count·{s}",
                 f"{target.name}:{lineno}  주장 {n}회 · 실측 {text.count(s)}회")

    for b in blocked:
        print(f"  ⊘ {b}")

    print(f"\n자기 계수 대조: 주장 {claims} · 불일치 {len(fails)} · 미판정 {len(blocked)}")
    if claims == 0:
        # 「셀 것이 없어서 초록」을 만들지 않는다.
        print("🚨 주장이 하나도 없다 — PASS 가 아니라 BLOCKED 다.")
        return 2, fails
    if fails:
        return 1, fails
    return (2, fails) if blocked else (0, fails)


def selftest() -> int:
    """네 축마다 정본을 하나씩 틀리게 만들어 **그 축이** 빨강을 내는지 본다.

    rc 만 보면 엉뚱한 축이 대신 걸려도 통과한다 — 축 이름을 대조한다.
    """
    import tempfile

    src = (ROOT / "docs/SEED-RAHAB.md").read_text(encoding="utf-8")
    ex = exclusions(YML)
    if ex is None or "배제 문자열이" not in src:
        print("⊘ selftest 불가 — seed 또는 배제 목록이 없다")
        return 2

    # 🚨 scope 주장은 줄바꿈을 건너 이어져 있다 — 리터럴로 적으면 「변이 대상 없음」이
    # 되어 축 하나가 조용히 안 재진다. 실제 자구를 정규식으로 떠서 그 안을 바꾼다.
    sm = C_SCOPE.search(src)
    scope_before = sm.group(0) if sm else ""
    scope_after = scope_before.replace(sm.group(1), str(int(sm.group(1)) - 1), 1) if sm else ""

    cases = [
        ("x-total", "`exclusions` 는 **40항**", "`exclusions` 는 **41항**"),
        ("x-scope", scope_before, scope_after),
        ("x-kinds", "배제 문자열이 40 중 28종 실재", "배제 문자열이 40 중 27종 실재"),
        ("x-count·히 11:31", "「히 11:31」 18회", "「히 11:31」 17회"),
    ]
    hit = 0
    with tempfile.TemporaryDirectory() as td:
        for key, before, after in cases:
            if before not in src:
                print(f"  ⊘ {key} — 변이 대상 문자열이 없다: {before!r}")
                continue
            p = Path(td) / "MUTANT.md"
            p.write_text(src.replace(before, after, 1), encoding="utf-8")
            rc, fails = check(p)
            ok = rc == 1 and key in fails
            hit += ok
            print(f"  {'✓' if ok else '✗'} {key} — rc={rc} 걸린 축={fails}\n")
    print(f"selftest: 검출 {hit} / {len(cases)}")
    return 0 if hit == len(cases) else 1


if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "--selftest":
        sys.exit(selftest())
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    a = Path(sys.argv[1])
    sys.exit(check(a if a.is_absolute() else ROOT / a)[0])
