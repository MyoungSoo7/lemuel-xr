#!/usr/bin/env python3
"""문서가 **코드에 대해** 한 주장을 기계 대조.

기존 세 도구는 전부 문서만 읽는다 — `quote_sweep.py` 는 인용된 자구를,
`verse_lines_check.py` 는 절 본문 행을, `occurrence_check.py` 는 정본을 센 주장을.
**`backend/` 와 `content/` 를 읽는 도구가 하나도 없었다.**

2026-08-06 rev.7 채점에서 차단 6건 중 5건이 전부 그 자리에서 나왔다:

    ":748 사후 정규식이 목맴 계열에 맹목이라"     → application.yml 을 한 번도 안 열었다
    ":1306 '더 이상 못 버티겠다' 를 잡지 못한다"   → 그 문자열이 despairIdeation 에 있다
    ":693 실재하는 14건은 전부 [subtitle_only…]"  → yml 13건 중 11건 · 이형 2건 · 산문 1건
    ":698 · :1330 §5-5 참조"                      → §5 는 5-4 로 끝난다
    ":403 골딩게이 "한 번 언급""                   → 원문은 one-word mention

넷째 검사축이라 도구도 넷째다. 검사하는 것:

1. **참조 실재와 내용 일치** — `path:NNN` 이 실재하는 줄인가, 그리고 **거기에 그것이
   있는가**. 초판은 줄 존재만 봤고, 그래서 rev.8 에서 **좌표 여섯 자리가 정확히 +20
   어긋난 채 "불일치 0 · EXIT 0"** 으로 통과했다 — 측정 뒤 위에 20줄을 넣고 표를 안
   고친 것을, 줄 수만 세는 검사가 덮었다. 같은 줄의 백틱·인용부호 조각을 앵커로 삼아
   그 범위 안에 있는지 보고, 없으면 **그 파일 안에서 실제 위치를 찾아 같이 낸다.**
2. **자기 절 참조** — `§N-M` 이 이 문서에 정의된 절인가(헌장·타 문서 참조는 건너뜀).
3. **키 실재와 값 형태** — 백틱 안 `snake_case` 식별자가 `content/`·`backend/` 에
   실재하는가. 실재하면 **값 형태를 종류별로 센다** — 이름만 맞고 값이 다른 경우가
   실제 결함이었다("이름만 grep 하고 값의 형태를 안 봤다").
4. **미탐지 주장 프로브** — "잡지 못한다 · 맹목 · 미탐지" 가 있는 줄의 인용 문자열을
   런타임 정규식(`application.yml` 의 `crisis-keywords-regex`)에 실제로 넣어 본다.
   걸리면 그 줄의 주장은 거짓이다.
5. **실측 표 재현** — 표의 첫 칸이 `` `xxx.py` `` 인 행은 도구 실행 결과를 옮겨 적은
   것이다. **그 도구를 지금 다시 돌려** EXIT 코드와 요약 수치, 그리고 그 행이 적은
   맨 `:NNN` 자리를 실제 출력과 대조한다. 손으로 옮겨 적은 좌표는 본문이 길어지면
   반드시 어긋나므로, 사람이 다시 재는 규율에 기대지 않고 기계가 다시 잰다.

못 잡는 것: 자연어 주장 일반. 이 도구는 **파일·줄·키·정규식** 네 종류의
기계로 확인 가능한 참조만 본다. 나머지는 여전히 사람이 읽어야 한다.

    python3 scripts/code_claims_check.py docs/SEED-RAHAB.md
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP_YML = ROOT / "backend" / "src" / "main" / "resources" / "application.yml"

# `backend/src/.../application.yml:136` · `content/ruth/scene1.yml:93` · `docs/X.md:12-14`
FILE_REF = re.compile(
    r"`?((?:backend|content|frontend|scripts|docs|unity|unity-stub)/[\w./-]+?\.(?:yml|yaml|kt|ts|tsx|py|md)):(\d+)(?:[-–](\d+))?`?"
)
# 리포 경로처럼 생긴 앵커 — 식별자 앵커에서 제외한다(대상 파일 안에 있을 리 없다).
PATHLIKE = re.compile(
    r"^(?:backend|content|frontend|scripts|docs|unity|unity-stub)/"
    r"|\.(?:yml|yaml|kt|ts|tsx|py|md)$"
)
# 이 문서 자신의 절 정의: `### 5-4. …` / `## 10. …`
SECTION_DEF = re.compile(r"^#{2,4}\s+(\d+(?:-\d+)*(?:-[a-z])?)\.")
# 🚨 초판은 `§(\d+-\d+)` 였다. 그래서 `§7-2-a` 를 **`§7-2` 로 잘라** 보고했다 —
# 이 문서에 `§7-2` 가 없다는 판정은 맞았지만 **가리킨 좌표가 틀렸고**, 사람이
# 원문을 찾아가면 문서에 없는 문자열을 찾게 된다. 꼬리까지 다 잡는다.
SECTION_REF = re.compile(r"§(\d+(?:-\d+)+(?:-[a-z])?)")
# 타 문서를 가리키는 § 는 건너뛴다 — 헌장 §2.1-b 처럼.
FOREIGN = re.compile(r"헌장|\.md|SERIES-GRACE|FUNCTIONAL-SPEC|EMOTION-CLASSIFIER")
# 🚨 FOREIGN 은 **줄 단위**라, 「seed §7-2」처럼 *참조 하나*에 붙은 한정어는 못 본다.
# 한정어가 바로 앞에 오면 그 참조만 건너뛴다. 줄 전체를 면제하지 않는다 —
# 같은 줄의 **한정어 없는** 참조는 그대로 이 문서 것으로 대조받아야 하기 때문이다.
QUALIFIED = re.compile(r"(?:seed|SEED|MVP|로그|README|정본|사양)\s*§$")

IDENT = re.compile(r"`([a-z][a-z0-9]*(?:_[a-z0-9]+){1,4})`")
# 백틱 안에 **키와 값을 함께** 적으면 그건 값 형태에 대한 주장이다:
#     `voice_intensity_options: subtitle_only`  → 그 형태가 실재하는가?
# rev.6 의 결함이 정확히 이 형태였고(스칼라 0건), 그래서 별도로 대조한다.
KEYVAL = re.compile(r"`([a-z][a-z0-9_]{3,}):\s*(\[[^`\]]*\]|[^`\s][^`]*?)`")
# 자리표시자 표기는 값 주장이 아니다. `<id>` · `…` 뿐 아니라 `path: NNN` 처럼
# 대문자 자리표시자도 있다 — 초판은 이걸 "코드에 0건"이라 띄웠다(rev.8 실측 오탐).
PLACEHOLDER = re.compile(r"[<>]|…|\.\.\.|\b[A-Z]{2,}\b")
# **옛 오류를 일부러 인용한 줄**은 주장이 아니다. 개정 사유 표가 통째로 그렇다.
# 지우지 않고 2군으로 내린다 — 조용히 빼면 진짜 결함이 그 뒤에 숨는다.
#
# ⚠️ 이 판정은 **낱말 매칭 + ±1줄 창**이다. 양쪽으로 다 틀린다. 하루에 둘 다 실측했다
#    (2026-08-11). 둘 다 도구가 아니라 **문장 위치·낱말** 때문에 판정이 뒤집힌 사례다.
#
#    ① 너무 좁다 — `SEED-RAHAB.md:1105` 는 옛 값을 인용한 줄이 맞는데, 그걸 부정하는
#       말("**0건**이다")이 같은 문단 **4줄 아래**에 있었다. 마크다운은 하드랩이라
#       한 문단이 여러 물리 줄이다. 창이 안 닿아 1군으로 떴다.
#    ② 너무 넓다 — `MVP-RUTH-CONTENT.md:498` 은 §4-2 상호참조 결함으로 1군이었는데,
#       바로 위에 답변 산문을 채워 넣자("…아니다") 2군으로 내려갔다. 결함은 그대로다.
#
#    창을 문단 단위로 넓히는 쪽은 **택하지 않았다.** ②가 보여주듯 이 heuristic 의
#    고장 방향은 이미 과다 강등이고, 넓히면 그쪽이 더 심해진다. 위 docstring 이 적은
#    rev.8 사고(낱말 하나로 좌표 드리프트 6건 강등)도 같은 방향이다. ①은 문서 표기를
#    고쳐서 해결했다(살아있는 `키: 값` 형태로 옛 값을 적지 않는다).
DISCLAIMED = re.compile(
    r"0건|없다|없었다|적 없다|틀렸다|허위|오류|삭제|아니다|미착수|정정|재현되지")
SEARCH_TREES = ("content", "backend", "frontend", "scripts")
SCAN_SUFFIXES = {".yml", ".yaml", ".kt", ".ts", ".tsx", ".py", ".md"}

# 🚨 빌드 산출물·의존성 트리는 「코드」가 아니다 (2026-08-11 신설).
#
# 초판은 `base.rglob("*")` 로 `frontend`·`backend` 를 통째로 훑었다. 그 결과 이 도구는
# **사람마다 다른 답을 냈다** — 실측: 빌드 산출물이 있는 작업 트리 5351개 파일 대 깨끗한
# 체크아웃 652개 파일. CI 는 이 잡에서 npm install 도 gradle build 도 하지 않으므로 늘
# 후자를 본다.
#
# 이것이 이론적 위험이 아니라 실제로 틀린 답을 냈다. f385546 이 래치 정책 값을 바꾼 뒤
# `docs/SEED-RAHAB.md:1105` 는 **옛 값을 인용한 채 남아 있었는데**, 로컬 실행은 그 주장을
# 통과시켰다 — 8월 5일자 `backend/build/resources/main/scenarios/ruth.yml` 에 옛 값이
# 그대로 들어 있었기 때문이다. 즉 **낡은 빌드 사본이 낡은 문서 주장을 옳아 보이게 했다.**
# CI 만 빨갛고 로컬은 초록이었던 이유가 이것이다.
#
# `os.walk` 로 바꾼 것도 같은 이유다 — `rglob` 은 가지치기를 못 해서 제외할 트리도 일단
# 다 걸어 들어간다. 정렬은 결정성 때문이다(파일시스템 순서는 macOS·Linux 가 다르다).
PRUNE_DIRS = {
    "node_modules", ".next", "build", "dist", "out", ".gradle",
    "__pycache__", ".venv", "venv", ".git", "coverage", ".turbo",
}

# 이 낱말이 있는 줄은 "런타임이 X 를 못 잡는다" 는 주장으로 본다.
NEGATIVE = re.compile(r"잡지 못한다|잡히지 않는다|맹목|미탐지|탐지되지 않는다|걸리지 않는다")
QUOTED = re.compile(r"[\"“]([^\"”]{2,40})[\"”]")
# 인용된 것이 **사용자 발화**일 때만 정규식에 넣는다. 라벨·항목명("자해·자살 키워드
# (정규식 + **의미 매칭**)")까지 넣으면 그 안의 '자해' 가 걸려 오탐이 난다 — 초판 실측.
UTTERANCE = re.compile(r"^[가-힣][^·()*\[\]{}]*[다요어네지까죠음]$")

# 식별자 오탐 억제 — 문서·산문에서만 쓰는 낱말은 코드 키가 아니다.
SKIP_IDENT = {"file_path", "chat_id", "message_id"}

# 참조와 같은 줄에 있는 백틱·인용부호 조각. 이것이 그 줄에 실제로 있는지가 앵커다.
ANCHOR = re.compile(r"`([^`\n]{6,80})`|[\"“]([^\"”\n]{6,80})[\"”]")
# 실측 표 행: 첫 칸이 도구 이름인 마크다운 행.
TOOL_ROW = re.compile(r"^\|\s*`?(\w+\.py)`?\s*\|(.*)\|\s*$")
# 표 구분선. 바로 윗줄이 머리글이라는 표시다 — 열을 자리가 아니라 이름으로 찾기 위한 닻.
SEP_ROW = re.compile(r"^\s*\|(?:\s*:?-{3,}:?\s*\|)+\s*$")
BARE_LINE_REF = re.compile(r"`:(\d+)`")


def strip_md(s: str) -> str:
    """마크다운 강조·공백만 걷는다. 자구는 건드리지 않는다."""
    return re.sub(r"\s+", " ", re.sub(r"\*\*|\*|__", "", s)).strip()


def sniff_regex() -> re.Pattern[str] | None:
    """런타임 위기 정규식을 application.yml 에서 읽어 Python 문법으로 바꾼다.

    자바 명명 그룹 `(?<name>)` → 파이썬 `(?P<name>)`. 못 읽으면 None 을 주고
    4번 검사를 **건너뛴 것으로 보고**한다 — 조용히 통과시키지 않는다.
    """
    if not APP_YML.exists():
        return None
    m = re.search(r"crisis-keywords-regex:\s*\$\{[A-Z_]+:(.*)\}\s*$",
                  APP_YML.read_text(encoding="utf-8"), re.M)
    if not m:
        return None
    try:
        return re.compile(m.group(1).replace("(?<", "(?P<"))
    except re.error:
        return None


def load_trees() -> list[tuple[Path, str]]:
    files = []
    for tree in SEARCH_TREES:
        base = ROOT / tree
        if not base.exists():
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames[:] = sorted(d for d in dirnames if d not in PRUNE_DIRS)
            for fn in sorted(filenames):
                p = Path(dirpath) / fn
                if p.suffix in SCAN_SUFFIXES:
                    files.append((p, p.read_text(encoding="utf-8", errors="replace")))
    return files


def value_shapes(files: list[tuple[Path, str]], key: str) -> tuple[Counter, list[str]]:
    """key 가 yaml 키로 쓰인 줄을 모아 값 형태별로 센다. md 는 산문으로 따로 센다."""
    shapes: Counter = Counter()
    prose = []
    pat = re.compile(rf"^\s*{re.escape(key)}:\s*(.+?)\s*(?:#.*)?$")
    for path, text in files:
        for i, line in enumerate(text.splitlines(), 1):
            m = pat.match(line)
            if not m:
                continue
            rel = path.relative_to(ROOT).as_posix()
            if path.suffix == ".md":
                prose.append(f"{rel}:{i}")
            else:
                shapes[m.group(1)] += 1
    return shapes, prose


def check(target: Path) -> int:
    text = target.read_text(encoding="utf-8")
    lines = text.splitlines()
    files = load_trees()
    rx = sniff_regex()
    name = target.name

    sections = {m.group(1) for line in lines if (m := SECTION_DEF.match(line))}
    bad = skipped = 0
    cited: list[str] = []   # 옛 오류 인용으로 보이는 줄 — 사람이 훑을 2군

    def flag(lineno: int, msg: str, demotable: bool = True) -> None:
        """demotable=False 면 2군으로 내리지 않는다.

        **좌표 주장은 인용이 될 수 없다.** `path:NNN` 이 가리키는 자리와 그 표가
        적은 줄 번호는 문서 자신의 주소이지 옛 오류의 인용이 아니다. 첫 판은 이
        구분이 없어서 rev.8 의 좌표 드리프트 여섯 자리를 전부 2군으로 내렸고,
        그래서 "불일치 0 · EXIT 0" 이 나왔다 — 낱말 하나("없다")가 근거였다.
        """
        nonlocal bad
        # 정정문은 인용한 줄 **바로 다음 줄**에서 부정하는 일이 흔하다(문단 어순).
        # 한 줄만 보면 그런 정정을 새 결함으로 띄운다 — 실측 오탐.
        window = "\n".join(lines[max(0, lineno - 2):lineno + 1])
        if demotable and DISCLAIMED.search(window):
            cited.append(f"  {name}:{lineno}  {msg}")
        else:
            bad += 1
            print(f"  ✗ {name}:{lineno}  {msg}")

    # ── 1. 파일:줄 참조 — 존재 + **내용 일치** ──────────────────────
    for lineno, raw in enumerate(lines, 1):
        refs = FILE_REF.findall(raw)
        if not refs:
            continue
        # 참조가 **둘 이상**인 줄은 앵커를 어느 참조에 붙일지 알 수 없다. 하나뿐인
        # 앵커를 모든 참조에 대보면 나머지가 전부 불일치로 뜬다 — 실측 오탐(개정
        # 사유 표 5번 행이 `:515` 와 `:519` 를 함께 적었고, 앵커는 앞의 것 소유였다).
        # **못 보는 자리로 남긴다.** 억지로 짝지어 붙이면 조용히 틀린 판정이 된다.
        # (존재·범위 검사는 그대로 하고, **앵커 대조만** 건너뛴다.)
        anchors: list[str] = []
        key_anchors: list[str] = []
        if len(refs) == 1:
            # 앵커는 참조 표기 자체를 뺀 나머지 백틱·인용 조각이다.
            # **표 행이면 참조가 있는 칸만 본다.** 마크다운 표는 한 줄에 여러 주장을
            # 담으므로, 옆 칸의 식별자를 이 참조의 앵커로 삼으면 조용히 틀린 판정이
            # 된다(실측 오탐: 개정 사유 표 7번 행 — 참조는 「사유」 칸, 앵커
            # `consent_covers_scenes` 는 「조치」 칸 소유였다).
            rest = raw
            if raw.lstrip().startswith("|"):
                for cell in raw.split("|"):
                    if FILE_REF.search(cell):
                        rest = cell
                        break
            rest = FILE_REF.sub(" ", rest)
            raw_anchors = [strip_md(a or b) for a, b in ANCHOR.findall(rest)]
            anchors = [a for a in raw_anchors if a and not re.fullmatch(r"[\w./-]+", a)]
            # **식별자 앵커** — `staging_constraints` 처럼 한 낱말짜리 키는 위 필터가
            # 통째로 버린다. 그런데 코드·저작물을 가리키는 인용에서 앵커는 대개
            # 식별자 하나뿐이라, 버리면 그 줄은 존재·범위만 검사받고 **내용 대조를
            # 못 받는다**. rev.11 의 `newchar_gates.py:1354`(빈 줄) 세 자리가 정확히
            # 이 구멍으로 rc=0 을 통과했다.
            #
            # ⚠️ 데이터 파일(`.yml`)에만 적용한다. `.py`/`.kt` 는 "그 키를 판정하는
            # 코드가 있는 줄"을 가리키는 인용이 정상이라 — `newchar_gates.py:914` 가
            # `crisis_resources.default` 를 상수로 참조하듯 — 리터럴 부재가 결함이
            # 아니다(실측 오탐 2건). 소스 파일의 좌표 드리프트는 이 축이 아니라
            # 사람·채점이 잡는다. **못 잡는 자리로 남긴다.**
            key_anchors = [
                a for a in raw_anchors
                if a and a not in anchors
                and not PATHLIKE.search(a)
                and re.fullmatch(r"[\w.]+", a)
                and ("_" in a or "." in a)
            ]
        for rel, start, end in refs:
            p = ROOT / rel
            if not p.exists():
                bad += 1
                print(f"  ✗ {name}:{lineno}  참조 파일 없음 — {rel}")
                continue
            body = p.read_text(encoding="utf-8", errors="replace").splitlines()
            last = int(end or start)
            if last > len(body):
                bad += 1
                print(f"  ✗ {name}:{lineno}  {rel}:{last} — 그 파일은 {len(body)}줄뿐")
                continue
            # **가리킨 범위가 통째로 빈 줄이면 그 좌표는 언제나 틀렸다.**
            # 빈 줄을 일부러 인용하는 경우는 없다. 이 규칙은 앵커가 하나도 없는
            # 인용 — 곧 이 축이 존재·길이만 보고 통과시키던 자리 — 까지 덮는다.
            # rev.11 채점이 이 형태로 살아 있는 좌표 드리프트 2건을 잡았고
            # (`SERIES-GRACE.md:113`(실제 `:112`) · `gates/rahab.yml:35`(실제 `:37`)),
            # 여섯 도구가 전부 rc=0 인 상태였다. **오탐이 원리적으로 없는 규칙**이라
            # 강등하지 않는다.
            # ⚠️ 이 규칙도 `path:NNN` 형태만 본다 — 앞 문장의 파일을 이어받는
            # **맨 `:NNN`** 은 여전히 이 축의 사각이다(§10 C9).
            if all(not x.strip() for x in body[int(start) - 1:last]):
                flag(lineno, f"{rel}:{start}" + (f"-{end}" if end else "")
                             + " — 그 범위는 통째로 빈 줄이다 (좌표가 밀렸다)",
                     demotable=False)
                continue
            # **줄이 있다는 것과 거기 그것이 있다는 것은 다르다.** 앵커가
            # 범위 안에 없고 **그 파일의 다른 줄에는 있으면** 좌표가 밀린 것이다.
            # 어디에도 없으면 앵커가 그 파일의 자구가 아니라는 뜻이라 판정하지 않는다.
            scope = strip_md(" ".join(body[int(start) - 1:last]))
            whole = [strip_md(x) for x in body]
            probe = anchors + (key_anchors if rel.endswith((".yml", ".yaml")) else [])
            for a in probe:
                if a in scope:
                    break
                hits = [i for i, x in enumerate(whole, 1) if a in x]
                if hits:
                    flag(lineno, f"{rel}:{start}"
                                 + (f"-{end}" if end else "")
                                 + f" 에 「{a[:40]}」 없음 — 실제 "
                                 + " · ".join(f":{h}" for h in hits[:4]),
                         demotable=False)
                    break

    # ── 2. 자기 절 참조 ──────────────────────────────────────────────
    for lineno, raw in enumerate(lines, 1):
        for m in SECTION_REF.finditer(raw):
            ref = m.group(1)
            if FOREIGN.search(raw):
                continue
            if QUALIFIED.search(raw[: m.start() + 1]):
                continue
            if ref not in sections:
                flag(lineno, f"§{ref} 는 이 문서에 정의된 적 없다"
                             f" (정의된 절: {', '.join(sorted(sections))})")

    # ── 3a. `키: 값` 을 통째로 인용한 주장 ──────────────────────────
    # 이름은 맞고 값이 다른 결함이 두 판 연속 났다. 값까지 대조한다.
    for lineno, raw in enumerate(lines, 1):
        for key, val in KEYVAL.findall(raw):
            # `when: {<label>: null}` · `consent_declined_route: <id>` 처럼
            # 자리표시자를 쓴 것은 값 주장이 아니라 **표기**다. 대조 대상이 아니다.
            if PLACEHOLDER.search(val):
                continue
            # URL 은 `키: 값` 이 아니다. `https://…` 의 스킴이 키로 잡히고 나머지가
            # 값이 된다 — `SERIES-GRACE.md:277`(대한성서공회 링크)이 이 오탐으로
            # 여러 판 동안 rc=1 을 냈고, 그때마다 "헌장의 알려진 오탐"으로 손으로
            # 넘겨 왔다. 사람이 매번 넘겨야 하는 빨강은 다음 빨강을 못 보게 한다.
            if key in ("http", "https", "ftp") or val.lstrip().startswith("//"):
                continue
            shapes, prose = value_shapes(files, key)
            if not shapes and not prose:
                continue  # 코드 키가 아닐 수 있다
            # 리스트 리터럴의 쉼표 뒤 공백은 YAML 상 같은 값이다. 그 차이를
            # 형태 불일치로 띄우면 잡음이 진짜 불일치를 덮는다.
            def tidy(x: str) -> str:
                return re.sub(r",\s*", ", ", x.strip())
            if not any(tidy(v) == tidy(val) for v in shapes):
                detail = " · ".join(f"{c}× {v}" for v, c in shapes.most_common(4))
                flag(lineno, f"`{key}: {val}` 형태는 코드에 0건"
                             + (f" — 실재: {detail}" if detail else "")
                             + (f" (산문 {len(prose)}건 {prose[:3]})" if prose else ""))

    # ── 3b. 키 실재와 값 형태 ────────────────────────────────────────
    idents = {m for line in lines for m in IDENT.findall(line)} - SKIP_IDENT
    for key in sorted(idents):
        shapes, prose = value_shapes(files, key)
        if not shapes and not prose:
            continue  # 코드 키가 아닐 수 있다 — 부재를 결함으로 단정하지 않는다
        total = sum(shapes.values())
        if len(shapes) > 1 or prose:
            print(f"  ⓘ {key} — yml/코드 {total}건"
                  + (f" · 산문 {len(prose)}건 {prose}" if prose else ""))
            for v, c in shapes.most_common():
                print(f"        {c:2}× {v}")

    # ── 4. 미탐지 주장 프로브 ────────────────────────────────────────
    if rx is None:
        skipped += 1
        print(f"  ?? 런타임 정규식을 읽지 못했다 — 미탐지 주장 검사를 건너뛴다"
              f" ({APP_YML.relative_to(ROOT)})")
    else:
        for lineno, raw in enumerate(lines, 1):
            if not NEGATIVE.search(raw):
                continue
            for s in QUOTED.findall(raw):
                if not UTTERANCE.match(s.strip()):
                    continue
                m = rx.search(s)
                if m:
                    flag(lineno, f"\"{s}\" 를 못 잡는다고 적었으나"
                                 f" 런타임이 잡는다 — {m.lastgroup}")

    # ── 5. 실측 표 재현 ──────────────────────────────────────────────
    # 도구 이름이 첫 칸인 행은 실행 결과를 옮겨 적은 것이다. 지금 다시 돌린다.
    #
    # **열은 머리글로 찾는다. 자리로 찾지 않는다.** 초판은 결과를 `cells[0]` 에서만
    # 읽고 입력 파일 칸을 아예 무시했다. 그래서 `| 도구 | 명령 | 결과 | EXIT |` 로 짠
    # rev.7 표(`docs/SEED-RAHAB.md:227`)에서는 첫 칸이 경로라 declared 가 공집합이 되어
    # **세 줄이 영원히 초록**이었고, 두 줄이 선언한 입력(`VERSES-RAHAB-GAE.md`)은 한 번도
    # 돌지 않은 채 EXIT 만 우연히 맞아떨어졌다. 열 순서가 판정을 바꾸면 그건 검사가 아니다.
    headers: dict[int, list[str]] = {}
    for i, raw in enumerate(lines, 1):
        if SEP_ROW.match(raw) and i >= 2 and lines[i - 2].lstrip().startswith("|"):
            headers[i - 1] = [strip_md(c) for c in
                              lines[i - 2].strip().strip("|").split("|")]

    def column(lineno: int, *words: str) -> int | None:
        """이 행이 속한 표의 머리글에서 낱말을 포함하는 열의 자리를 준다."""
        head = headers.get(max((h for h in headers if h < lineno), default=-1))
        if not head:
            return None
        for idx, cell in enumerate(head):
            if any(w in cell for w in words):
                return idx
        return None

    for lineno, raw in enumerate(lines, 1):
        m = TOOL_ROW.match(raw)
        if not m:
            continue
        tool = m.group(1)
        script = ROOT / "scripts" / tool
        if not script.exists() or script.resolve() == Path(__file__).resolve():
            continue  # 자기 자신은 돌리지 않는다(무한 재귀)
        cells = [c.strip() for c in raw.strip().strip("|").split("|")]

        # 입력 파일: 표가 선언한 대상으로 돌린다. 선언이 없을 때만 이 문서가 대상이다.
        arg, arg_name = target, name
        ci = column(lineno, "명령", "입력", "대상")
        if ci is not None and ci < len(cells):
            for tok in re.findall(r"[\w./-]+\.(?:md|txt|ya?ml|py)", cells[ci]):
                if (ROOT / tok).exists():
                    arg, arg_name = ROOT / tok, Path(tok).name
                    break
        try:
            r = subprocess.run([sys.executable, str(script), str(arg)],
                               capture_output=True, text=True, cwd=ROOT, timeout=180)
        except (OSError, subprocess.TimeoutExpired) as e:
            skipped += 1
            print(f"  ?? {name}:{lineno}  {tool} 재실행 실패 — {e}")
            continue

        # EXIT 칸. 머리글로 찾고, 없으면 숫자 하나뿐인 칸으로 되짚는다.
        ei = column(lineno, "EXIT", "exit")
        exits = ([strip_md(cells[ei])] if ei is not None and ei < len(cells)
                 else [strip_md(c) for c in cells])
        for plain in exits:
            if re.fullmatch(r"\d+", plain) and int(plain) != r.returncode:
                flag(lineno, f"{tool}({arg_name}) EXIT 선언 {plain}"
                             f" ≠ 실제 {r.returncode}", demotable=False)

        # 결과 칸의 수치. **머리글에 결과 열이 없으면 판정하지 않고 건너뛴 것으로 센다** —
        # 못 재는 것을 통과로 적지 않는다.
        ri = column(lineno, "결과", "실측")
        if ri is None or ri >= len(cells):
            skipped += 1
            print(f"  ?? {name}:{lineno}  {tool} 결과 열을 머리글에서 못 찾음 — 수치 미판정")
        else:
            # 요약 수치: 항목 줄(두 칸 들여쓴 줄)을 뺀 나머지에 실린 정수들.
            summary = {int(x) for ln in r.stdout.splitlines()
                       if not ln.startswith("  ") for x in re.findall(r"\d+", ln)}
            declared = {int(x) for x in re.findall(r"\d+", strip_md(cells[ri]))}
            if declared - summary:
                flag(lineno, f"{tool}({arg_name}) 결과 수치"
                             f" {sorted(declared - summary)} 가 이번 실행 요약에 없다"
                             f" — 실제 {sorted(summary)}", demotable=False)

        # 맨 `:NNN` 자리: 도구가 **그 입력에 대해** 실제로 찍은 줄 번호와 대조한다.
        actual = {int(x) for x in re.findall(rf"{re.escape(arg_name)}:(\d+)", r.stdout)}
        claimed = {int(x) for x in BARE_LINE_REF.findall(raw)}
        if claimed and claimed - actual:
            flag(lineno, f"{tool}({arg_name}) 이 지목한 줄로 적힌 "
                         f"{sorted(claimed - actual)} 를 이번 실행은 지목하지 않았다"
                         f" — 실제 {sorted(actual)}", demotable=False)

    if cited:
        print(f"\n── 2군 · 옛 오류를 인용한 줄로 보임 · 훑어볼 것 ({len(cited)}) ──")
        for line in cited:
            print(line)

    print(f"\n코드 주장 대조: 불일치 {bad} · 인용 판정 {len(cited)} · 건너뜀 {skipped}")
    return 2 if skipped and not bad else (1 if bad else 0)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    arg = Path(sys.argv[1])
    sys.exit(check(arg if arg.is_absolute() else ROOT / arg))
