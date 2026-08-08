#!/usr/bin/env python3
"""라합 — **자막 계약(AC-6)** 과 **F-6.5 footer 카드 부착(AC-7)** 을 검사한다.

**왜 있는가.** `SEED-RAHAB.md` §5-1-a 는 스스로 이렇게 적어 두었다 —
「이 계약은 게이트가 검사하지 않는다. `attribution_display_ko` 는 `newchar_gates.py` 에
**0회**다. 24행의 수도, 화자 표기의 유무도, `중` 표기의 정확성도 재는 게이트가 없다.」
§5-4 도 마찬가지다 — footer 커버리지는 **두 판 연속 틀렸고**(rev.5 는 5장 중 3장,
rev.7 은 6장 중 5장) 두 번 다 **사람이 다시 세어서** 잡았다. 그동안 모든 게이트는 초록이었다.
이 파일이 그 자리를 맡는다(§8-1 AC-6 · AC-7 · §9).

**정본을 읽는다. 사본을 두지 않는다.** 24행 표도, footer 7종도, 카드 6장 본문도, 금지
토큰도 전부 파일에서 파싱한다. 이 파일이 아는 것은 **표의 위치와 열 이름**뿐이다.
파싱 기구는 `check_rahab_staging.py` 것을 **불러 쓴다** — 베껴 두면 두 벌이 되고,
저쪽이 바뀌어도 이쪽은 옛 규칙으로 초록을 낸다(`review_log_check.py` 와 같은 이유).

검사 항목:

    AC-6 자막 계약
      a-rows   §5-1-a 표가 24행이다
      a-used   표의 절 좌표 집합이 `verses-rahab.txt` `## USED` 24행과 **1:1**
      a-jung   `중` 표기 — `수 6:17b`·`수 6:27` 만 붙이지 않는다(§5-1-a 가 선언한 규칙)
      a-order  Scene 은 1..5 이고 각 Scene 의 순서는 1 부터 끊김 없이 이어진다
      a-attr   🚨 **화자 지문이 배제 절에 있는 세 자리**(2:18 · 2:19 · 6:17b)에 표기가 있다
      a-word   표기 낱말이 `verses-rahab.txt` 본문에 실재한다(seed 가 근거로 댄 것)
      a-yml    Scene yml 의 자막 배열이 이 표와 일치 — **designer 산출물**

    AC-7 F-6.5 footer
      f-rows   §5-4 표가 **7종**이다(seed 의 명시 회계)
      f-cards  그 7종 중 카드를 지목하는 6종의 카드 id 집합 == §7-2 동의 카드 6장
      f-cover  🚨 **카드 본문 6장 각각의 끝에 배정된 LOCKED 문자열이 자구 그대로 있다**
      f-token  7종 전부 `{{crisis_resources.default}}` 를 품는다(§5-4 의 단언)
      f-once   카드 본문 안에서 그 토큰이 **한 번만** 나온다(§7-2-a — 두 번 뜨면 안 된다)
      f-forbid footer 문안이 `scripts/gates/rahab.yml` 의 금지 토큰을 쓰지 않는다
      f-yml    Scene yml 의 카드에 footer 가 실제로 붙었는가 — **designer 산출물**

**한 검사가 두 층위로 갈린다.** `content/rahab/scene*.yml` 은 designer 산출물이라 아직
없다. 없는 쪽을 조용히 건너뛰면 그 검사는 "통과"로 보이므로 **문서 층위와 yml 층위를 각각
한 줄로 낸다.** yml 이 없으면 그 줄은 PASS 가 아니라 `BLOCKED` 다.

🚨 **이 검사기가 초록이어도 AC-6·AC-7 은 닫히지 않는다.** 자막도 카드도 사용자에게 닿는
것은 yml 이고, 그것이 없는 동안 여기서 재는 것은 **상류 계약의 자기정합**뿐이다.

**rc 규약**: `0` PASS · `1` FAIL · `2` BLOCKED(판정 불가) · `≥126` 실행 실패
(`SEED-RAHAB.md` §8-1).

    python3 scripts/check_rahab_captions.py
    python3 scripts/check_rahab_captions.py --seed docs/SEED-RAHAB.md --content content/rahab
"""

from __future__ import annotations

import argparse
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import check_rahab_staging as st  # noqa: E402 — 파싱 기구는 저 파일 하나뿐이다

