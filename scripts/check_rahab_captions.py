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

    비성경 내레이션 (`RAHAB-LOCKED-STRINGS.md` §3 — rev.12 신설)
      n-rows   §3 이 4행이다 — 내레이션 3줄 + 표지 1
      n-marker 표지 자구 "본문이 아닙니다" 가 잠겨 있다
      n-forbid 내레이션 문안이 금지 토큰을 쓰지 않는다
      n-verse  내레이션이 성경 자구를 옮기지 않는다(연속 6자 n-gram)
      n-disclose 내레이션을 부르는 route 의 카드가 그 사실을 고지한다
      n-yml    Scene yml 에 내레이션이 실렸는가 — **designer 산출물**

    미션 제목 (`RAHAB-LOCKED-STRINGS.md` §4 — 이 판 신설)
      t-rows   §4 가 `rahab_mission_title` 1행이다
      t-title  제목 자구가 "먼저 있었던 일" 이다
      t-forbid 제목이 금지 토큰을 쓰지 않는다
      t-verse  제목이 성경 자구를 옮기지 않는다 — 🚨 제목에는 표지를 붙일 자리가 없다

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
STRINGS = "docs/RAHAB-LOCKED-STRINGS.md"
CRISIS = "{{crisis_resources.default}}"

# 🚨 화면에서 정본과 비정본을 가르는 **유일한** 수단. 이 낱말이 내레이션에 붙지 않으면
# 저자가 쓴 문장이 성경 자막과 같은 자격으로 화면에 선다. `RAHAB-LOCKED-STRINGS.md`
# §3-1 이 rev.12 에서야 잠근 문자열이고, 그 전까지는 산문 한 줄이 전부였다.
MARKER = "본문이 아닙니다"

# 🚨 미션 제목. `RAHAB-LOCKED-STRINGS.md` §4 가 이 판에서 잠근 것이고, seed 에는
# 확정값이 **없었다** — `docs/MVP-RAHAB.md` §1 이 designer 제안으로 올린 가제를
# 저자가 2026-08-09 에 승인했다. 제목은 **도달률 100%** 인 유일한 비성경 문자열이다:
# 자막보다 앞에 있고, 동의 카드도 스킵도 그것을 줄이지 못한다. 여기에 자구를 적어
# 두는 이유는 표만 잠그면 다음 판이 표를 통째로 바꿔도 아무 줄도 붉어지지 않기 때문이다.
TITLE_ID = "rahab_mission_title"
TITLE = "먼저 있었던 일"

# n-verse 문턱. 공백·문장부호를 걷어낸 뒤의 **연속 6자**다. 근거는 실측이다 —
# 확정한 네 문자열의 본문 최대 겹침이 4자("라합이그", 우연)이고, rev.12 초안이 실제로
# 저지른 복사 「지붕에 올라가」는 공백을 걷으면 정확히 6자다. 곧 이 문턱은 **지금
# 통과하면서 그 결함은 잡는** 자리에 있다. 낮추면 조사·어미가 걸려 거짓 FAIL 이 나고,
# 높이면 저 결함이 빠져나간다.
NAR_NGRAM = 6

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


def footer_table(slines: list[str]):
    """F-6.5 문안 7종 — **정본은 `RAHAB-LOCKED-STRINGS.md` §1 이다.**

    rev.11 까지는 seed §5-4 안에 근거 산문과 섞여 있었다. 옮기면서 seed 에는 사본을
    남기지 않았으므로(포인터만 있다) 여기서 seed 를 읽으면 표를 **못 찾는다** —
    `section_bounds` 가 `LookupError` 를 내고 main 이 그것을 rc 2(판정 불가)로 낸다.
    조용히 0 종으로 세어 초록이 되는 길은 없다.
    """
    s, e = st.section_bounds(slines, "1. F-6.5")
    return st.pick_table(st.parse_tables(slines, s, e), "id", "노출 위치", "LOCKED 문자열")


