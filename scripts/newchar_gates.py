#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
/새인물 파이프라인 게이트 — 단일 실행 구현 (G0~G11).

왜 이 파일이 존재하는가
-----------------------
seed-COMMON.md 는 게이트를 "베껴 쓸 산문"으로 배포했다. 그래서 라운드마다 5개 seed 가
게이트를 각자 다시 타이핑했고 매번 누군가 잘못 옮겼다(⑮ 재발 2건, ⑨ 재발 1건,
폐기 기준 인용 1건 — COMMON-v5-pending ★ 참조). 이 파일은 그 전사(轉寫) 자체를 없앤다.
seed 는 `scripts/gates/{인물}.yml` **설정만** 쓰고 게이트 코드는 쓰지 않는다.

설계 규칙 (개별 땜질 금지 — 전부 여기 한 군데서만 강제한다)
------------------------------------------------------------
1. **판정 3종**: PASS / FAIL <이유> / BLOCKED <이유>.
   BLOCKED 는 "판정 불가"다 — 타겟 부재, 입력 배열이 비어 순회 0회 등.
   **BLOCKED 는 PASS 가 아니다.** FAIL 또는 BLOCKED 가 하나라도 있으면 종료코드 비영.
2. **빈 순회는 BLOCKED** (⑳㉒ 의 일반화). 어떤 게이트도 "순회 0회 → 무출력 → 초록"이
   될 수 없다. `Ctx.iterate()` 를 통하지 않고 리스트를 도는 게이트를 추가하지 마라.
3. **grep 금지**. 전부 python. macOS BSD grep 에 `-P` 가 없고(㉔) 이 개발 셸의 `grep` 은
   ugrep 래퍼라 대화형과 스크립트 결과가 갈린다(㉘). 외부 grep 을 부르지 않으면 둘 다 무의미해진다.