ROOT = st.ROOT
PASS, FAIL, BLOCKED = st.PASS, st.FAIL, st.BLOCKED
ok, bad, blocked = st.ok, st.bad, st.blocked

VERSES = "docs/verses-rahab.txt"
GATES = "scripts/gates/rahab.yml"
CRISIS = "{{crisis_resources.default}}"

# 🚨 이 세 절만은 좌표를 **코드에 적는다.** §5-1-a 가 「화자 지문이 배제 절에 있다」고
# 지목한 자리이고, 표기가 빠지면 수 2:19 「그의 피가 그의 머리로 돌아갈 것이요」가
# 라합이 가족에게 하는 말로 읽혀 **R2 로 곧장 미끄러진다.** 이 목록을 seed 표에서
# 유도하면, 표가 그 세 행의 표기를 지우는 순간 검사 대상도 함께 사라진다 —
# 곧 **검사가 자기 대상을 잃는 방식으로 초록이 된다.** 그래서 여기 못 박는다.
SPEAKER_ABSENT = ("수 2:18", "수 2:19", "수 6:17b")


def norm(s: str) -> str:
    """비교용 정규화 — 줄바꿈·연속 공백을 한 칸으로 접는다.

    seed 의 표 칸은 한 줄이고 카드 본문은 여러 줄로 접혀 있어, 접지 않으면
    **같은 문자열이 다른 문자열로 보인다.**
    """
    return re.sub(r"\s+", " ", s.replace("​", "")).strip()


def read(path: str) -> str:
    with open(os.path.join(ROOT, path), encoding="utf-8") as f:
        return f.read()


# ──────────────────────────────────────────────────────────────────────────
# 정본 로드
# ──────────────────────────────────────────────────────────────────────────

def used_verses() -> tuple[list[str], str]:
    """`## USED` 구간의 (참조 목록, 본문 전체)."""
    refs, body = [], []
    inside = False
    for ln in read(VERSES).splitlines():
        if ln.startswith("## "):
            inside = ln.strip() == "## USED"
            continue
        if not ln.strip() or ln.lstrip().startswith("#"):
            continue
        if "\t" not in ln:
            continue
        ref, text = ln.split("\t", 1)
        if inside:
            refs.append(ref.strip())
        body.append(text.strip())
    return refs, " ".join(body)


def forbidden_tokens() -> list[str]:
    """`gates/rahab.yml` 의 `forbidden:` 목록. yaml 없이도 읽히게 줄 단위로 뽑는다."""
    out, inside = [], False
    for ln in read(GATES).splitlines():
        if re.match(r"^\w+:", ln):
            inside = ln.startswith("forbidden:")
            continue
        if inside:
            m = re.match(r'^\s*-\s*"([^"]+)"', ln)
            if m:
                out.append(m.group(1))
    return out


# ──────────────────────────────────────────────────────────────────────────
# AC-6 — 자막 계약
# ──────────────────────────────────────────────────────────────────────────