def check_footers(lines: list[str], slines: list[str]) -> list[st.Result]:
    res: list[st.Result] = []

    f_tbl = footer_table(slines)
    f_rows = [r for r in f_tbl.rows if st.strip_markup(r["id"])]

    # f-rows — 7종
    res.append(ok("f-rows", f"§1 F-6.5 문자열 {len(f_rows)}종 — 7종")
               if len(f_rows) == 7 else
               bad("f-rows", f"§1 이 {len(f_rows)}종이다 — 7종이어야 한다",
                   [f"표 머리 STRINGS:{f_tbl.line}"]))

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
    bodies = card_bodies(slines, *st.section_bounds(slines, "2. 동의 카드"))
    uncovered, wrong_tail = [], []
    for cid in cards:
        if cid not in bodies:
            uncovered.append(f"{cid}: §2 에 카드 본문이 없다")
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
# 비성경 내레이션 — `RAHAB-LOCKED-STRINGS.md` §3
#
# 🚨 **이 네 문자열은 rev.11 까지 아무도 재지 않았다.** seed §7-2 는 「비성경 내레이션
#    1줄」이 필요하다고 판정만 하고 문안을 비워 두었고, 비어 있는 칸은 어떤 검사도
#    걸리지 않는다 — 곧 **없는 것이 통과처럼 보이는** 자리였다. 잠그는 것과 재는 것을
#    같은 판에 넣는다. 잠그기만 하면 다음 판이 조용히 바꿔도 아무 줄도 붉어지지 않는다.
# ──────────────────────────────────────────────────────────────────────────

ROUTE = re.compile(r'"([a-z0-9_]+)"')


def narration_rows(slines: list[str]) -> list[dict]:
    s, e = st.section_bounds(slines, "3. 비성경 내레이션")
    tbl = st.pick_table(st.parse_tables(slines, s, e), "id", "언제 뜨는가", "LOCKED 문자열")
    return [r for r in tbl.rows if st.strip_markup(r["id"])]