4. **값을 봐야 하는 판정은 전부 파서 경유**. 주석·따옴표 없는 스칼라를 텍스트로 보면
   YAML 파서가 보는 것과 달라진다(⑤⑱⑲, PR #18 의 교훈).
5. **카브아웃은 노드 단위**. `lint_forbidden_tokens` 제외는 줄 단위 `grep -v` 가 아니라
   YAML 노드의 줄 범위를 계산해 제외한다(★★). 줄 단위 카브아웃은 블록 리스트에서
   깨지고, 그 결과 designer 가 초록으로 가는 가장 쉬운 길이 "안전 토큰을 지우는 것"이 된다.
   = 게이트가 안전 저하에 보상을 주는 상태. flow 단일라인 강제는 채택하지 않는다(포매터 한 번이면 깨짐).
6. **대조 대상을 암묵으로 정하지 않는다** (㉝). 배제 문자열 선언은 `scope:` 로 어느 텍스트에
   대고 재는지를 명시해야 하고 **기본값이 없다.** 기본값을 주면 "참조 표기를 본문 열에 대고
   찾아 영원히 초록" 인 결함이 조용히 재발한다. 누락은 설정 오류 — 게이트를 실행하지 않는다.
7. **목록은 내용을 보기 전에 목록 자체를 검사한다** (㉞). 선언 개수 대조 · 자기매칭 원소 ·
   구분자 없는 원소. 문서에서 옮겨 적힌 배열은 줄임표가 원소가 되어 있을 수 있다.

사용법
------
    python3 scripts/newchar_gates.py --character elijah
    python3 scripts/newchar_gates.py --config scripts/gates/peter.yml
    python3 scripts/newchar_gates.py --character david --strict      # legacy 면제 무시
    python3 scripts/newchar_gates.py --character elijah --json
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, field
from typing import Any, Callable, Iterable

try:
    import yaml
except ImportError:  # pragma: no cover
    sys.stderr.write("PyYAML 필요: pip3 install pyyaml\n")
    sys.exit(2)


PASS = "PASS"
FAIL = "FAIL"
BLOCKED = "BLOCKED"

# ──────────────────────────────────────────────────────────────────────────────
# 공백 정규화 — **출처: backend/src/main/kotlin/.../safety/application/ForbiddenTokenScanner.kt**
#
#     private fun normalize(s: String): String = s.trim().replace(WHITESPACE, " ")
#     val WHITESPACE = Regex("\\s+")
#     ... 토큰과 본문을 **둘 다** 정규화한 뒤 indexOf 한다.
#
# 게이트가 이 의미론과 어긋나면 두 방향으로 다 틀린다:
#   ① 게이트가 더 엄격 → **false red**. 실제로는 차단되는 올바른 선언이 red 가 되고,
#      자연스러운 수리가 "게이트를 느슨하게" 라서 다음 라운드에 vacuous green 이 된다.
#   ② 게이트가 더 느슨 → **vacuous green**. 런타임은 잡는데 게이트가 못 잡는다. 더 나쁘다.
# 실측 ②(이 러너에 실제로 있던 구멍, 수정 전):
#   - YAML **리터럴 블록 스칼라** `|` 는 파서가 줄바꿈을 보존한다.
#     `hole_probe_ko: |\n  이제 그만 앓고 빨리\n  회복해서 다시 일해` → G6 PASS(구멍).
#     접힘 스칼라 `>-` 는 파서가 먼저 접어서 무사했다 — 그래서 `>-` 만 보면 안전해 보인다.
#   - 마크다운을 줄 단위로 읽는 G6d/G9d 는 금지구가 줄바꿈을 건너면 못 잡았다 → PASS(구멍).
# 그래서 매칭하는 모든 지점이 이 함수 하나를 통과한다.
_WS = re.compile(r"\s+")


def norm_ws(s: str) -> str:
    return _WS.sub(" ", str(s).strip())


# 양보 부정 면제 — **런타임 ForbiddenTokenScanner.CONCESSIVE_NEGATION 의 거울이다.**
#
# 한국어는 부정이 어간 뒤에 `-지 않/못` 으로 붙어서, 어간에서 끊은 토큰(`빨리 회복`)은
# 그 축의 정당한 위로("빨리 회복하지 않아도 됩니다")까지 반드시 삼킨다. 런타임이 그걸
# 면제하므로 게이트도 같이 면제해야 한다 — 게이트가 런타임보다 **느슨하면** vacuous green
# 이고, **엄격하면** false red 다. 둘 다 게이트를 못 믿게 만든다.
#
# 부정만으로 면제하지 않고 양보 어미 `-도` 까지 요구하는 이유는 런타임 쪽 주석 참조
# (`-지 않으면 안 된다` 는 이중부정이라 뜻이 압박 그대로다).
_CONCESSIVE_NEGATION = re.compile(r"[가-힣]{0,3}지\s?(?:않|못)[가-힣]{0,3}도")
_CONCESSIVE_LOOKAHEAD = 12


def violation_index(haystack: str, needle: str) -> int:
    """정규화된 `haystack` 에서 양보 부정으로 면제되지 *않는* 첫 `needle` 위치. 없으면 -1.

    같은 토큰이 한 문장에 두 번 나오면 앞이 면제돼도 뒤가 위반일 수 있으므로 이어서 찾는다.
    """
    if not needle:
        return -1
    frm = 0
    while True:
        at = haystack.find(needle, frm)
        if at < 0:
            return -1
        end = at + len(needle)
        if not _CONCESSIVE_NEGATION.match(haystack, end, end + _CONCESSIVE_LOOKAHEAD):
            return at
        frm = at + 1


# ──────────────────────────────────────────────────────────────────────────────
# legacy 면제 — **스크립트 소스에 하드코딩한다. 설정 파일로 못 켠다.**
#
# 왜 설정 파일이 아닌가: 게이트를 끄는 스위치를 설정 파일에 두면 designer 가 초록으로
# 가는 가장 싼 길이 그 스위치가 된다. 이건 ★★(게이트가 안전 저하를 보상) 의 재발이다.
# 여기 이름이 없는 인물이 `ruleset: legacy-baseline` 을 선언하면 G0cfg 가 FAIL 한다.
#
# 이 집합에 있는 3인물은 **토큰 규약(§5(a))·5-Scene 규약(§8-2)·상시 R1 리스너(§4 R1)가
# 도입되기 전에 머지**됐다. 실측: joseph/david/moses 는 lint_forbidden_tokens 0종,
# R1 리스너 1/5~1/6 scene, david·moses 는 scene6 보유.
# 이 상태를 FAIL 로 찍으면 34건의 red 가 상시로 남고, 그건 "게이트가 원래 그래"로 학습돼
# 다음 진짜 red 를 무시하게 만든다(⑮ 가 경고한 false red 의 정확한 메커니즘).
# → 신규 규약 게이트는 이 3인물에 대해 BLOCKED(판정 유보). **PASS 로 세지 않는다.**
#   부채를 보고 싶으면 `--strict` 로 돌린다.
LEGACY_BASELINE = frozenset({"joseph", "moses", "david"})

# legacy-baseline 에서 판정을 유보하는 게이트 = "신규 인물 규약" 게이트.
# 여기 없는 게이트(G3·G4·G8·G10·G11 등)는 legacy 에도 그대로 적용된다 —
# 그건 세 인물이 이미 100% 지키고 있음을 실측으로 확인한 항목이다.
NEWCHAR_ONLY_GATES = frozenset(
    {"G0a", "G1b", "G2", "G2v", "G3b", "G3c", "G3d", "G5a-i", "G5a-ii", "G5d",
     "G5e", "G6d", "G9d", "G7", "G8", "G10"}
)


# ──────────────────────────────────────────────────────────────────────────────
# 결과 표현
# ──────────────────────────────────────────────────────────────────────────────
@dataclass
class Result:
    gate: str
    status: str
    reason: str = ""
    details: list[str] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return self.status == PASS


def ok(gate: str, reason: str = "") -> Result:
    return Result(gate, PASS, reason)


def fail(gate: str, reason: str, details: Iterable[str] = ()) -> Result:
    return Result(gate, FAIL, reason, list(details))


def blocked(gate: str, reason: str, details: Iterable[str] = ()) -> Result:
    return Result(gate, BLOCKED, reason, list(details))


class GateBlocked(Exception):
    """게이트 본체가 판정 불가를 신호할 때. Ctx.iterate() 가 던진다."""

    def __init__(self, reason: str):
        super().__init__(reason)
        self.reason = reason


# ──────────────────────────────────────────────────────────────────────────────
# YAML 유틸 — 전부 파서 경유
# ──────────────────────────────────────────────────────────────────────────────
def load_yaml(path: str) -> Any:
    with open(path, encoding="utf-8") as fh:
        return yaml.safe_load(fh)


def yaml_values(node: Any) -> list[str]:
    """스칼라 *값* 만 평탄화. 키·주석은 안 나온다(⑤ 대응).

    숫자도 문자열로 뽑는다 — 따옴표 없는 1393 을 놓치지 않기 위해."""
    out: list[str] = []

    def walk(n: Any) -> None:
        if isinstance(n, dict):
            for v in n.values():
                walk(v)
        elif isinstance(n, list):
            for v in n:
                walk(v)
        elif n is not None:
            out.append(str(n))

    walk(node)
    return out


def yaml_keys(node: Any) -> set[str]:
    """문서 안에 실제로 등장하는 **매핑 키** 전부(깊이 무관). 주석은 안 잡힌다."""
    out: set[str] = set()

    def walk(n: Any) -> None:
        if isinstance(n, dict):
            for k, v in n.items():
                out.add(str(k))
                walk(v)
        elif isinstance(n, list):
            for v in n:
                walk(v)

    walk(node)
    return out


def collect_key(node: Any, key: str) -> list[Any]:
    """깊이 무관하게 `key` 의 값을 전부 수집(⑮ — 최상위 조회 금지)."""
    out: list[Any] = []

    def walk(n: Any) -> None:
        if isinstance(n, dict):
            for k, v in n.items():
                if str(k) == key:
                    out.append(v)
                else:
                    walk(v)
        elif isinstance(n, list):
            for v in n:
                walk(v)

    walk(node)
    return out


def _end_line(mark: Any) -> int:
    """end_mark 는 다음 토큰 시작을 가리킬 수 있다. 열이 0이면 앞 줄이 실제 끝."""
    return mark.line - 1 if mark.column == 0 else mark.line


def _node_last_line(n: Any) -> int:
    if isinstance(n, yaml.ScalarNode):
        return _end_line(n.end_mark)
    if isinstance(n, yaml.SequenceNode):
        return max([_node_last_line(x) for x in n.value] or [_end_line(n.start_mark)])
    if isinstance(n, yaml.MappingNode):
        return max(
            [max(_node_last_line(k), _node_last_line(v)) for k, v in n.value]
            or [_end_line(n.start_mark)]
        )
    return _end_line(n.end_mark)


def carveout_lines(path: str, key: str) -> set[int]:
    """`key` 의 키 줄 + **값 노드가 차지한 모든 줄**(0-based).

    ★★ 의 수리. 줄 단위 `grep -v lint_forbidden_tokens` 는 키 1줄만 걷어내는데
    YAML 블록 리스트는 키 1줄 + 항목 N줄이다 → 항목 N줄이 G6 히트로 남고,
    designer 가 초록으로 가는 가장 쉬운 길이 '토큰을 지우는 것'이 된다.
    """
    try:
        with open(path, encoding="utf-8") as fh:
            root = yaml.compose(fh)
    except Exception:
        return set()
    lines: set[int] = set()

    def walk(n: Any) -> None:
        if isinstance(n, yaml.MappingNode):
            for k, v in n.value:
                if isinstance(k, yaml.ScalarNode) and k.value == key:
                    for i in range(k.start_mark.line, _node_last_line(v) + 1):
                        lines.add(i)
                else:
                    walk(v)
        elif isinstance(n, yaml.SequenceNode):
            for v in n.value:
                walk(v)

    if root is not None:
        walk(root)
    return lines


def read_lines(path: str) -> list[str]:
    with open(path, encoding="utf-8") as fh:
        return fh.read().splitlines()


# 저작 메타데이터 키 — **렌더되지 않는다.** 여기 담긴 문자열은 사용자에게 도달할 수 없다.
#
# G6/G9 가 이 키들을 검사하면 살아있는 콘텐츠가 통째로 red 가 된다. 실측:
#   content/elijah/scene5.yml:120  description: "'다시 이겨라/빨리 회복/사역 복귀' 어휘 0건 …"
# = **금지 토큰을 금지한다고 선언한 그 줄** 이 금지 토큰 히트로 잡힌다.
# COMMON §10-0 ④ 도 "description 이 막을 문구를 직접 인용한 토큰형" 을 정상으로 전제한다.
# 이건 게이트를 느슨하게 하는 게 아니라 ⑤/⑱ 이 세운 원칙("파서가 보는 것만 값이다")을
# G6/G9 에도 일관되게 적용하는 것이다 — 주석과 저작 메모는 제품 텍스트가 아니다.
# ⚠️ 대신 좁게 유지하라. `text_ko`·`*_text_ko`·`card_text_ko` 같은 **렌더되는 키는 절대 넣지 마라.**
NONUSER_KEYS = frozenset(
    {
        "description",
        "note",
        "notes",
        "safety_note",
        "structural_check",
        "rule",
        "comment",
        "rationale",
        "lint_forbidden_tokens",
    }
)

# 서브트리 단위 카브아웃 — **키 이름이 아니라 부위** 로 면제한다.
#
# `theology_footer_refs` 는 신학 검토자용 주석 블록이다(citation_id·source·note·
# anchor_block·handling·disputed_points[].summary). 실측: 이 키들은 라이브 레포에서
# 100% 이 블록 아래에만 있고, 정본인 ContentSafetyGateEnforcementTest.kt 의
# 사용자 노출 정의(`*_ko` + static_text + reflection_prompt)에도 들지 않는다.
#
# 검토자 주석은 **금지 토큰을 인용해야 한다** — "'의심하면 책망받는다' 로 단순화하지
# 않는다" 가 그 블록이 존재하는 이유다. 인용을 위반으로 세면 designer 가 초록으로 가는
# 가장 싼 길이 *검토 주석을 지우는 것* 이 되고, 게이트가 안전 저하에 보상을 준다(★★).
#
# 단, 이 블록 아래라도 `*_ko` 는 **면제하지 않는다.** 실측으로 raw_text_ko·note_ko·ko
# 가 여기 있고, 그건 렌더될 수 있는 한국어 본문이다. 부위로 좁히되 렌더 가능성은 남긴다.
NONUSER_SUBTREES = frozenset({"theology_footer_refs"})


def _in_nonuser_subtree(key: str, anc: tuple[str, ...], subtrees: set) -> bool:
    """조상 경로가 검토자 주석 서브트리에 들어 있고, 그 leaf 가 `*_ko` 가 아닌가."""
    if key.endswith("_ko"):
        return False
    return any(a in subtrees for a in anc)


def scalar_nodes(path: str) -> list[tuple[int, str, str, tuple[str, ...]]]:
    """(1-based 줄번호, 직속 매핑 키, 스칼라 값, 조상 키 경로) — **파서가 본 값만.**

    YAML 주석은 애초에 결과에 없다. G3b/G3c/G3d 가 주석을 값으로 오인해 뚫린
    ⑤⑱ 의 재발을 스캔 게이트에서도 구조적으로 불가능하게 만든다.

    조상 경로를 같이 내보내는 이유: 키 이름만으로는 카브아웃을 **부위로** 좁힐 수 없다.
    `summary` 라는 이름 하나를 전역 면제하면 나중에 렌더되는 곳에 같은 이름이 생겨도
    조용히 면제된다. 경로가 있으면 "theology_footer_refs **아래의**" 로 붙일 수 있다."""
    try:
        with open(path, encoding="utf-8") as fh:
            root = yaml.compose(fh)
    except Exception:
        return []
    out: list[tuple[int, str, str, tuple[str, ...]]] = []

    def walk(n: Any, key: str, anc: tuple[str, ...]) -> None:
        if isinstance(n, yaml.MappingNode):
            for k, v in n.value:
                kn = k.value if isinstance(k, yaml.ScalarNode) else key
                walk(v, str(kn), anc + (str(kn),))
        elif isinstance(n, yaml.SequenceNode):
            for v in n.value:
                walk(v, key, anc)
        elif isinstance(n, yaml.ScalarNode):
            if n.value is not None:
                out.append((n.start_mark.line + 1, key, str(n.value), anc))

    if root is not None:
        walk(root, "", ())
    return out


# ──────────────────────────────────────────────────────────────────────────────
# 실행 컨텍스트
# ──────────────────────────────────────────────────────────────────────────────
class Ctx:
    def __init__(self, root: str, cfg: dict, strict: bool = False):
        self.root = os.path.abspath(root)
        self.cfg = cfg
        self.strict = strict
        self.name: str = str(cfg.get("character", "")).strip()
        self.ruleset: str = str(cfg.get("ruleset", "newchar-v5")).strip()
        self._yaml_cache: dict[str, Any] = {}

    # ── 경로 ────────────────────────────────────────────────────────────────
    def p(self, *parts: str) -> str:
        return os.path.join(self.root, *parts)

    def exists(self, rel: str) -> bool:
        return os.path.exists(self.p(rel))

    @property
    def content_dir(self) -> str:
        return self.p("content", self.name)

    def scene_files(self) -> list[str]:
        import glob as _g

        return sorted(_g.glob(os.path.join(self.content_dir, "scene*.yml")))

    def scene_file(self, n: int) -> str:
        return os.path.join(self.content_dir, f"scene{n}.yml")

    def doc_paths(self) -> list[str]:
        docs = self.cfg.get("docs") or {}
        return [self.p(v) for v in docs.values() if v]

    # ── 설정 접근 ───────────────────────────────────────────────────────────
    def cfg_list(self, key: str) -> list:
        v = self.cfg.get(key)
        if v is None:
            return []
        if not isinstance(v, list):
            raise GateBlocked(
                f"설정 `{key}` 가 리스트가 아니다 (실제 {type(v).__name__}: {v!r}). "
                f"⑨ 재발 — 목록은 전부 리스트로 선언해야 한다"
            )
        return v

    def iterate(self, key: str, label: str, minimum: int = 1) -> list:
        """**빈 순회는 BLOCKED** — 게이트 전체의 일반 규칙(⑳㉒).

        공집합에 대한 전칭명제는 항상 참이다. 그걸 '충족'으로 인쇄하는 게
        vacuous green 의 가장 흔한 형태고, 이 파이프라인에서 3번 났다."""
        items = self.cfg_list(key)
        if len(items) < minimum:
            raise GateBlocked(
                f"설정 `{key}` 원소 {len(items)}개 (<{minimum}) — {label} 판정 불가. "
                f"순회 0회를 통과로 읽지 않는다"
            )
        return items

    # ── YAML ────────────────────────────────────────────────────────────────
    def yml(self, path: str) -> Any:
        if path not in self._yaml_cache:
            self._yaml_cache[path] = load_yaml(path) or {}
        return self._yaml_cache[path]

    def require_targets(self, *rels: str) -> None:
        missing = [r for r in rels if not os.path.exists(self.p(r) if not os.path.isabs(r) else r)]
        if missing:
            raise GateBlocked("타겟 부재: " + ", ".join(missing))

    def require_scenes(self) -> list[str]:
        files = self.scene_files()
        if not files:
            raise GateBlocked(f"타겟 부재: content/{self.name}/scene*.yml (0개)")
        return files

    def rel(self, path: str) -> str:
        try:
            return os.path.relpath(path, self.root)
        except ValueError:
            return path


# ──────────────────────────────────────────────────────────────────────────────
# 게이트 구현
#   함수 시그니처: (ctx) -> Result.  GateBlocked 를 던지면 BLOCKED 로 잡힌다.
# ──────────────────────────────────────────────────────────────────────────────
REQUIRED_CFG = ("character", "ruleset", "docs")


def g0cfg(c: Ctx) -> Result:
    """설정 자체 검증 + legacy 면제 남용 차단."""
    problems = []
    for k in REQUIRED_CFG:
        if not c.cfg.get(k):
            problems.append(f"필수 설정 누락: {k}")
    if c.ruleset not in ("newchar-v5", "legacy-baseline"):
        problems.append(f"알 수 없는 ruleset: {c.ruleset!r}")
    if c.ruleset == "legacy-baseline" and c.name not in LEGACY_BASELINE:
        problems.append(
            f"`ruleset: legacy-baseline` 은 스크립트 소스의 LEGACY_BASELINE "
            f"({', '.join(sorted(LEGACY_BASELINE))}) 에만 허용된다 — "
            f"{c.name!r} 은(는) 신규 인물이므로 면제 불가"
        )
    # ㉝ — 배제 선언의 scope 누락/오기는 **설정 오류**다. 여기서 FAIL 로 잡고,
    #      배제 게이트(G0b/G0e/G9/G9d)는 실행하지 않고 BLOCKED 로 둔다.
    problems.extend(_exclusions(c)[1])
    # 목록형 설정이 스칼라로 들어오는 ⑨ 재발을 여기서 한 번에 잡는다
    for k in (
        "exclusions",
        "forbidden",
        "polite_evasive",
        "trigger_scenes",
        "crisis_reminder_scenes",
        "token_examples",
        "crisis_numbers",
    ):
        v = c.cfg.get(k)
        if v is not None and not isinstance(v, list):
            problems.append(
                f"`{k}` 가 리스트가 아니다 ({type(v).__name__}: {v!r}) — "
                f"⑨ (`TRIGGER_SCENES='1 5'` 스칼라) 재발"
            )
    if problems:
        return fail("G0cfg", "설정 결함", problems)
    return ok("G0cfg", f"ruleset={c.ruleset}")


def g0a(c: Ctx) -> Result:
    """Scene 파일 수 (§8-2). 6개면 5-Scene 을 전제한 게이트가 조용히 깨진다."""
    files = c.require_scenes()
    want = int(c.cfg.get("scene_count", 5))
    if len(files) != want:
        return fail(
            "G0a",
            f"Scene 파일 {len(files)}개 (기대 {want})",
            [c.rel(f) for f in files],
        )
    return ok("G0a", f"{len(files)}개")


def _verses_sections(c: Ctx) -> dict[str, str]:
    """verses 파일을 **네 개의 blob** 으로 쪼갠다.

    ㉝ — 배제 문자열은 "어느 텍스트를 상대로 재는지"까지가 정의다. 예전 구현은
    `cut -f2-` 에 해당하는 **본문 열만** 만들어 놓고 참조 표기(`단 6:24`)를 거기에
    대고 찾았다. 라벨을 뗀 열에는 원리상 없으므로 그 판정은 **영원히 초록**이다.
    실측 재현: USED 구간에 `단 6:24<TAB>…` 를 심었을 때 본문 열 판정 3/3 통과,
    라벨 열 판정 2/3 즉시 FAIL.

    그래서 열을 둘 다 보존하고, 어느 열에 대고 잴지는 선언의 `scope:` 가 정한다.
    """
    vf = c.cfg.get("verses_file")
    if not vf:
        raise GateBlocked("설정 `verses_file` 미정의 — 배제 문자열 실재/과차단 검증 불가")
    path = c.p(vf)
    if not os.path.exists(path):
        raise GateBlocked(f"타겟 부재: {vf}")
    lines = read_lines(path)
    if not any(l.startswith("## USED") for l in lines) or not any(
        l.startswith("## EXCLUDED") for l in lines
    ):
        raise GateBlocked("verses 파일에 `## USED` / `## EXCLUDED` 헤더가 없다 — 방향 검증 불가")
    cols: dict[str, list[str]] = {
        "used_ref": [],
        "used_text": [],
        "excluded_ref": [],
        "excluded_text": [],
    }
    side: str | None = None
    tab_seen = False
    for l in lines:
        if l.startswith("## USED"):
            side = "used"
            continue
        if l.startswith("## EXCLUDED"):
            side = "excluded"
            continue
        if side is None or not l.strip():
            continue
        if "\t" in l:
            tab_seen = True
            ref, text = l.split("\t", 1)
            cols[f"{side}_ref"].append(ref.strip())
            cols[f"{side}_text"].append(text)
        else:
            raise GateBlocked(
                f"verses 파일 {vf} 에 TAB 없는 데이터 줄이 있다: {l.strip()[:60]!r} — "
                f"참조 열과 본문 열을 분리할 수 없다"
            )
    if not tab_seen:
        raise GateBlocked(
            "verses 파일이 TAB 구분이 아니다 — 절 라벨이 안 떼져 배제 문자열이 "
            "자기 라벨에 매칭돼 실재로 오판된다(②)"
        )
    return {k: "\n".join(v) for k, v in cols.items()}


# ── ㉝ 배제 문자열 선언: `scope:` 필수, 기본값 없음 ────────────────────────────
#   verse_ref    : verses 파일의 **참조(라벨) 열**  — `단 6:24` 같은 표기
#   verse_text   : verses 파일의 **본문 열**        — 성경 본문 조각
#   content_leaf : 산출물 YAML 의 **스칼라 leaf**   — 렌더되는 문장
# 기본값을 두지 않는 이유: 기본값을 주는 순간 "본문 열에 참조 표기를 대고 찾는"
# 지금의 결함이 조용히 그대로 재발한다. 누락은 통과가 아니라 **설정 오류**다.
EXCL_SCOPES = ("verse_ref", "verse_text", "content_leaf")
EXCL_EXPECTS = ("excluded_only", "absent")
VERSE_SCOPES = ("verse_ref", "verse_text")


def _exclusions(c: Ctx) -> tuple[list[dict], list[str]]:
    """`exclusions:` 선언을 정규화한다. 반환 (엔트리, 설정 문제 목록).

    문제가 하나라도 있으면 **게이트를 실행하지 않는다** — g0cfg 가 FAIL 로 보고하고
    나머지 배제 게이트는 BLOCKED 다. 잘못 선언된 목록으로 낸 초록은 초록이 아니다.
    """
    problems: list[str] = []
    for legacy in ("excluded_text", "excluded_ref"):
        if c.cfg.get(legacy) is not None:
            problems.append(
                f"구식 키 `{legacy}` — 키 이름이 대상 열을 암묵적으로 정하던 형태다(㉝). "
                f"`exclusions:` 로 옮기고 각 원소에 `scope:` 를 명시해라"
            )
    raw = c.cfg.get("exclusions")
    if raw is None:
        return [], problems
    if not isinstance(raw, list):
        problems.append(f"`exclusions` 가 리스트가 아니다 ({type(raw).__name__}: {raw!r})")
        return [], problems
    out: list[dict] = []
    for i, e in enumerate(raw):
        if not isinstance(e, dict):
            problems.append(f"exclusions[{i}] 가 매핑이 아니다: {e!r} — `value`/`scope`/`expect` 필요")
            continue
        val = str(e.get("value", "")).strip()
        scope = e.get("scope")
        expect = e.get("expect")
        if not val:
            problems.append(f"exclusions[{i}] `value` 가 비었다 — 빈 문자열은 모든 줄에 매칭된다(⑫)")
        if scope is None:
            problems.append(
                f"exclusions[{i}] ({val!r}) `scope` 누락 — 어느 텍스트를 상대로 재는지가 "
                f"선언의 일부다. 허용값 {'/'.join(EXCL_SCOPES)}"
            )
        elif scope not in EXCL_SCOPES:
            problems.append(f"exclusions[{i}] 알 수 없는 scope {scope!r} (허용 {'/'.join(EXCL_SCOPES)})")
        if expect is None:
            problems.append(
                f"exclusions[{i}] ({val!r}) `expect` 누락 — 허용값 {'/'.join(EXCL_EXPECTS)}"
            )
        elif expect not in EXCL_EXPECTS:
            problems.append(f"exclusions[{i}] 알 수 없는 expect {expect!r}")
        if scope == "content_leaf" and expect == "excluded_only":
            problems.append(
                f"exclusions[{i}] ({val!r}) content_leaf 에는 USED/EXCLUDED 구분이 없다 — "
                f"`expect: absent` 만 가능하다"
            )
        if val and scope in EXCL_SCOPES and expect in EXCL_EXPECTS:
            out.append({"value": val, "scope": scope, "expect": expect})
    return out, problems


def _exclusion_entries(c: Ctx) -> list[dict]:
    """게이트용 접근자 — 설정 오류면 실행하지 않고 BLOCKED."""
    entries, problems = _exclusions(c)
    if problems:
        raise GateBlocked("`exclusions` 설정 오류로 실행하지 않는다: " + " / ".join(problems[:4]))
    if not entries:
        raise GateBlocked(
            "설정 `exclusions` 미정의/0건 — 배제 목록 없이 0건은 무의미하다(빈 순회는 BLOCKED)"
        )
    return entries


def g0b(c: Ctx) -> Result:
    """배제 문자열 양방향 검증 (§7-1-a) — **선언된 scope 의 열에 대고** 잰다.

    expect: excluded_only → EXCLUDED 쪽에 **실재**해야 (없으면 영구 0건 = 가짜 게이트)
                            + USED 쪽에는 **부재**해야 (있으면 자기 콘텐츠 과차단)
    expect: absent        → USED 쪽에 부재하기만 하면 된다
    scope: content_leaf   → verses 파일로는 판정할 수 없다. G9 가 맡는다.
    """
    entries = _exclusion_entries(c)
    subject = [e for e in entries if e["scope"] in VERSE_SCOPES]
    if not subject:
        raise GateBlocked(
            f"scope 가 {'/'.join(VERSE_SCOPES)} 인 배제 선언이 0건 — verses 파일 대조 대상 없음"
        )
    cols = _verses_sections(c)
    bad = []
    for e in subject:
        v, sc = e["value"], e["scope"]
        used_blob = cols[f"used_{'ref' if sc == 'verse_ref' else 'text'}"]
        excl_blob = cols[f"excluded_{'ref' if sc == 'verse_ref' else 'text'}"]
        col = "참조(라벨) 열" if sc == "verse_ref" else "본문 열"
        if e["expect"] == "excluded_only" and v not in excl_blob:
            bad.append(f"[{sc}] 배제장 {col}에 부재(가짜 게이트 — 영구 0건): {v!r}")
        if v in used_blob:
            bad.append(f"[{sc}] 사용장 {col}에 존재(과차단): {v!r}")
    if bad:
        return fail("G0b", f"배제 문자열 {len(bad)}건 방향 위반", bad)
    by = {s: sum(1 for e in subject if e["scope"] == s) for s in VERSE_SCOPES}
    return ok("G0b", f"verse_ref {by['verse_ref']}건 · verse_text {by['verse_text']}건 방향 일치")


def _live_corpus(c: Ctx) -> list[str]:
    """이 인물 것을 뺀 **라이브 콘텐츠** 파일 목록 — 과차단 사전 스캔용."""
    import glob as _g

    own_docs = {os.path.abspath(p) for p in c.doc_paths()}
    own_dir = os.path.abspath(c.content_dir)
    out = []
    for pat in ("content/*/*.yml", "content/*/*.md", "docs/MVP-*.md"):
        for p in _g.glob(os.path.join(c.root, pat)):
            ap = os.path.abspath(p)
            if ap.startswith(own_dir + os.sep) or ap in own_docs:
                continue
            out.append(p)
    return sorted(out)


def g0e(c: Ctx) -> Result:
    """㉓ — [A]군 짧은 배제 문자열이 무고한 라이브 콘텐츠를 과차단하는지 사전 스캔.

    실측 사례: 창 34(디나) 배제용 2글자 `누이` 가 content/david/scene1.yml 의
    시편 23:2a 자막을 친다. 게이트가 시키는 자명한 수리가 '멀쩡한 시편 자막을 지우는 것'이 된다.
    """
    a = [e["value"] for e in _exclusion_entries(c)]
    corpus = _live_corpus(c)
    if not corpus:
        raise GateBlocked(
            "비교할 라이브 콘텐츠가 0개 — 과차단 사전 스캔 불가(빈 코퍼스를 통과로 읽지 않는다)"
        )
    min_len = int(c.cfg.get("min_exclusion_len", 3))
    bad = []
    for s in a:
        s = str(s)
        if len(s) < min_len:
            bad.append(f"길이 하한 미달({len(s)}<{min_len}자): {s!r} — 과차단 위험군")
        hits = []
        for path in corpus:
            for i, line in enumerate(read_lines(path), 1):
                if s in line:
                    hits.append(f"    {c.rel(path)}:{i}: {line.strip()[:90]}")
                    if len(hits) >= 3:
                        break
            if len(hits) >= 3:
                break
        if hits:
            bad.append(f"라이브 콘텐츠 과차단: {s!r}")
            bad.extend(hits)
    if bad:
        return fail("G0e", "[A]군 배제 문자열 과차단 위험", bad)
    return ok("G0e", f"{len(a)}건 · 코퍼스 {len(corpus)}파일 무충돌")


def g0c(c: Ctx) -> Result:
    """§5-2 — 토큰이 자기 위험 예문을 실제로 잡는가. + ㉞ 목록 자체의 무결성.

    ㉞ 실측: 배열 리터럴 `TOKEN_EXAMPLE_PAIRS=( "연단하시|…예문…" … )` 에서 bash 는
    `…`(U+2026) 를 **생략 기호가 아니라 두 번째 원소**로 읽는다. `|` 가 없어서 tok 과
    ex 가 둘 다 `…` 가 되고 `case "…" in *"…"*)` 가 자기 자신에 매칭돼 통과한다.
    실행 확인: 원소 2개 · 2건 전부 PASS — 33종을 검사한다던 게이트가 실제로 검사한 건 1종.

    그래서 짝의 내용을 보기 전에 **목록 자체**를 먼저 검사한다.
      (a) 선언 개수(`token_examples_declared`) ≠ 실제 원소 수  → FAIL
      (b) tok == ex 인 원소                                    → 자기매칭 FAIL
      (c) 구분자(`|`) 없는 원소                                → FAIL
    (a) 가 없으면 "줄여 적다가 원소가 사라진" 사고가 조용히 통과한다 — 개수는 선언해야
    비교할 수 있다. 기본값을 두지 않는다.
    """
    pairs = c.iterate("token_examples", "토큰:예문 대조")
    declared = c.cfg.get("token_examples_declared")
    if declared is None:
        raise GateBlocked(
            "설정 `token_examples_declared` 미정의 — 실제 원소 수와 대조할 선언 개수가 없다. "
            "㉞(줄여 적다가 원소가 사라지는 사고)를 판정할 수 없다"
        )
    bad = []
    if not isinstance(declared, int) or int(declared) != len(pairs):
        bad.append(
            f"선언 개수 {declared!r} ≠ 실제 원소 {len(pairs)}개 — "
            f"㉞ 재발(축약·줄임으로 원소가 사라졌다). N종을 검사한다는 주장이 거짓이 된다"
        )
    for i, pr in enumerate(pairs):
        if isinstance(pr, dict):
            if "token" not in pr or "example" not in pr:
                bad.append(f"[{i}] token/example 키가 없다: {pr!r}")
                continue
            tok, ex = str(pr["token"]), str(pr["example"])
        elif isinstance(pr, str):
            # bash 배열에서 옮겨온 `"tok|example"` 형태도 받는다 — 단 구분자가 있어야 한다
            if "|" not in pr:
                bad.append(
                    f"[{i}] 구분자 `|` 없는 원소: {pr!r} — 토큰과 예문이 갈라지지 않는다(㉞). "
                    f"생략 기호를 그대로 옮겨 적으면 그게 **원소**가 된다"
                )
                continue
            tok, ex = (s.strip() for s in pr.split("|", 1))
        else:
            bad.append(f"[{i}] 매핑도 문자열도 아니다: {pr!r}")
            continue
        if not tok.strip() or not ex.strip():
            bad.append(f"[{i}] 토큰/예문이 비었다: {tok!r} ← {ex!r}")
            continue
        # …(U+2026) 축약 기호는 자기매칭의 특수 사례지만 원인 진단이 다르므로 먼저 본다
        if tok in ("…", "...") or ex in ("…", "..."):
            bad.append(f"[{i}] 축약기호가 토큰/예문에 그대로 남아 있다: {tok!r} ← {ex!r}")
            continue
        # ㊱ — 포함/자기매칭 **둘 다** 실 스캐너와 같은 정규화 후에 판정한다.
        #   자기매칭을 strip 비교로만 하면 `"빨리 회복"` vs `"빨리  회복"`(두 칸)이
        #   자기매칭으로 안 걸리고 엉뚱하게 "토큰이 예문을 못 잡음" 으로 잡힌다 —
        #   FAIL 이라 안전해 보이지만 **진단이 틀린 red** 다.
        ntok, nex = norm_ws(tok), norm_ws(ex)
        if ntok == nex:
            bad.append(
                f"[{i}] 자기매칭: 공백 정규화하면 토큰과 예문이 같은 문자열이다 {tok!r} ← {ex!r} — "
                f"무조건 통과한다. 예문은 토큰을 **포함한 다른 문장**이어야 한다(㉞)"
            )
            continue
        if ntok not in nex:
            bad.append(f"토큰이 예문을 못 잡음: {tok!r} ← {ex!r} (공백 정규화 후 비교)")
    if bad:
        return fail("G0c", f"{len(bad)}건", bad)
    return ok("G0c", f"선언 {declared}쌍 = 실제 {len(pairs)}쌍, 전부 토큰이 예문을 잡음")


def g0d(c: Ctx) -> Result:
    """G0d — **제품 말투**로 쓴 회피형 위험 문장이 토큰에 걸리는가.

    순회 축은 토큰이 아니라 예문이다(⑩). 토큰마다 존댓말 짝을 요구하면 어휘 자체가
    반말인 토큰은 정의상 짝을 못 만들어 게이트가 통과 불가가 되고, 초록의 유일한 길이
    '그 토큰을 지우는 것'이 된다.
    ★★★ 의 정면 대응 게이트 — 여기가 비면 존댓말 위반 10/10 이 그대로 통과한다.
    """
    n_min = int(c.cfg.get("polite_evasive_min", 1))
    sents = c.iterate("polite_evasive", "제품 말투 위험 문장 커버리지", minimum=n_min)
    toks = [str(t) for t in c.cfg_list("forbidden")]
    if not toks:
        raise GateBlocked("설정 `forbidden` 이 비어 있다 — 커버리지 판정 불가")
    misses = [s for s in map(str, sents) if not any(t in s for t in toks)]
    if misses:
        return fail(
            "G0d",
            f"{len(misses)}/{len(sents)} 문장이 어떤 토큰에도 안 걸린다",
            [f"미포착: {s}" for s in misses],
        )
    return ok("G0d", f"{len(sents)}문장 전부 포착 (토큰 {len(toks)}종)")


def g1b(c: Ctx) -> Result:
    """§8-1 — 마지막 Scene 라우팅에 `when: {<label>: null}` 폴백. 스킵 사용자 보호."""
    files = c.require_scenes()
    last = files[-1]
    d = c.yml(last)
    routes = []
    for br in collect_key(d, "routes"):
        if isinstance(br, list):
            routes.extend([r for r in br if isinstance(r, dict)])
    if not routes:
        raise GateBlocked(f"{c.rel(last)} 에 routes 가 없다 — 폴백 판정 불가")
    for r in routes:
        when = r.get("when")
        if isinstance(when, dict) and any(v is None for v in when.values()):
            return ok("G1b", f"{c.rel(last)} null 폴백 라우트 존재")
    return fail(
        "G1b",
        f"{c.rel(last)} 라우팅에 null 폴백이 없다 — R4 스킵 경로 사용자가 마무리 문구 없이 끝난다",
        [f"when={r.get('when')!r}" for r in routes],
    )


def g2(c: Ctx) -> Result:
    """§4 R1 — 상시 음성 리스너가 **모든** Scene 에. 하드코딩 `-eq 5` 를 쓰지 않는다."""
    files = c.require_scenes()
    key = str(c.cfg.get("r1_listener_id", "R1_voice_self_harm_listener"))
    missing = []
    for f in files:
        ids = {str(g.get("id")) for g in (c.yml(f).get("safety_gates") or []) if isinstance(g, dict)}
        if key not in ids:
            missing.append(c.rel(f))
    if missing:
        return fail("G2", f"{len(missing)}/{len(files)} Scene 에 {key} 부재", missing)
    return ok("G2", f"{len(files)}/{len(files)}")


def _latch_canon(c: Ctx, spec: dict) -> dict:
    """정본 md 의 첫 ```yaml 블록에서 래치 계약 매핑을 꺼낸다.

    `check_ruth_captions.py::_load_canon` 과 같은 규약(첫 ```yaml 블록)을 쓴다.
    **일부러 값을 여기에 사본으로 두지 않는다** — 사본을 두면 정본·설정·콘텐츠
    셋을 손으로 맞춰야 하고, 그건 이 게이트가 잡으려는 실패 그 자체다."""
    rel = str(spec["canon_doc"])
    block = str(spec.get("canon_block", "crisis_latch"))
    path = c.p(rel)
    if not os.path.exists(path):
        raise GateBlocked(f"래치 정본 문서가 없다: {rel}")
    with open(path, encoding="utf-8") as fh:
        m = re.search(r"^```yaml\n(.*?)^```", fh.read(), re.DOTALL | re.MULTILINE)
    if not m:
        raise GateBlocked(f"{rel} 에 ```yaml 데이터 블록이 없다")
    try:
        data = yaml.safe_load(m.group(1))
    except yaml.YAMLError as e:
        raise GateBlocked(f"{rel} 데이터 블록 파싱 실패: {e}") from e
    got = (data or {}).get(block)
    if not isinstance(got, dict) or not got:
        raise GateBlocked(f"{rel} 의 `{block}` 이 비어 있거나 매핑이 아니다")
    return got


def g2v(c: Ctx) -> Result:
    """§4 R1 — 위기 래치 **키의 값**이 Scene 전건에 실렸는가.

    G2 는 `safety_gates[].id` 하나만 본다. 그래서 래치 3키가 통째로 빠지거나
    `post_crisis_latch_scope` 가 `mission` 대신 `session` 으로 실려도 **G2 는
    초록이었다**(`docs/SEED-RAHAB.md` C7 이 기록한 구멍). 룻 README 가 못박은 대로
    「키 존재가 아니라 값이 계약이다」(`content/ruth/README.md:32`).

    설정이 없으면 **BLOCKED 다 — PASS 가 아니다.** 선언하지 않은 인물에 대해
    이 게이트는 재지 않았고, "재지 않았다"를 초록으로 적으면 그게 이 리포가
    가장 싫어하는 형태가 된다.

    설정 형태 (`scripts/gates/<인물>.yml`) — 둘 중 **하나만** 쓴다:

        latch_contract:
          canon_doc: docs/RUTH-LOCKED-STRINGS.md   # 정본 md 의 첫 ```yaml 블록
          canon_block: crisis_latch                # 그 아래 매핑 = Scene 최상위 키·값

        latch_contract:
          scene_keys: { post_crisis_latch_scope: mission, ... }   # 정본 문서가 없는 인물
          gate_keys:  { enforced_at: always, ... }                # safety_gates[] 안

    이 게이트가 주장하지 않는 것: 값이 **옳다**는 것. 정본과 Scene 을 같이 고치면
    통과한다 — 그때 남는 것은 정본의 diff 다(AC 23 과 같은 한계)."""
    spec = c.cfg.get("latch_contract")
    if not spec:
        return blocked(
            "G2v",
            f"{c.name}: `latch_contract` 미선언 — 래치 키의 값을 재지 않았다. "
            "통과가 아니라 판정 유보다(SEED-RAHAB C7)",
        )
    if not isinstance(spec, dict):
        raise GateBlocked(f"`latch_contract` 가 매핑이 아니다 (실제 {type(spec).__name__})")
    if "canon_doc" in spec and "scene_keys" in spec:
        raise GateBlocked(
            "`latch_contract` 에 canon_doc 과 scene_keys 가 같이 있다 — "
            "출처가 둘이면 어느 쪽이 정본인지 게이트가 정할 수 없다. 하나만 남겨라"
        )

    if "canon_doc" in spec:
        scene_keys = _latch_canon(c, spec)
        source = f"{spec['canon_doc']} `{spec.get('canon_block', 'crisis_latch')}`"
    else:
        scene_keys = spec.get("scene_keys") or {}
        source = f"{c.name}.yml `latch_contract.scene_keys`"
        if not isinstance(scene_keys, dict) or not scene_keys:
            raise GateBlocked("`latch_contract.scene_keys` 가 비어 있거나 매핑이 아니다")
    gate_keys = spec.get("gate_keys") or {}
    if not isinstance(gate_keys, dict):
        raise GateBlocked(f"`latch_contract.gate_keys` 가 매핑이 아니다 (실제 {type(gate_keys).__name__})")

    files = c.require_scenes()
    bad: list[str] = []
    for f in files:
        rel = c.rel(f)
        doc = c.yml(f)
        for key, want in scene_keys.items():
            got = collect_key(doc, key)
            if not got:
                bad.append(f"{rel}: `{key}` 없음 (계약값 {want!r})")
            elif any(v != want for v in got):
                bad.append(f"{rel}: `{key}` = {got!r} != {want!r}")
        if not gate_keys:
            continue
        gates = [g for g in (doc.get("safety_gates") or []) if isinstance(g, dict)]
        if not gates:
            bad.append(f"{rel}: `safety_gates[]` 가 없다 — gate_keys 를 잴 대상이 없다")
            continue
        for key, want in gate_keys.items():
            seen = [g[key] for g in gates if key in g]
            if not seen:
                bad.append(f"{rel}: `safety_gates[].{key}` 없음 (계약값 {want!r})")
            elif any(v != want for v in seen):
                bad.append(f"{rel}: `safety_gates[].{key}` = {seen!r} != {want!r}")

    n = len(scene_keys) + len(gate_keys)
    if bad:
        return fail("G2v", f"{n}키 × {len(files)} Scene 중 {len(bad)}건 불일치 (정본 {source})", bad)
    return ok("G2v", f"{n}키 × {len(files)}/{len(files)} Scene 값 일치 (정본 {source})")


CRISIS_TOKEN = "crisis_resources.default"


# 서지 인용 문맥 — `제109호`, `pp.109~135` 는 상담 전화번호가 아니다.
# 실측 false red: content/david/scene5.yml:233 `신학논단 제109호, pp.109~135`,
# docs/MVP-DAVID-CONTENT.md:400 동일 서지. 게이트가 시키는 자명한 수리가
# "멀쩡한 각주의 권호·쪽수를 지우는 것"이 된다 = 게이트 결함이지 콘텐츠 결함이 아니다.
# **설정으로 끄지 못하게 소스에 둔다.** 문맥을 설정으로 넓힐 수 있으면 그게 off-switch 가 된다.
_CITE_BEFORE = ("제", "p.", "pp.", "~", "–", "-", "vol.", "Vol.")
_CITE_AFTER = ("호", "쪽", "페이지", "~", "–", "p")


def _is_citation(text: str, start: int, end: int) -> bool:
    before = text[max(0, start - 4) : start]
    after = text[end : end + 3]
    if before.endswith("제") and after.startswith("호"):
        return True
    if any(before.rstrip().endswith(b) for b in ("p.", "pp.", "vol.", "Vol.", "~", "–")):
        return True
    if after.startswith(("호", "쪽", "페이지", "~", "–")):
        return True
    return False


def g3(c: Ctx) -> Result:
    """§6 — 위기 전화번호 하드코딩 0건. 단어경계 + 서지 인용 문맥 제외."""
    nums = [str(n) for n in (c.cfg.get("crisis_numbers") or ["109", "1393", "1577-0199"])]
    targets = c.require_scenes() + [p for p in c.doc_paths() if os.path.exists(p)]
    if not targets:
        raise GateBlocked("검사 대상 0개")
    pat = re.compile(r"(?<![0-9\-])(" + "|".join(re.escape(n) for n in nums) + r")(?![0-9\-])")
    hits = []
    for path in c.require_scenes():
        # YAML 은 **값 노드만** — 주석에 적힌 번호는 렌더되지 않는다(⑤ 원칙의 일관 적용)
        for line, key, val, _anc in scalar_nodes(path):
            if CRISIS_TOKEN in val:
                continue
            if any(not _is_citation(val, m.start(), m.end()) for m in pat.finditer(val)):
                hits.append(f"{c.rel(path)}:{line}: ({key}) {val.strip()[:100]}")
    for path in [p for p in c.doc_paths() if os.path.exists(p)]:
        for i, line in enumerate(read_lines(path), 1):
            if CRISIS_TOKEN in line:
                continue
            if any(not _is_citation(line, m.start(), m.end()) for m in pat.finditer(line)):
                hits.append(f"{c.rel(path)}:{i}: {line.strip()[:100]}")
    if hits:
        return fail("G3", f"위기 자원 하드코딩 {len(hits)}건", hits)
    return ok("G3", f"{len(targets)}파일 0건")


def g3b(c: Ctx) -> Result:
    """§6-1 — 부재가 아니라 **존재**. 토큰이 최소 2개 Scene 에 값으로 놓였는가(파서)."""
    files = c.require_scenes()
    have = [c.rel(f) for f in files if any(CRISIS_TOKEN in v for v in yaml_values(c.yml(f)))]
    if len(have) < 2:
        return fail(
            "G3b",
            f"{CRISIS_TOKEN} 를 값으로 가진 Scene {len(have)}개 (<2) — 위기 표면이 없다",
            have,
        )
    return ok("G3b", f"{len(have)}/{len(files)} Scene")


def g3c(c: Ctx) -> Result:
    """§6-1 — 마지막 Scene 은 이탈 지점이므로 예외 없음."""
    files = c.require_scenes()
    last = files[-1]
    if any(CRISIS_TOKEN in v for v in yaml_values(c.yml(last))):
        return ok("G3c", c.rel(last))
    return fail("G3c", f"{c.rel(last)} 에 {CRISIS_TOKEN} 값 부재")


def g3d(c: Ctx) -> Result:
    """⑱ — 위기 리마인더 **리터럴 키**. `grep -q 'crisis_reminder'` 는 주석을 값으로 오인한다.

    파서로 키 집합 + id 값 집합을 모아 판정하므로 `# - id: crisis_reminder` 는 안 걸린다.
    ㉑ 주의: 라이브 레포 content/ 에 이 키를 가진 파일은 **0개**다. 판단은 README 참조.
    """
    scenes = c.iterate("crisis_reminder_scenes", "위기 리마인더 Scene")
    key = str(c.cfg.get("crisis_reminder_key", "crisis_reminder"))
    bad = []
    for s in scenes:
        f = c.scene_file(int(s))
        if not os.path.exists(f):
            raise GateBlocked(f"타겟 부재: {c.rel(f)} (crisis_reminder_scenes={scenes})")
        d = c.yml(f)
        present = key in yaml_keys(d) or key in {str(v) for v in collect_key(d, "id")}
        if not present:
            bad.append(f"{c.rel(f)}: 파서가 본 키/id 에 {key!r} 없음")
    if bad:
        return fail("G3d", f"{len(bad)}개 Scene 에 위기 리마인더 부재", bad)
    return ok("G3d", f"{len(scenes)}개 Scene")


def g4(c: Ctx) -> Result:
    files = c.require_scenes()
    bad = []
    for f in files:
        try:
            load_yaml(f)
        except Exception as e:  # noqa: BLE001
            bad.append(f"{c.rel(f)}: {type(e).__name__}: {str(e).splitlines()[0]}")
    if bad:
        return fail("G4", f"{len(bad)}개 파일 파싱 실패", bad)
    return ok("G4", f"{len(files)}파일")


LINT_KEY = "lint_forbidden_tokens"
AXIS_RE = re.compile(r"^R[23](_|$)")  # ⑰ — `^R[23]_` 로 쓰면 id 가 정확히 R2/R3 인 축을 건너뛴다


def _axes(c: Ctx) -> list[tuple[str, str, dict]]:
    """(파일, gate id, gate dict) — R2/R3 축만."""
    out = []
    for f in c.require_scenes():
        for g in c.yml(f).get("safety_gates") or []:
            if not isinstance(g, dict):
                continue
            gid = str(g.get("id", ""))
            if AXIS_RE.match(gid):
                out.append((f, gid, g))
    return out


def g5a_i(c: Ctx) -> Result:
    """§5(i) — 인물 고유 토큰 **합집합** ≥ 6. 깊이 무관 재귀 수집(⑮)."""
    files = c.require_scenes()
    union: set[str] = set()
    for f in files:
        for v in collect_key(c.yml(f), LINT_KEY):
            if isinstance(v, list):
                union.update(str(x) for x in v)
    lo = int(c.cfg.get("min_union_tokens", 6))
    if len(union) < lo:
        return fail(
            "G5a-i",
            f"토큰 합집합 {len(union)}종 (<{lo}) — §5 는 인물 고유 {lo}종 이상을 요구한다",
            sorted(union),
        )
    return ok("G5a-i", f"합집합 {len(union)}종")


def g5a_ii(c: Ctx) -> Result:
    """§5(ii)+⑳ — 선언된 R2/R3 축마다 집행 수단. **축 0개면 BLOCKED**(공회전)."""
    axes = _axes(c)
    if not axes:
        raise GateBlocked(
            "R2/R3 축 0개 — 공집합에 대한 전칭명제는 항상 참이다. 이걸 충족으로 인쇄하지 않는다(⑳)"
        )
    bad = []
    for f, gid, g in axes:
        mode = g.get("enforcement")
        n = len(g.get(LINT_KEY) or [])
        if mode == "structural":
            if not str(g.get("structural_check") or "").strip():
                bad.append(
                    f"{c.rel(f)} :: {gid} — enforcement: structural 인데 structural_check 가 비었다"
                )
        elif n == 0:
            bad.append(
                f"{c.rel(f)} :: {gid} — R2/R3 축 선언 + 토큰 0종(④). "
                f"토큰을 넣거나 enforcement: structural + structural_check 를 적어라"
            )
    if bad:
        return fail("G5a-ii", f"축 {len(bad)}/{len(axes)}건 집행 수단 없음", bad)
    return ok("G5a-ii", f"축 {len(axes)}건 전부 집행 수단 보유")


def g5d(c: Ctx) -> Result:
    """㉗ — `lint_forbidden_tokens` 는 `safety_gates[].id: R2_*/R3_*` **아래**에만.

    최상위에 두면 G5a-i(재귀라 어디 있든 센다)는 통과하고 G5a-ii 는 축 0개로 공회전한다.
    두 게이트가 서로를 가려 준다. 위치를 강제해 그 조합을 불가능하게 만든다.
    """
    files = c.require_scenes()
    placed = 0
    bad = []
    for f in files:
        d = c.yml(f)
        total = len(collect_key(d, LINT_KEY))
        if total == 0:
            continue
        legit = 0
        for g in d.get("safety_gates") or []:
            if isinstance(g, dict) and AXIS_RE.match(str(g.get("id", ""))) and LINT_KEY in g:
                legit += 1
        placed += legit
        if legit < total:
            bad.append(
                f"{c.rel(f)}: {LINT_KEY} {total}곳 중 {total - legit}곳이 "
                f"safety_gates[].id R2_*/R3_* 밖에 있다"
            )
    if bad:
        return fail("G5d", "토큰 목록 위치 위반", bad)
    if placed == 0:
        raise GateBlocked(f"{LINT_KEY} 선언이 0곳 — 위치 판정 대상 없음")
    return ok("G5d", f"{placed}곳 전부 R2/R3 축 아래")


def _appyml_tokens(c: Ctx) -> list[str]:
    rel = str(c.cfg.get("application_yml", "backend/src/main/resources/application.yml"))
    path = c.p(rel)
    if not os.path.exists(path):
        raise GateBlocked(f"타겟 부재: {rel}")
    d = load_yaml(path) or {}
    raw = (((d.get("safety") or {}).get("forbidden-tokens") or {}).get("list")) or ""
    return [t.strip() for t in str(raw).split(",") if t.strip()]


def g5b(c: Ctx) -> Result:
    """§5(b) — 저작 토큰이 application.yml 에 반영됐는가(파서로 값 조회, append only)."""
    toks = c.iterate("forbidden", "application.yml 반영")
    live = _appyml_tokens(c)
    if not live:
        raise GateBlocked("application.yml 의 safety.forbidden-tokens.list 가 비었다")
    # 런타임은 토큰도 정규화해 보관한다(ForbiddenTokenScanner.normalizedTokens) —
    # 공백 수만 다른 같은 토큰을 "미반영"으로 읽으면 false red 다
    nlive = {norm_ws(t) for t in live}
    missing = [str(t) for t in toks if norm_ws(t) not in nlive]
    if missing:
        return fail(
            "G5b",
            f"{len(missing)}/{len(toks)} 토큰이 application.yml 에 없다 (content/ 는 클래스패스가 아니다)",
            missing,
        )
    return ok("G5b", f"{len(toks)}종 전부 반영 (list {len(live)}종)")


def g5c(c: Ctx) -> Result:
    """§5(c) — ForbiddenTokenConfigTest 의 mustBlock 에 인물 문장 + **같은 라인에 인물 식별자**."""
    rel = str(
        c.cfg.get(
            "forbidden_token_test",
            "backend/src/test/kotlin/github/lms/lemuel/xr/safety/application/ForbiddenTokenConfigTest.kt",
        )
    )
    path = c.p(rel)
    if not os.path.exists(path):
        raise GateBlocked(f"타겟 부재: {rel}")
    ident = str(c.cfg.get("test_identifier") or "").strip()
    if not ident:
        raise GateBlocked("설정 `test_identifier` 미정의 — 인물 식별자 판정 불가(§5(c))")
    toks = c.iterate("forbidden", "mustBlock 인물 문장")
    lines = read_lines(path)
    for i, line in enumerate(lines, 1):
        if ident not in line:
            continue
        for t in toks:
            if str(t) in line:
                return ok("G5c", f"{rel}:{i} — 식별자 {ident!r} + 토큰 {str(t)!r} 동일 라인")
    return fail(
        "G5c",
        f"{rel} 에 인물 식별자 {ident!r} 와 인물 토큰이 같은 라인에 있는 항목이 없다 "
        f"(식별자만 있는 한 줄은 통과시키지 않는다)",
    )


def _scan_scene_values(
    c: Ctx, needles: list[str], line_marker: str | None = None
) -> list[str]:
    """content/{인물}/scene*.yml 의 **렌더되는 스칼라 leaf** 에서만 needles 를 찾는다.

    줄 단위 문자열 검색은 쓰지 않는다. 실측(라이브 5인, 토큰 후보 목록):
      줄 단위 content/+docs/ 검색 56건 → 전부 토큰을 *인용한* 주석·문서.
      YAML leaf 로 좁히면 2건 → 그 2건도 `safety_gates[].description`,
      즉 **게이트가 자기 자신의 서술을 콘텐츠로 착각한 것**(⑱ 과 같은 부류).
    그래서 (a) 파서 leaf 만 보고 (b) 게이트를 서술하는 키를 제외한다.

    카브아웃은 키 이름 기준이라 **노드 단위** 다 — 줄 단위 `grep -v lint_forbidden_tokens`
    는 키 1줄만 걷어내고 블록 리스트 항목 N줄을 남긴다(★★). 그러면 designer 가 초록으로
    가는 가장 쉬운 길이 "scene yml 에서 안전 토큰을 지우는 것" 이 되고,
    게이트가 안전 저하에 보상을 주게 된다.

    ⚠️ 매칭은 **공백 정규화 후** 한다(norm_ws — 출처 ForbiddenTokenScanner). 리터럴 블록
    스칼라 `|` 는 파서가 줄바꿈을 보존하므로 원문 그대로 비교하면 `빨리\\n회복` 을 놓친다.
    런타임은 정규화 후 indexOf 라 그걸 잡는다 — 게이트만 못 잡으면 vacuous green 이다.
    """
    nonuser = set(c.cfg.get("nonuser_keys") or NONUSER_KEYS)
    subtrees = set(c.cfg.get("nonuser_subtrees") or NONUSER_SUBTREES)
    nneedles = [(n, norm_ws(n)) for n in needles]
    hits = []
    for f in c.require_scenes():
        raw = read_lines(f) if line_marker else []
        for line, key, val, anc in scalar_nodes(f):
            if key in nonuser:
                continue
            if _in_nonuser_subtree(key, anc, subtrees):
                continue
            if line_marker and _line_declared(raw, line, val, line_marker):
                continue
            nval = norm_ws(val)
            for orig, nn in nneedles:
                if violation_index(nval, nn) >= 0:
                    hits.append(f"{c.rel(f)}:{line}: [{orig}] ({key}) {nval[:90]}")
    return hits


def _line_declared(raw: list[str], line: int, val: str, marker: str) -> bool:
    """이 leaf 의 원본 줄이 **선언 마커 주석**을 달고 있는가(G9 전용).

    문서에는 `EXCLUSION-DECL` 마커로 "이건 배제를 *인용* 하는 줄이지 위반이 아니다"를
    말하는 길이 있다(G9d/_scan_docs). 콘텐츠 leaf 에는 그 길이 없었다. 그래서
    `excluded_passages_note: "요 21:18-19(순교 예고)는 … 배제되었다"` 처럼 **배제를
    설명하느라 배제 문자열을 담은 leaf** 가 G9 를 빨갛게 만들고, 게이트가 시키는 자명한
    수리가 "배제를 기록한 문장을 지우는 것"이 된다. 그건 안전 저하에 보상을 주는 짓이다.
    (실측: content/peter/scene5.yml 의 그 leaf 는 이미 `# <!-- EXCLUSION-DECL -->` 를
    달고 있었다 — 저자는 규약대로 적었는데 집행기에 그 개념이 없었다.)

    ⚠️ 두 가지를 의도적으로 좁혔다.
      · **G9 에서만** 쓴다. `_scan_scene_values` 의 기본값은 여전히 마커 없음이라
        G6(금지 토큰)에는 이 면제가 존재하지 않는다 — 안전 토큰을 주석 한 줄로
        면제하는 길은 열지 않는다.
      · 마커가 **값 안에** 있으면 면제하지 않는다. 그러면 렌더되는 문장에 마커 문자열을
        박아 넣어 자기 자신을 면제하는 길이 생긴다. 주석에만 있어야 한다.
    """
    if not (0 < line <= len(raw)):
        return False
    return marker in raw[line - 1] and marker not in val


def _scan_docs(c: Ctx, needles: list[str], doc_marker: str) -> list[str]:
    """docs/MVP-*.md 의 줄에서 needles 를 찾는다. 마커 라인은 제외(§5-2 / §7-2).

    마크다운에는 "값 노드"가 없다 — 파서로 인용과 사용을 구분할 방법이 없다.
    그래서 규약이 **선언 라인 마커** 를 요구한다. 마커 없이 토큰을 인용한 문서는
    여기서 red 가 되는데, 그건 *안전 위반이 아니라 마커 규율 부채* 다.
    그래서 이 스캔은 G6/G9 가 아니라 **별도 게이트 id(G6d/G9d)** 로 보고한다 —
    같은 red 라도 원인이 오귀속되면 안 된다(⑫ 가 지적한 그 병리).

    ⚠️ **줄 단위로 읽지 않는다.** 실측(수정 전): 문서에서 금지구가 줄바꿈을 건너면
    (`… 빨리\\n회복해서 …`) 줄 단위 스캔은 못 잡고 G6d 가 PASS 였다. 런타임 스캐너는
    공백을 접고 보므로 같은 문장을 잡는다 — 게이트만 못 잡는 **vacuous green** 이다.
    그래서 유지되는 줄들을 이어붙여 정규화한 뒤 검색하고, 매치 위치를 원래 줄 번호로 되돌린다.

    이어붙이면 새 위험이 생긴다: 건너뛴 줄(마커)이나 빈 줄을 사이에 두고 우연히 어구가
    이어져 **없던 매치가 생기는** 것. 그래서 연속되지 않은 줄 사이에는 매치 불가 구분자를
    넣는다. 빈 줄(마크다운 문단 경계)도 같은 이유로 끊는다 — 문단을 건너뛴 어구는 어구가 아니다.
    """
    SEP = "\x00"  # 어떤 needle 에도 나타나지 않는 문자 — 여기서 매칭이 끊긴다
    nneedles = [(n, norm_ws(n)) for n in needles]
    hits = []
    for p in c.doc_paths():
        if not os.path.exists(p):
            raise GateBlocked(f"타겟 부재: {c.rel(p)}")
        chars: list[str] = []
        owner: list[int] = []  # chars[i] 가 온 원래 줄 번호(1-based)
        prev_kept = -2
        prev_space = True
        for idx, line in enumerate(read_lines(p)):
            skip = (doc_marker and doc_marker in line) or not line.strip()
            if skip:
                prev_kept = -2
                continue
            if idx != prev_kept + 1 and chars:
                chars.append(SEP)
                owner.append(idx + 1)
                prev_space = True
            prev_kept = idx
            for ch in line:
                if ch.isspace():
                    if prev_space:
                        continue
                    chars.append(" ")
                    owner.append(idx + 1)
                    prev_space = True
                else:
                    chars.append(ch)
                    owner.append(idx + 1)
                    prev_space = False
            # 줄 경계는 공백 1개 — 런타임 정규화와 같은 취급
            if not prev_space:
                chars.append(" ")
                owner.append(idx + 1)
                prev_space = True
        blob = "".join(chars)
        for orig, nn in nneedles:
            if not nn:
                continue
            start = blob.find(nn)
            while start >= 0:
                # 런타임이 면제하는 양보 부정은 문서에서도 마커 없이 쓸 수 있어야 한다 —
                # "빨리 회복하지 않아도 됩니다" 는 토큰 인용이 아니라 문서 자신의 말이다.
                end = start + len(nn)
                if _CONCESSIVE_NEGATION.match(blob, end, end + _CONCESSIVE_LOOKAHEAD):
                    start = blob.find(nn, start + 1)
                    continue
                ln = owner[start]
                span = blob[start : start + len(nn)]
                multi = "" if owner[min(start + len(nn) - 1, len(owner) - 1)] == ln else " (줄바꿈 넘김)"
                hits.append(f"{c.rel(p)}:{ln}: [{orig}]{multi} {span[:90]}")
                start = blob.find(nn, start + 1)
    return hits


def _needles_forbidden(c: Ctx) -> list[str]:
    toks = [str(t) for t in c.iterate("forbidden", "금지 문구 스캔")]
    if any(not t.strip() for t in toks):
        raise GateBlocked("`forbidden` 에 빈 문자열 원소가 있다 — 모든 줄에 매칭된다(⑫)")
    return toks


def g6(c: Ctx) -> Result:
    """§5 — 금지 문구가 **렌더되는 콘텐츠**(scene yml leaf)에 0건.

    ⚠️ 주장 범위: PASS 는 "선언된 토큰의 **정확한 표층형** 이 렌더 leaf 에 없다" 까지다.
    어미·높임형 변형(`정신 차려`→`정신 차리세요`)·유의어는 이 게이트가 못 잡는다.
    "이 인물의 R2/R3 위반이 없다" 로 읽지 마라.
    """
    toks = _needles_forbidden(c)
    hits = _scan_scene_values(c, toks)
    if hits:
        return fail("G6", f"렌더 leaf 에 금지 문구 {len(hits)}건", hits[:40])
    return ok("G6", f"{len(toks)}종 × 렌더 leaf 0건")


def g6d(c: Ctx) -> Result:
    """§5-2 — 문서가 금지 토큰을 인용할 때 선언 라인 마커를 달았는가.

    안전 위반이 아니라 **문서 마커 규율** 게이트다. G6 와 분리한 이유는 _scan_docs 주석 참조.
    """
    toks = _needles_forbidden(c)
    if not c.doc_paths():
        raise GateBlocked("설정 `docs` 가 비었다 — 문서 스캔 대상 없음")
    hits = _scan_docs(c, toks, str(c.cfg.get("token_decl_marker", LINT_KEY)))
    if hits:
        return fail("G6d", f"마커 없는 토큰 인용 {len(hits)}건", hits[:40])
    return ok("G6d", f"{len(toks)}종 × 문서 마커 규율 준수")


def g7(c: Ctx) -> Result:
    """§4 R4 — 트리거 Scene 에 동의 카드 + 스킵 목적지(리터럴 키, 파서)."""
    scenes = c.iterate("trigger_scenes", "R4 트리거 Scene")
    bad = []
    for s in scenes:
        f = c.scene_file(int(s))
        if not os.path.exists(f):
            raise GateBlocked(f"타겟 부재: {c.rel(f)} (trigger_scenes={scenes})")
        keys = yaml_keys(c.yml(f))
        miss = [k for k in ("consent_card_id", "skip_alternative_scene_id") if k not in keys]
        if miss:
            bad.append(f"{c.rel(f)}: {', '.join(miss)} 부재")
    if bad:
        return fail("G7", f"{len(bad)}/{len(scenes)} 트리거 Scene 미비", bad)
    return ok("G7", f"{len(scenes)}개 Scene")


def g8(c: Ctx) -> Result:
    """⑲ — R5. `llm_optin_only: false` 가 초록이 되지 않도록 **값**을 본다."""
    files = c.require_scenes()
    subject = []
    for f in files:
        d = c.yml(f)
        br = d.get("branches")
        if (isinstance(br, list) and br) or d.get("routes"):
            subject.append(f)
    if not subject:
        raise GateBlocked("비어 있지 않은 branches/routes 를 가진 Scene 이 0개 — R5 판정 대상 없음")
    bad = []
    for f in subject:
        d = c.yml(f)
        optin = [v for v in collect_key(d, "llm_optin_only")]
        static = [str(v) for v in collect_key(d, "default_path")]
        good = any(v is True for v in optin) or any(v == "static_curation" for v in static)
        if not good:
            bad.append(
                f"{c.rel(f)}: llm_optin_only={optin!r} default_path={static!r} — "
                f"`llm_optin_only: true` 또는 `default_path: static_curation` 중 하나가 값으로 있어야 한다"
            )
    if bad:
        return fail("G8", f"{len(bad)}/{len(subject)} Scene R5 기본 경로 미확정", bad)
    return ok("G8", f"{len(subject)}개 분기 Scene")


def g9(c: Ctx) -> Result:
    """§7 — 배제 문자열이 산출물 leaf 에 0건.

    scope 와 무관하게 **선언된 값 전부** 를 산출물에 대고 잰다. verse_ref 든
    verse_text 든 배제한 재료가 렌더되면 안 된다는 요구는 같기 때문이다.
    (scope 는 G0b 의 '어느 열에 실재해야 하는가' 를 정하지, 여기의 대상을 정하지 않는다.)

    배제를 *기록하는* leaf 는 `EXCLUSION-DECL` 주석으로 면제된다 — 문서 쪽 G9d 와 같은
    규약이고, 근거·범위 제한은 `_line_declared` 참조. G6 에는 이 면제가 없다.
    """
    needles = [e["value"] for e in _exclusion_entries(c)]
    marker = str(c.cfg.get("exclusion_decl_marker", "EXCLUSION-DECL"))
    hits = _scan_scene_values(c, needles, line_marker=marker)
    if hits:
        return fail("G9", f"렌더 leaf 에 배제 문자열 {len(hits)}건", hits[:40])
    return ok("G9", f"{len(needles)}종 × 렌더 leaf 0건")


def _needles_excluded(c: Ctx) -> list[str]:
    return [e["value"] for e in _exclusion_entries(c)]


def g9d(c: Ctx) -> Result:
    """§7-2 — 문서가 배제 문자열을 적을 때 EXCLUSION-DECL 마커를 달았는가."""
    needles = _needles_excluded(c)
    if not c.doc_paths():
        raise GateBlocked("설정 `docs` 가 비었다 — 문서 스캔 대상 없음")
    marker = str(c.cfg.get("exclusion_decl_marker", "EXCLUSION-DECL"))
    hits = _scan_docs(c, needles, marker)
    if hits:
        return fail("G9d", f"마커 없는 배제 문자열 인용 {len(hits)}건", hits[:40])
    return ok("G9d", f"{len(needles)}종 × 문서 마커 규율 준수")


def g10(c: Ctx) -> Result:
    """§1 — 깊이 하한(계측 가능). 주관 문구로 판정하지 않는다."""
    docs = c.cfg.get("docs") or {}
    mins = c.cfg.get("doc_min_lines") or {}
    if not docs:
        raise GateBlocked("설정 `docs` 가 비었다")
    bad = []
    checked = []
    for key, rel in docs.items():
        p = c.p(rel)
        if not os.path.exists(p):
            raise GateBlocked(f"타겟 부재: {rel}")
        n = len(read_lines(p))
        lo = int(mins.get(key, 0))
        checked.append(f"{rel}: {n}줄 (하한 {lo})")
        if n < lo:
            bad.append(f"{rel}: {n}줄 < {lo}줄")
    if bad:
        return fail("G10", "분량 하한 미달", bad + checked)
    return ok("G10", " · ".join(checked))


def g11(c: Ctx) -> Result:
    """㉖ — AI 고지 푸터. 규약 §3 이 요구하고 baseline 27/27 이 지키는데 게이트가 없었다."""
    files = c.require_scenes()
    needle = str(c.cfg.get("ai_footer_text", "AI 보조"))
    missing = [c.rel(f) for f in files if not any(needle in v for v in yaml_values(c.yml(f)))]
    if missing:
        return fail("G11", f"{len(missing)}/{len(files)} Scene 에 {needle!r} 푸터 값 부재", missing)
    return ok("G11", f"{len(files)}/{len(files)}")


# ──────────────────────────────────────────────────────────────────────────────
# 게이트의 **검사 시점(scope)** — 이걸 안 적으면 다음 사람이 저작 게이트 초록을
# 런타임 보장으로 읽는다. 실측: `backend/src/main/resources/scenarios/*.yml`
# 7개 파일 전부 `safety_gates` 0건이다. 런타임은 저작 파일을 읽지 않는다.
#   AUTHOR  : content/{인물}/scene*.yml · docs/MVP-*.md 만 본다. 런타임 무관.
#   RUNTIME : backend 클래스패스(application.yml·테스트)를 본다.
#   BRIDGE  : 저작물의 값이 런타임 쪽에 전파됐는지를 본다(둘을 잇는 검사).
AUTHOR = "저작"
RUNTIME = "런타임"
BRIDGE = "저작→런타임"


def g5e(c: Ctx) -> Result:
    """저작 토큰 **합집합 ⊆ 런타임 토큰**. 설정이 아니라 콘텐츠에서 직접 뽑는다.

    왜 G5b 와 별개인가: G5b 는 설정 `forbidden` 목록을 application.yml 과 대조한다.
    설정에 안 적힌 토큰은 G5b 가 영원히 못 본다 — seed 가 scene 에는 토큰을 넣고
    설정 목록에서 빠뜨리면 G5b 는 초록인데 런타임 방어는 0이다.
    이 게이트는 `content/{인물}/scene*.yml` 의 lint_forbidden_tokens 를 파서로
    긁어 합집합을 만들고, 그게 application.yml 의 런타임 목록에 다 있는지만 본다.

    ⚠️ 주장 범위: 이 게이트의 PASS 는 "저작에서 선언한 토큰의 **정확한 표층형**이
    런타임 목록에도 있다" 까지다. 런타임 매칭이 `trim`+공백축약+`indexOf` 부분문자열
    이므로 어미·높임형 변형(`정신 차려`→`정신 차리세요`)은 이 게이트와 무관하게 통과한다.
    "이 인물의 R2/R3 위반이 없다" 는 뜻이 **아니다.**
    """
    files = c.require_scenes()
    union: set[str] = set()
    for f in files:
        for v in collect_key(c.yml(f), LINT_KEY):
            if isinstance(v, list):
                union.update(str(x).strip() for x in v if str(x).strip())
    if not union:
        raise GateBlocked(
            f"content/{c.name}/scene*.yml 의 {LINT_KEY} 합집합 0종 — "
            f"전파 여부를 판정할 대상이 없다(빈 순회는 BLOCKED)"
        )
    live = _appyml_tokens(c)
    if not live:
        raise GateBlocked("application.yml 의 safety.forbidden-tokens.list 가 비었다")
    nlive = {norm_ws(t) for t in live}  # 런타임 토큰도 정규화 보관 — G5b 주석 참조
    missing = sorted(t for t in union if norm_ws(t) not in nlive)
    if missing:
        return fail(
            "G5e",
            f"저작 토큰 {len(missing)}/{len(union)}종이 런타임 목록에 없다 — "
            f"그 토큰의 런타임 방어는 0이다",
            [f"{t!r} — application.yml safety.forbidden-tokens.list 에 없음" for t in missing],
        )
    return ok("G5e", f"저작 합집합 {len(union)}종 전부 런타임 목록에 존재")


GATES: list[tuple[str, Callable[[Ctx], Result], str, str]] = [
    ("G0cfg", g0cfg, "설정 자체 검증 + legacy 면제 남용 차단", AUTHOR),
    ("G0a", g0a, "Scene 파일 수", AUTHOR),
    ("G0b", g0b, "배제 문자열 양방향 실재/부재", AUTHOR),
    ("G0e", g0e, "배제 문자열 길이 하한 + 라이브 과차단 사전 스캔", AUTHOR),
    ("G0c", g0c, "토큰이 자기 예문을 잡는가", AUTHOR),
    ("G0d", g0d, "제품 말투 위험 문장 커버리지", AUTHOR),
    ("G1b", g1b, "마지막 Scene null 폴백 라우트", AUTHOR),
    ("G2", g2, "R1 상시 음성 리스너", AUTHOR),
    ("G2v", g2v, "R1 위기 래치 키의 값", AUTHOR),
    ("G3", g3, "위기 번호 하드코딩 0건", AUTHOR),
    ("G3b", g3b, "위기 토큰 ≥2 Scene 존재", AUTHOR),
    ("G3c", g3c, "마지막 Scene 위기 토큰", AUTHOR),
    ("G3d", g3d, "위기 리마인더 리터럴 키", AUTHOR),
    ("G4", g4, "YAML 파싱", AUTHOR),
    ("G5a-i", g5a_i, "토큰 합집합 하한", AUTHOR),
    ("G5a-ii", g5a_ii, "R2/R3 축마다 집행 수단", AUTHOR),
    ("G5d", g5d, "토큰 목록 위치 강제", AUTHOR),
    ("G5b", g5b, "application.yml 반영", BRIDGE),
    ("G5e", g5e, "저작 토큰 합집합 ⊆ 런타임 토큰", BRIDGE),
    ("G5c", g5c, "ForbiddenTokenConfigTest 인물 문장", RUNTIME),
    ("G6", g6, "금지 문구 — 렌더 leaf 0건", AUTHOR),
    ("G6d", g6d, "문서 토큰 인용 마커 규율", AUTHOR),
    ("G7", g7, "R4 동의 카드 + 스킵 목적지", AUTHOR),
    ("G8", g8, "R5 기본 경로 값 검사", AUTHOR),
    ("G9", g9, "배제 문자열 — 렌더 leaf 0건", AUTHOR),
    ("G9d", g9d, "문서 배제 문자열 마커 규율", AUTHOR),
    ("G10", g10, "분량 하한", AUTHOR),
    ("G11", g11, "AI 고지 푸터", AUTHOR),
]


def run_gates(c: Ctx, only: list[str] | None = None) -> list[Result]:
    results: list[Result] = []
    legacy = (c.ruleset == "legacy-baseline") and not c.strict
    for gid, fn, _desc, _scope in GATES:
        if only and gid not in only:
            continue
        if legacy and gid in NEWCHAR_ONLY_GATES:
            results.append(
                blocked(
                    gid,
                    "legacy-baseline ruleset — 신규 인물 규약 게이트라 판정 유보. "
                    "부채를 보려면 --strict",
                )
            )
            continue
        try:
            results.append(fn(c))
        except GateBlocked as e:
            results.append(blocked(gid, e.reason))
        except FileNotFoundError as e:
            results.append(blocked(gid, f"타겟 부재: {e.filename}"))
        except Exception as e:  # noqa: BLE001
            results.append(fail(gid, f"게이트 실행 예외 {type(e).__name__}: {e}"))
    return results


# ──────────────────────────────────────────────────────────────────────────────
def default_config_path(root: str, name: str) -> str:
    return os.path.join(root, "scripts", "gates", f"{name}.yml")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="/새인물 게이트 러너")
    ap.add_argument("--character", "-c", help="인물 슬러그 (예: elijah)")
    ap.add_argument("--config", help="설정 파일 경로 (기본 scripts/gates/{character}.yml)")
    ap.add_argument("--root", default=None, help="레포 루트 (기본: 이 스크립트의 상위)")
    ap.add_argument("--only", default=None, help="쉼표 구분 게이트 id 만 실행")
    ap.add_argument("--strict", action="store_true", help="legacy-baseline 면제를 무시")
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args(argv)

    root = a.root or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    cfg_path = a.config
    if not cfg_path:
        if not a.character:
            ap.error("--character 또는 --config 중 하나는 필요하다")
        cfg_path = default_config_path(root, a.character)
    if not os.path.isabs(cfg_path):
        cfg_path = os.path.join(root, cfg_path) if not os.path.exists(cfg_path) else cfg_path
    if not os.path.exists(cfg_path):
        sys.stderr.write(f"설정 파일 없음: {cfg_path}\n")
        return 2
    cfg = load_yaml(cfg_path) or {}
    if a.character:
        cfg.setdefault("character", a.character)

    c = Ctx(root, cfg, strict=a.strict)
    only = [s.strip() for s in a.only.split(",")] if a.only else None
    results = run_gates(c, only)

    n_fail = sum(1 for r in results if r.status == FAIL)
    n_block = sum(1 for r in results if r.status == BLOCKED)
    n_pass = sum(1 for r in results if r.status == PASS)

    if a.json:
        print(
            json.dumps(
                {
                    "character": c.name,
                    "ruleset": c.ruleset,
                    "strict": c.strict,
                    "summary": {"pass": n_pass, "fail": n_fail, "blocked": n_block},
                    "results": [
                        {
                            "gate": r.gate,
                            "scope": {g: sc for g, _f, _d, sc in GATES}.get(r.gate),
                            "status": r.status,
                            "reason": r.reason,
                            "details": r.details,
                        }
                        for r in results
                    ],
                },
                ensure_ascii=False,
                indent=2,
            )
        )
    else:
        print(f"=== /새인물 게이트 :: {c.name} (ruleset={c.ruleset}{', strict' if c.strict else ''}) ===")
        scope_of = {g: sc for g, _f, _d, sc in GATES}
        for r in results:
            mark = {PASS: "PASS   ", FAIL: "FAIL   ", BLOCKED: "BLOCKED"}[r.status]
            print(f"  [{mark}] {r.gate:<8} {scope_of.get(r.gate, '?'):<6} {r.reason}")
            for d in r.details:
                print(f"            - {d}")
        print(f"--- PASS {n_pass} / FAIL {n_fail} / BLOCKED {n_block} ---")
        if n_block:
            print("  ⚠️ BLOCKED 는 PASS 가 아니다 — 판정 불가 상태다. 통과로 보고하지 말 것.")
        print(
            "  scope: 저작=content/·docs/ 만 검사(런타임 무관) · 런타임=backend 클래스패스 · "
            "저작→런타임=전파 검사"
        )
        print(
            "  ⚠️ PASS 의 주장 범위: '선언된 토큰의 정확한 표층형이 대상에 없다' 까지다. "
            "'R2/R3 위반이 없다' 가 아니다."
        )

    return 0 if (n_fail == 0 and n_block == 0) else 1


if __name__ == "__main__":
    sys.exit(main())