def check_captions(lines: list[str]) -> list[st.Result]:
    res: list[st.Result] = []
    s, e = st.section_bounds(lines, "5-1-a.")
    tbl = st.pick_table(st.parse_tables(lines, s, e),
                        "Scene", "verse_ref", "attribution_display_ko")
    rows = [r for r in tbl.rows if r.get("Scene")]

    refs, body = used_verses()

    # a-rows
    res.append(ok("a-rows", f"§5-1-a 표 {len(rows)}행 — 24행")
               if len(rows) == 24 else
               bad("a-rows", f"§5-1-a 표가 {len(rows)}행이다 — 24행이어야 한다",
                   [f"표 머리 SEED:{tbl.line}"]))

    # a-used — 좌표 집합의 1:1. 순서가 아니라 집합과 중복을 본다.
    tbl_refs = [st.strip_markup(r["verse_ref"]).replace(" 중", "").strip() for r in rows]
    dup = sorted({x for x in tbl_refs if tbl_refs.count(x) > 1})
    only_tbl = sorted(set(tbl_refs) - set(refs))
    only_used = sorted(set(refs) - set(tbl_refs))
    if dup or only_tbl or only_used:
        res.append(bad("a-used", "§5-1-a 표와 `## USED` 24행이 1:1 이 아니다",
                       [f"표에 중복: {d}" for d in dup]
                       + [f"표에만 있다: {x}" for x in only_tbl]
                       + [f"USED 에만 있다: {x}" for x in only_used]))
    else:
        res.append(ok("a-used", f"표의 절 좌표 {len(tbl_refs)}개가 `## USED` 와 1:1"))

    # a-jung — `중` 규칙
    wrong = []
    for r in rows:
        raw = st.strip_markup(r["verse_ref"])
        base = raw.replace(" 중", "").strip()
        has = raw.endswith("중")
        want = base not in ("수 6:17b", "수 6:27")
        if has != want:
            wrong.append(f"{raw!r} — `중` 이 {'붙어 있다' if has else '없다'}")
    res.append(ok("a-jung", "`중` 표기 24행 전건 규칙대로 (6:17b·6:27 만 예외)")
               if not wrong else
               bad("a-jung", f"`중` 표기 불일치 {len(wrong)}건", wrong))

    # a-order — Scene 1..5, 각 Scene 순서 1..N 연속
    seq: dict[int, list[int]] = {}
    for r in rows:
        try:
            seq.setdefault(int(st.strip_markup(r["Scene"])), []).append(
                int(st.strip_markup(r["순서"])))
        except (ValueError, KeyError):
            pass
    bad_order = []
    if sorted(seq) != [1, 2, 3, 4, 5]:
        bad_order.append(f"Scene 번호가 1..5 가 아니다: {sorted(seq)}")
    for n in sorted(seq):
        if seq[n] != list(range(1, len(seq[n]) + 1)):
            bad_order.append(f"Scene {n} 순서가 끊겼다: {seq[n]}")
    res.append(ok("a-order", "Scene 1..5 · 각 Scene 순서 연속",
                  [" · ".join(f"S{n}:{len(seq[n])}행" for n in sorted(seq))])
               if not bad_order else bad("a-order", "Scene·순서 결함", bad_order))

    # a-attr — 화자 지문이 배제 절에 있는 세 자리
    attr = {st.strip_markup(r["verse_ref"]).replace(" 중", "").strip():
            st.strip_markup(r["attribution_display_ko"]) for r in rows}
    missing = [v for v in SPEAKER_ABSENT
               if not attr.get(v) or attr[v] in ("—", "-", "")]
    res.append(ok("a-attr", "화자 지문이 배제 절에 있는 3자리 전건에 표기가 있다",
                  [f"{v} → {attr.get(v)!r}" for v in SPEAKER_ABSENT])
               if not missing else
               bad("a-attr", f"🚨 표기 없음 {len(missing)}건 — R2 오독 경로가 열린다",
                   [f"{v}: 표기가 비어 있다" for v in missing]))

    # a-word — 표기 낱말의 본문 근거
    #   ⚠️ 표기는 인용이 아니다(§5-1-a). 여기서 재는 것은 **seed 가 근거로 댄 낱말이
    #   실제로 본문에 있는가**뿐이고, 표기가 옳은 화자인지는 재지 못한다.
    all_body = body + " " + read(VERSES)
    ungrounded = sorted({v for v in attr.values()
                         if v and v not in ("—", "-") and v not in all_body})
    res.append(ok("a-word", "표기 낱말 전건이 `verses-rahab.txt` 본문에 실재",
                  [" · ".join(sorted({v for v in attr.values() if v and v not in ("—", "-")}))])
               if not ungrounded else
               bad("a-word", f"본문에 없는 표기 {len(ungrounded)}건", ungrounded))
    return res


# ──────────────────────────────────────────────────────────────────────────
# AC-7 — F-6.5 footer
# ──────────────────────────────────────────────────────────────────────────

CARD_HEAD = re.compile(r"^\*\*\d+\.\s*`([a-z0-9_]+)`\*\*")
# ⚠️ `parse_tables` 는 칸을 이미 `strip_markup` 한다 — 백틱이 남아 있다고 보고 찾으면
#    **한 건도 안 걸리고, 그러면 「footer 0/6」이라는 거짓 FAIL 이 나온다.** 실제로 났다.
CARD_IN_SLOT = re.compile(r"([a-z0-9_]+)\s*카드")