def check_narration(lines: list[str], slines: list[str]) -> list[st.Result]:
    res: list[st.Result] = []
    rows = narration_rows(slines)
    ids = [st.strip_markup(r["id"]) for r in rows]
    text_of = {st.strip_markup(r["id"]): st.strip_markup(r["LOCKED 문자열"]).strip('"')
               for r in rows}

    # n-rows — 내레이션 3줄 + 표지 1 = 4행. 표지를 따로 세지 않으면 §3-1 이 적어 둔
    #          「16종이 아니라 17종」이 다시 16종으로 돌아가도 아무 줄도 붉어지지 않는다.
    want = ["rahab_nar_s1_address", "rahab_nar_s3_bridge",
            "rahab_nar_s5_close", "rahab_nar_marker"]
    missing = [i for i in want if i not in ids]
    extra = [i for i in ids if i not in want]
    res.append(ok("n-rows", f"§3 내레이션 {len(rows)}행 — 3줄 + 표지 1")
               if not missing and not extra and len(rows) == 4 else
               bad("n-rows", f"§3 이 {len(rows)}행이다 — 4행(3줄+표지)이어야 한다",
                   [f"없다: {m}" for m in missing] + [f"모르는 id: {x}" for x in extra]))

    # n-marker — 표지 문자열이 자구 그대로 잠겨 있는가
    got_marker = text_of.get("rahab_nar_marker", "")
    res.append(ok("n-marker", f"표지 문자열 {MARKER!r} 이 표에 잠겨 있다")
               if norm(got_marker) == MARKER else
               bad("n-marker", "표지 문자열이 검사기가 아는 자구와 다르다",
                   [f"표: {got_marker!r} ≠ 코드: {MARKER!r}",
                    "🚨 화면에서 정본과 비정본을 가르는 유일한 수단이다"]))

    # n-forbid — R3 어휘
    toks = forbidden_tokens()
    hits = [f"{i}: {t!r}" for i in ids for t in toks if t in text_of[i]]
    res.append(ok("n-forbid", f"내레이션 {len(ids)}종이 금지 토큰 {len(toks)}종을 쓰지 않는다")
               if not hits else
               bad("n-forbid", f"금지 토큰 사용 {len(hits)}건", hits))

    # n-verse — 🚨 성경 자구 복사. rev.12 초안이 실제로 저지른 결함이다
    #           (「지붕에 올라가」 — 하필 `s2_skip` 이 **빼는** 수 2:6 의 자구였다).
    corpus = re.sub(r"[^0-9A-Za-z가-힣]", "", used_verses()[1])
    grams = {corpus[i:i + NAR_NGRAM] for i in range(len(corpus) - NAR_NGRAM + 1)}
    copied = []
    for i in ids:
        t = re.sub(r"[^0-9A-Za-z가-힣]", "", text_of[i])
        hit = sorted({t[j:j + NAR_NGRAM] for j in range(len(t) - NAR_NGRAM + 1)} & grams)
        if hit:
            copied.append(f"{i}: {' · '.join(hit)}")
    res.append(ok("n-verse", f"내레이션 {len(ids)}종에 본문 연속 {NAR_NGRAM}자 겹침 없음",
                  ["⚠️ 자구 복사의 **하한**이다 — 자구를 안 겹치고 사건만 옮기는 문장은 통과한다"])
               if not copied else
               bad("n-verse", f"본문 자구 복사 의심 {len(copied)}건", copied))

    # n-disclose — 🚨 내레이션을 부르는 경로의 카드가 그 사실을 말하는가.
    #   §7-2-a 자신의 규칙이 「건너뛰기 문구는 최악을 적는다」인데, 성경만 보겠다고
    #   건너뛴 사용자가 **저자가 쓴 문장을 더 받는다**는 것은 rev.11 까지 어느 카드도
    #   말하지 않았다. 카드 이름으로 세면 `rahab_lineage_birth` 가 빠진다 —
    #   route 가 `rahab_stigma` 와 같아서 이쪽을 거절해도 같은 줄이 뜬다. 그래서
    #   **이름이 아니라 route 로** 역산한다.
    routes = {m for r in rows for m in ROUTE.findall(r["언제 뜨는가"])}
    s72s, s72e = st.section_bounds(lines, "7-2.")
    c_tbl = st.pick_table(st.parse_tables(lines, s72s, s72e),
                          "consent_card_id", "Scene", "skip_alternative_scene_id")
    need = sorted({st.strip_markup(r["consent_card_id"]) for r in c_tbl.rows
                   if st.strip_markup(r["consent_card_id"])
                   and st.strip_markup(r["skip_alternative_scene_id"]).strip('"') in routes})
    bodies = card_bodies(slines, *st.section_bounds(slines, "2. 동의 카드"))
    # norm 을 거쳐 본다 — 카드 본문은 여러 줄로 접혀 있어, 접지 않으면 표지가
    # 줄바꿈에 걸린 장을 「고지 없음」으로 오판한다. 실제로 그렇게 났다.
    silent = [f"{c}: 거절하면 내레이션이 뜨는데 카드가 그 말을 하지 않는다"
              for c in need if MARKER not in norm(bodies.get(c, ""))]
    res.append(ok("n-disclose", f"내레이션을 부르는 카드 {len(need)}장 전건이 그 사실을 고지",
                  [" · ".join(need)])
               if not silent else
               bad("n-disclose", f"미고지 {len(silent)}건 — 「최악을 적는다」 위반", silent))
    return res


# ──────────────────────────────────────────────────────────────────────────
# 미션 제목 — `RAHAB-LOCKED-STRINGS.md` §4
#
# 🚨 이 문자열은 **아무 통제도 줄이지 못하는 자리**에 있다. 동의 카드는 자막을 빼고
#    스킵은 Scene 을 빼지만, 제목은 그 어느 것보다 먼저 읽힌다 — 곧 도달률 100% 다.
#    §4 를 잠그는 같은 판에서 네 축을 넣는다. rev.12 가 배운 것이 그것이다:
#    **잠그기만 하고 재지 않으면 정본이 하나 는 것이 아니라 사각지대가 하나 는다.**
# ──────────────────────────────────────────────────────────────────────────


def title_rows(slines: list[str]) -> list[dict]:
    s, e = st.section_bounds(slines, "4. 미션 제목")
    tbl = st.pick_table(st.parse_tables(slines, s, e),
                        "id", "어디에 나가는가", "LOCKED 문자열")
    return [r for r in tbl.rows if st.strip_markup(r["id"])]


def check_title(slines: list[str]) -> list[st.Result]:
    res: list[st.Result] = []
    rows = title_rows(slines)
    text_of = {st.strip_markup(r["id"]): st.strip_markup(r["LOCKED 문자열"]).strip('"')
               for r in rows}

    # t-rows — §4 는 1행이다. 제목이 둘이면 어느 것이 화면에 서는지 정본이 답하지 못한다.
    ids = list(text_of)
    res.append(ok("t-rows", f"§4 미션 제목 {len(rows)}행 — 1행")
               if len(rows) == 1 and ids == [TITLE_ID] else
               bad("t-rows", f"§4 가 {len(rows)}행이다 — {TITLE_ID} 1행이어야 한다",
                   [f"본 id: {ids or '없음'}"]))

    # t-title — 자구. 표만 있고 자구를 코드가 모르면 다음 판이 조용히 바꿀 수 있다.
    got = text_of.get(TITLE_ID, "")
    res.append(ok("t-title", f"제목 자구 {TITLE!r} 이 표에 잠겨 있다")
               if norm(got) == TITLE else
               bad("t-title", "제목 자구가 검사기가 아는 것과 다르다",
                   [f"표: {got!r} ≠ 코드: {TITLE!r}",
                    "🚨 도달률 100% 인 유일한 비성경 문자열이다"]))

    # t-forbid — R3 어휘
    toks = forbidden_tokens()
    hits = [f"{i}: {t!r}" for i in ids for t in toks if t in text_of[i]]
    res.append(ok("t-forbid", f"제목 {len(ids)}종이 금지 토큰 {len(toks)}종을 쓰지 않는다")
               if not hits else
               bad("t-forbid", f"금지 토큰 사용 {len(hits)}건", hits))

    # t-verse — 🚨 내레이션(`n-verse`)보다 **더 강하게** 요구되는 축이다. 내레이션은
    #   `rahab_nar_marker` 표지를 달고 뜨지만 제목에는 표지를 붙일 자리가 없다
    #   (§4-2). 곧 제목이 자구를 옮기면 화면에 **표지 없는 성경 문장**이 서게 된다.
    corpus = re.sub(r"[^0-9A-Za-z가-힣]", "", used_verses()[1])
    grams = {corpus[i:i + NAR_NGRAM] for i in range(len(corpus) - NAR_NGRAM + 1)}
    copied = []
    for i in ids:
        t = re.sub(r"[^0-9A-Za-z가-힣]", "", text_of[i])
        hit = sorted({t[j:j + NAR_NGRAM] for j in range(len(t) - NAR_NGRAM + 1)} & grams)
        if hit:
            copied.append(f"{i}: {' · '.join(hit)}")
    res.append(ok("t-verse", f"제목에 본문 연속 {NAR_NGRAM}자 겹침 없음",
                  ["⚠️ 제목은 6자다 — n-gram 문턱과 길이가 같아 **전부 일치할 때만** 걸린다",
                   "   부분 복사를 잡는 검사가 아니다"])
               if not copied else
               bad("t-verse", f"본문 자구 복사 의심 {len(copied)}건", copied))
    return res


# ──────────────────────────────────────────────────────────────────────────
# yml 층위 — designer 산출물
# ──────────────────────────────────────────────────────────────────────────