def card_bodies(lines: list[str], s: int, e: int) -> dict[str, str]:
    """§7-2-a 「카드 본문 6장」의 id → 들여쓴 본문 블록."""
    out: dict[str, str] = {}
    cur = None
    buf: list[str] = []
    for ln in lines[s:e]:
        m = CARD_HEAD.match(ln.strip())
        if m:
            if cur:
                out[cur] = "\n".join(buf)
            cur, buf = m.group(1), []
            continue
        if cur is not None:
            if ln.startswith("####") or ln.startswith("###"):
                out[cur] = "\n".join(buf)
                cur, buf = None, []
                continue
            if ln.startswith("    "):
                buf.append(ln[4:])
    if cur:
        out[cur] = "\n".join(buf)
    return out


def check_footers(lines: list[str]) -> list[st.Result]:
    res: list[st.Result] = []

    s4s, s4e = st.section_bounds(lines, "5-4.")
    f_tbl = st.pick_table(st.parse_tables(lines, s4s, s4e), "id", "노출 위치", "LOCKED 문자열")
    f_rows = [r for r in f_tbl.rows if st.strip_markup(r["id"])]

    # f-rows — 7종
    res.append(ok("f-rows", f"§5-4 F-6.5 문자열 {len(f_rows)}종 — 7종")
               if len(f_rows) == 7 else
               bad("f-rows", f"§5-4 가 {len(f_rows)}종이다 — 7종이어야 한다",
                   [f"표 머리 SEED:{f_tbl.line}"]))

    # 노출 위치에서 카드 id 를 뽑는다. 카드가 아닌 행(종결 화면)은 None.
    assign: dict[str, str] = {}   # card_id -> footer 문안
    fid_of: dict[str, str] = {}   # card_id -> footer id
    non_card: list[str] = []
    for r in f_rows:
        fid = st.strip_markup(r["id"])
        text = st.strip_markup(r["LOCKED 문자열"]).strip('"')
        m = re.search(CARD_IN_SLOT, r["노출 위치"])
        if m:
            assign[m.group(1)] = text
            fid_of[m.group(1)] = fid
        else:
            non_card.append(fid)

    # f-cards — 카드 집합 일치
    s72s, s72e = st.section_bounds(lines, "7-2.")
    c_tbl = st.pick_table(st.parse_tables(lines, s72s, s72e),
                          "consent_card_id", "Scene", "skip_alternative_scene_id")
    cards = sorted({st.strip_markup(r["consent_card_id"]) for r in c_tbl.rows
                    if st.strip_markup(r["consent_card_id"])})
    diff = sorted(set(cards) ^ set(assign))
    res.append(ok("f-cards", f"footer 가 지목하는 카드 {len(assign)}장 == §7-2 동의 카드 {len(cards)}장",
                  [f"카드 아님: {' · '.join(non_card)}"])
               if not diff else
               bad("f-cards", f"카드 집합 불일치 {len(diff)}건",
                   [f"{c}: {'footer 없음' if c in cards else '§7-2 에 없는 카드'}" for c in diff]))

    # f-cover — 🚨 부착 커버리지. rev.5·rev.7 이 두 번 틀린 자리다.
    s7as, s7ae = st.section_bounds(lines, "7-2-a.")
    bodies = card_bodies(lines, s7as, s7ae)
    uncovered, wrong_tail = [], []
    for cid in cards:
        if cid not in bodies:
            uncovered.append(f"{cid}: §7-2-a 에 카드 본문이 없다")
            continue
        want = norm(assign.get(cid, ""))
        got = norm(bodies[cid])
        if not want:
            uncovered.append(f"{cid}: 배정된 footer 문안이 없다")
        elif want not in got:
            uncovered.append(f"{cid}: {fid_of.get(cid)} 문안이 카드 본문에 없다")
        elif not got.endswith(want):
            # footer 는 카드 **하단**이다. 가운데 박혀 있으면 화면에서 그 자리가 아니다.
            wrong_tail.append(f"{cid}: footer 가 본문 끝이 아니다")
    if uncovered:
        res.append(bad("f-cover", f"🚨 footer 부착 {len(cards) - len(uncovered)}/{len(cards)} — "
                                  "rev.5·rev.7 이 두 번 틀린 자리다", uncovered + wrong_tail))
    elif wrong_tail:
        res.append(bad("f-cover", f"footer 는 {len(cards)}장 전부에 있으나 위치가 하단이 아니다",
                       wrong_tail))
    else:
        res.append(ok("f-cover", f"footer 부착 {len(cards)}/{len(cards)} · 전건 카드 본문 **끝**에 자구 그대로",
                      [f"{c} ← {fid_of[c]}" for c in cards]))

    # f-token — 7종 전부 위기 자원 토큰을 품는다
    no_tok = [st.strip_markup(r["id"]) for r in f_rows if CRISIS not in r["LOCKED 문자열"]]
    res.append(ok("f-token", f"{len(f_rows)}종 전부 `{CRISIS}` 를 품는다")
               if not no_tok else
               bad("f-token", f"토큰 없는 footer {len(no_tok)}종 — R5 접촉면이 빈다", no_tok))

    # f-once — 카드 본문 안 토큰 1회
    twice = [f"{c}: {norm(bodies[c]).count(CRISIS)}회" for c in cards
             if c in bodies and norm(bodies[c]).count(CRISIS) != 1]
    res.append(ok("f-once", "카드 본문마다 위기 자원 토큰이 정확히 1회")
               if not twice else
               bad("f-once", f"토큰 출현 수 이상 {len(twice)}건 — 같은 화면에 두 번 뜬다", twice))

    # f-forbid — footer 문안이 금지 토큰을 쓰지 않는다
    toks = forbidden_tokens()
    hits = [f"{st.strip_markup(r['id'])}: {t!r}" for r in f_rows for t in toks
            if t in r["LOCKED 문자열"]]
    res.append(ok("f-forbid", f"footer 7종이 금지 토큰 {len(toks)}종을 쓰지 않는다")
               if not hits else
               bad("f-forbid", f"금지 토큰 사용 {len(hits)}건", hits))
    return res


# ──────────────────────────────────────────────────────────────────────────
# yml 층위 — designer 산출물
# ──────────────────────────────────────────────────────────────────────────

def check_yml(lines: list[str], content: str) -> list[st.Result]:
    files = st.scene_files(content)
    rel = os.path.relpath(content, ROOT)
    if not files:
        return [
            blocked("a-yml", f"자막 배열 대조 불가 — `{rel}/scene*.yml` 이 없다 (designer 산출물)"),
            blocked("f-yml", f"footer 실부착 대조 불가 — `{rel}/scene*.yml` 이 없다 (designer 산출물)"),
        ]
    if st.load_yaml(files[0]) is None:
        return [blocked("a-yml", "PyYAML 이 없어 yml 을 읽지 못한다"),
                blocked("f-yml", "PyYAML 이 없어 yml 을 읽지 못한다")]

    s, e = st.section_bounds(lines, "5-1-a.")
    tbl = st.pick_table(st.parse_tables(lines, s, e),
                        "Scene", "verse_ref", "attribution_display_ko")
    want: dict[int, list[tuple[str, str]]] = {}
    for r in tbl.rows:
        if not r.get("Scene"):
            continue
        want.setdefault(int(st.strip_markup(r["Scene"])), []).append(
            (st.strip_markup(r["verse_ref"]),
             st.strip_markup(r["attribution_display_ko"])))

    s4s, s4e = st.section_bounds(lines, "5-4.")
    f_tbl = st.pick_table(st.parse_tables(lines, s4s, s4e), "id", "노출 위치", "LOCKED 문자열")
    assign = {}
    for r in f_tbl.rows:
        m = re.search(CARD_IN_SLOT, r.get("노출 위치", ""))
        if m:
            assign[m.group(1)] = norm(st.strip_markup(r["LOCKED 문자열"]).strip('"'))

    res: list[st.Result] = []
    cap_bad, foot_bad = [], []
    for path in files:
        n = int(re.search(r"scene(\d+)", os.path.basename(path)).group(1))
        doc = st.load_yaml(path)
        caps = [d for d in st.walk_dicts(doc) if "verse_ref" in d and "text_ko" in d]
        got = [(str(d.get("verse_ref", "")), str(d.get("attribution_display_ko", "") or ""))
               for d in caps]
        exp = want.get(n, [])
        if [g[0] for g in got] != [x[0] for x in exp]:
            cap_bad.append(f"scene{n}.yml: verse_ref 배열이 §5-1-a 와 다르다 "
                           f"({len(got)}행 vs {len(exp)}행)")
        for (gr, ga), (er, ea) in zip(got, exp):
            want_attr = "" if ea in ("—", "-") else ea
            if gr == er and ga != want_attr:
                cap_bad.append(f"scene{n}.yml {gr}: attribution {ga!r} ≠ {want_attr!r}")
        for d in st.walk_dicts(doc):
            cid = d.get("consent_card_id")
            if not cid or cid not in assign:
                continue
            ko = norm(str(d.get("consent_card_ko", "")))
            if not ko.endswith(assign[cid]):
                foot_bad.append(f"scene{n}.yml {cid}: F-6.5 footer 가 카드 끝에 없다")
        for d in st.walk_dicts(doc):
            if "speaker" in d and "verse_ref" in d and d.get("speaker") != "scripture_caption":
                cap_bad.append(f"scene{n}.yml {d.get('verse_ref')}: "
                               f"speaker={d['speaker']!r} ≠ scripture_caption")

    res.append(bad("a-yml", f"(자막) yml 불일치 {len(cap_bad)}건", cap_bad) if cap_bad
               else ok("a-yml", f"{len(files)}개 Scene yml 의 자막 배열이 §5-1-a 와 일치"))
    res.append(bad("f-yml", f"(footer) yml 불일치 {len(foot_bad)}건", foot_bad) if foot_bad
               else ok("f-yml", f"{len(files)}개 Scene yml 의 동의 카드에 F-6.5 footer 가 붙었다"))
    return res