def check_yml(lines: list[str], slines: list[str], content: str) -> list[st.Result]:
    files = st.scene_files(content)
    rel = os.path.relpath(content, ROOT)
    if not files:
        return [
            blocked("a-yml", f"자막 배열 대조 불가 — `{rel}/scene*.yml` 이 없다 (designer 산출물)"),
            blocked("f-yml", f"footer 실부착 대조 불가 — `{rel}/scene*.yml` 이 없다 (designer 산출물)"),
            # 🚨 표기(서체·색)는 yml 이 생겨도 못 잰다. 여기서 재는 것은 **문자열이
            #    실렸는가**뿐이고, 그것이 자막과 구별되게 그려지는지는 여전히 미완화 노출이다.
            blocked("n-yml", f"내레이션 실적재 대조 불가 — `{rel}/scene*.yml` 이 없다 (designer 산출물)"),
        ]
    if st.load_yaml(files[0]) is None:
        return [blocked("a-yml", "PyYAML 이 없어 yml 을 읽지 못한다"),
                blocked("f-yml", "PyYAML 이 없어 yml 을 읽지 못한다"),
                blocked("n-yml", "PyYAML 이 없어 yml 을 읽지 못한다")]

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

    f_tbl = footer_table(slines)
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

    # n-yml — 내레이션 4종이 실제로 실렸는가. ⚠️ **문자열의 실재만 잰다.**
    #   서체·색은 여기서 못 재고, 그 몫은 미완화 노출로 남는다(§4).
    blob = norm(" ".join(read(os.path.relpath(p, ROOT)) for p in files))
    nar_bad = [f"{i}: yml 어디에도 없다"
               for i, t in ((st.strip_markup(r["id"]),
                             st.strip_markup(r["LOCKED 문자열"]).strip('"'))
                            for r in narration_rows(slines))
               if norm(t) not in blob]
    res.append(bad("n-yml", f"(내레이션) yml 미적재 {len(nar_bad)}건", nar_bad) if nar_bad
               else ok("n-yml", f"§3 내레이션 4종이 {len(files)}개 Scene yml 에 실려 있다",
                       ["⚠️ 문자열 실재만 잰다 — 서체·색 구별은 여전히 미측정"]))
    return res


# ──────────────────────────────────────────────────────────────────────────

def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="라합 자막 계약(AC-6) · F-6.5 footer 부착(AC-7) 검사")
    # 위치 인자를 받는 이유는 `check_rahab_staging.py` 와 같다 — `code_claims_check.py`
    # 가 §실측 표의 도구를 `python3 scripts/<tool> <입력>` 으로 **다시 돌린다.**
    ap.add_argument("seed_pos", nargs="?", default=None, help="검사할 seed (위치 인자)")
    ap.add_argument("--seed", default="docs/SEED-RAHAB.md")
    ap.add_argument("--strings", default=STRINGS)
    ap.add_argument("--content", default="content/rahab")
    a = ap.parse_args(argv)

    a.seed = a.seed_pos or a.seed
    seed = a.seed if os.path.isabs(a.seed) else os.path.join(ROOT, a.seed)
    strings = a.strings if os.path.isabs(a.strings) else os.path.join(ROOT, a.strings)
    content = a.content if os.path.isabs(a.content) else os.path.join(ROOT, a.content)
    if not os.path.exists(seed):
        sys.stderr.write(f"seed 없음: {seed}\n")
        return 2
    if not os.path.exists(strings):
        # 문안 정본이 없으면 footer·카드·내레이션은 **한 줄도** 잴 수 없다.
        # 이것을 "검사할 게 없어서 통과"로 접으면 이 검사기가 존재할 이유가 없어진다.
        sys.stderr.write(f"문안 정본 없음: {strings}\n")
        return 2

    with open(seed, encoding="utf-8") as f:
        lines = f.read().splitlines()
    with open(strings, encoding="utf-8") as f:
        slines = f.read().splitlines()

    try:
        results = (check_captions(lines) + check_footers(lines, slines)
                   + check_narration(lines, slines) + check_title(slines)
                   + check_yml(lines, slines, content))
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