# ──────────────────────────────────────────────────────────────────────────

def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="라합 자막 계약(AC-6) · F-6.5 footer 부착(AC-7) 검사")
    # 위치 인자를 받는 이유는 `check_rahab_staging.py` 와 같다 — `code_claims_check.py`
    # 가 §실측 표의 도구를 `python3 scripts/<tool> <입력>` 으로 **다시 돌린다.**
    ap.add_argument("seed_pos", nargs="?", default=None, help="검사할 seed (위치 인자)")
    ap.add_argument("--seed", default="docs/SEED-RAHAB.md")
    ap.add_argument("--content", default="content/rahab")
    a = ap.parse_args(argv)

    a.seed = a.seed_pos or a.seed
    seed = a.seed if os.path.isabs(a.seed) else os.path.join(ROOT, a.seed)
    content = a.content if os.path.isabs(a.content) else os.path.join(ROOT, a.content)
    if not os.path.exists(seed):
        sys.stderr.write(f"seed 없음: {seed}\n")
        return 2

    with open(seed, encoding="utf-8") as f:
        lines = f.read().splitlines()

    try:
        results = check_captions(lines) + check_footers(lines) + check_yml(lines, content)
    except LookupError as ex:
        # 절·표를 못 찾은 것은 "문제 없음"이 아니라 판정 불가다.
        sys.stderr.write(f"판정 불가 — seed 구조가 예상과 다르다: {ex}\n")
        return 2

    n_pass = sum(1 for r in results if r.status == PASS)
    n_fail = sum(1 for r in results if r.status == FAIL)
    n_block = sum(1 for r in results if r.status == BLOCKED)

    print(f"=== check_rahab_captions :: {os.path.relpath(seed, ROOT)} ===")
    for r in results:
        print(f"  [{r.status:<7}] {r.key:<8} {r.reason}")
        for d in r.details:
            print(f"             - {d}")
    print(f"--- PASS {n_pass} / FAIL {n_fail} / BLOCKED {n_block} ---")
    if n_block:
        print("  ⚠️ BLOCKED 는 PASS 가 아니다 — 잴 수단이 없는 상태다. 통과로 보고하지 말 것.")
    print("  주장 범위: 이 초록은 **상류 계약의 자기정합**이다. 자막도 카드도 사용자에게")
    print("  닿는 것은 `content/rahab/scene*.yml` 이고, 그것이 없는 동안 AC-6·AC-7 은")
    print("  닫히지 않는다. 그리고 `a-word` 는 표기 낱말이 본문에 **있는지**만 재지")
    print("  그 표기가 **옳은 화자인지**는 재지 못한다 — 그 판정은 사람 몫이다.")
    print("  rc: 0=PASS · 1=FAIL · 2=BLOCKED (SEED-RAHAB.md §8-1 규약)")

    return 1 if n_fail else (2 if n_block else 0)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as ex:  # noqa: BLE001
        sys.stderr.write(f"실행 실패: {type(ex).__name__}: {ex}\n")
        sys.exit(126)
