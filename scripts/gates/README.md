# /새인물 게이트 — 실행 파일

게이트는 더 이상 **베껴 쓸 산문**이 아니다. `scripts/newchar_gates.py` 한 파일이
G0~G11 전부를 구현하고, seed 는 `scripts/gates/{인물}.yml` **설정만** 쓴다.

```bash
python3 scripts/newchar_gates.py --character elijah
python3 scripts/newchar_gates.py --config scripts/gates/peter.yml
python3 scripts/newchar_gates.py --character david --strict   # legacy 면제 무시
python3 scripts/newchar_gates.py --character elijah --json

scripts/gates/tests/run.sh                                    # 게이트 자신의 테스트
```

## 왜 이렇게 만들었나

규약이 게이트를 산문으로 배포하면 라운드마다 5개 seed 가 게이트를 각자 다시 타이핑하고,
매번 누군가 잘못 옮긴다. 실측된 전사(轉寫) 사고: `d.get('lint_forbidden_tokens')` 최상위
조회 2건, `TRIGGER_SCENES='1 5'` 스칼라 2건, 폐기된 `-eq 5` 기준 인용 1건.
경고 문구를 키우는 걸로는 안 끝난다. 전사 자체를 없앤다.

> **문서에 옮겨 적은 스크립트는 정본이 아니다.** 줄여 적는 순간 줄임표가 데이터가 된다 —
> `TOKEN_EXAMPLE_PAIRS=( "연단하시|…예문…" … )` 에서 bash 는 `…` 를 **두 번째 원소**로
> 읽었고, `|` 가 없어 tok 과 ex 가 둘 다 `…` 가 되면서 자기 자신에 매칭돼 통과했다(㉞).
> 33종을 검사한다던 게이트가 실제로 검사한 건 1종이었다. 러너가 랜딩하면 seed §10 은
> **config + 명령 한 줄**로 줄고 이 부류의 사고는 구조적으로 사라진다.

## 판정 3종 — **BLOCKED 는 PASS 가 아니다**

| 판정      | 뜻                                                               |
| --------- | ---------------------------------------------------------------- |
| `PASS`    | 그 게이트가 **실제로 판정했고** 위반이 없다                      |
| `FAIL`    | 위반을 잡았다                                                    |
| `BLOCKED` | **판정 불가.** 타겟 부재, 입력 배열이 비어 순회 0회, 설정 미정의 |

`FAIL` 또는 `BLOCKED` 가 하나라도 있으면 종료코드는 비영이다.
"공집합에 대한 전칭명제는 항상 참" 을 초록으로 인쇄하는 것이 이 파이프라인에서
세 번 난 사고다(⑳ G5a-ii 축 0개, ㉒ G0d 미정의, ⑧ 타겟 부재). 개별 땜질 대신
`Ctx.iterate()` 라는 **단일 통로**로 강제한다. 리스트를 직접 도는 게이트를 추가하지 마라.

## 게이트의 검사 시점(scope) — 저작 / 런타임 / 저작→런타임

출력 2열이 scope 다. **이걸 안 보면 저작 게이트 초록을 런타임 보장으로 읽게 된다.**

실측: `backend/src/main/resources/scenarios/*.yml` 7개 파일 전부 `safety_gates` **0건**이다.
런타임은 `content/{인물}/scene*.yml` 을 읽지 않는다. 두 파일은 스키마부터 다르다.

| scope       | 무엇을 보나                                                                                                             |
| ----------- | ----------------------------------------------------------------------------------------------------------------------- |
| 저작        | `content/{인물}/scene*.yml` · `docs/MVP-*.md`. **런타임 무관.** (G0*·G1b·G2·G3*·G4·G5a·G5d·G6·G6d·G7·G8·G9·G9d·G10·G11) |
| 저작→런타임 | 저작물의 값이 런타임 쪽에 전파됐는지 (G5b·G5e)                                                                          |
| 런타임      | backend 클래스패스 (G5c)                                                                                                |

## PASS 가 주장하는 범위 — 부풀리지 마라

토큰 매칭은 `trim` + 공백축약 후 `indexOf` 부분문자열이다. 실측상 토큰 15종 중 6종은
어휘를 한 글자도 안 바꾸고 **어미·높임형만 바꾸면 통과**한다(`정신 차려`→`정신 차리세요`).
유의어로 바꾸면 8/8 전부 통과한다.

> G6/G9 의 PASS 는 **"선언된 토큰의 정확한 표층형이 렌더 leaf 에 없다"** 까지다.
> **"이 인물의 R2/R3 위반이 없다" 가 아니다.** 러너도 매 실행 끝에 이 문장을 인쇄한다.

높임형 커버리지 공백을 재는 건 G0d 다 — 그래서 `polite_evasive` 가 비면 PASS 가 아니라
BLOCKED 다. "제품 말투로 쓴 위험 문장을 하나도 안 넣었다"는 건 판정 불가지 통과가 아니다.

## 내려야 했던 판단 4건 (근거와 함께 기록)

### 1. G3d(위기 리마인더)는 **신규 인물에만** 적용한다

`crisis_reminder` 키는 라이브 레포 `content/` 에 **0개 파일**에 있다(`docs/MVP-JESUS.md`
본문에만 등장). 소급 적용하면 5인 전원이 즉시 false red 다. 게이트가 상시 빨간 상태는
"게이트는 원래 그래" 로 학습되고, 그 다음의 **진짜 red 를 무시하게 만든다.**

→ G3d 는 설정 `crisis_reminder_scenes` 를 순회한다. 라이브 5인 설정에는 이 키가 없으므로
**BLOCKED(판정 불가)** 로 나온다. PASS 가 아니다. 신규 인물은 이 키를 반드시 쓴다.
기존 5인 소급 마이그레이션은 **이 작업 범위 밖**이고, 하려면 별도 PR 로 콘텐츠를 고쳐야 한다.

### 2. `LEGACY_BASELINE` 면제는 **소스에 하드코딩** — 설정으로 못 켠다

joseph·moses·david 는 토큰 규약(§5)·5-Scene 규약(§8-2)·상시 R1 리스너·F-6.3 opt-out
선언 규약이 **도입되기 전에 머지**됐다. 실측: 세 인물 `lint_forbidden_tokens` 0종,
R5 선언 키(`llm_optin_only`/`default_path`) 0건, david·moses 는 scene6 보유.
전부 FAIL 로 찍으면 상시 red 34건이 남는다(위와 같은 desensitization).

→ `NEWCHAR_ONLY_GATES` 에 속한 게이트는 세 인물에 대해 **BLOCKED**. PASS 로 세지 않는다.
부채 수치를 보려면 `--strict`.
→ **면제 스위치를 설정 파일에 두지 않은 이유**: 게이트를 끄는 스위치가 설정에 있으면
초록으로 가는 가장 싼 길이 그 스위치가 된다. 소스의 집합에 이름이 없는 인물이
`ruleset: legacy-baseline` 을 선언하면 G0cfg 가 FAIL 한다(RED 픽스처 있음).

### 3. G8(R5 기본 경로)도 신규 인물 전용으로 내렸다

legacy 3인은 `llm_optin_only`/`default_path`/`llm_prompt_key` 키가 **0건**이다 — F-6.3
opt-out 기본 ON 선언 규약 자체가 그들보다 나중이다. 그리고 PR #20 이 R5 를 **런타임에**
배선했다(`ResponseResolver` + `AuthAiOptOutAdapter` + `ForbiddenTokenSanitizer`) — 저작
선언이 없다고 런타임이 무방비인 상태가 아니다. elijah·solomon 은 `newchar-v5` 라 그대로 적용된다.

### 4. `NONUSER_KEYS` — 게이트가 자기 서술을 콘텐츠로 착각하지 않게

G6/G9 는 YAML **스칼라 leaf** 만 본다. 줄 단위 검색이면 주석이 잡힌다(실측: 토큰 후보를
`content/`+`docs/` 에 줄 단위로 걸면 56건, 전부 토큰을 _인용한_ 주석·문서). leaf 로 좁히면
2건이 남는데 그 2건도 `safety_gates[].description` — **게이트를 서술하는 노드**다(⑱ 과 같은 부류).

→ `description`·`note`·`structural_check`·`rule`·`lint_forbidden_tokens` 등만 제외한다.

> ⚠️ **`text_ko`·`*_text_ko`·`card_text_ko` 처럼 렌더되는 키는 절대 이 집합에 넣지 마라.**
> 넣는 순간 그 게이트는 vacuous green 이 된다.

카브아웃은 **노드 단위**다. 줄 단위 `grep -v lint_forbidden_tokens` 는 키 1줄만 걷어내고
블록 리스트 항목 N줄을 남긴다 → designer 가 초록으로 가는 가장 쉬운 길이 "안전 토큰을
지우는 것"이 되고, **게이트가 안전 저하에 보상을 준다.**
`flow 단일라인 강제` 는 채택하지 않는다 — 포매터 한 번이면 깨진다.

## ㉝ 배제 문자열은 "어느 텍스트를 상대로 재는지"까지가 정의다

성경 파일이 `참조<TAB>본문` 형식일 때, 예전 구현은 `cut -f2-` 에 해당하는 **본문 열만**
만들어 놓고 참조 표기(`단 6:24`)를 그 열에 대고 찾았다. 라벨을 뗀 열에는 원리상 없으므로
그 판정은 **영원히 초록**이다. 실측 재현: USED 구간에 `단 6:24<TAB>…` 를 심었을 때
본문 열 판정 3/3 통과, 라벨 열(`cut -f1`) 판정 2/3 즉시 FAIL.

그래서 배제 선언은 **매핑**이고 `scope` 는 **필수**다.

```yaml
exclusions:
  - value: "기손 시내로 내려다가"
    scope: verse_text # verses 파일의 본문 열
    expect: excluded_only # EXCLUDED 쪽에 실재 + USED 쪽에 부재
  - value: "왕상18:40"
    scope: verse_ref # verses 파일의 참조(라벨) 열
    expect: excluded_only
  - value: "갈멜산 대결"
    scope: content_leaf # 산출물 YAML 의 스칼라 leaf
    expect: absent # content_leaf 에는 USED/EXCLUDED 구분이 없다
```

- **기본값을 두지 않는다.** 기본값을 주는 순간 지금 결함이 조용히 그대로 재발한다.
  `scope` 누락·오기·구식 키(`excluded_text`/`excluded_ref`)는 **설정 오류**다 →
  `G0cfg` FAIL, 그리고 배제 게이트(G0b·G0e·G9·G9d)는 **실행하지 않고 BLOCKED**.
  잘못 선언된 목록으로 낸 초록은 초록이 아니다.
- `scope` 가 정하는 건 **G0b 의 대조 대상 열**이다. G9/G9d 는 scope 와 무관하게
  선언된 값 전부를 산출물에 대고 잰다 — 배제한 재료가 렌더되면 안 된다는 요구는 같다.
- `verses_file` 에 TAB 없는 데이터 줄이 있으면 두 열을 분리할 수 없으므로 BLOCKED 다.

## ㉞ 배열 원소를 줄여 적으면 줄임표가 원소가 된다

`token_examples` 는 짝의 **내용을 보기 전에 목록 자체**를 검사한다.

| 검사                                                       | 왜                                                                      |
| ---------------------------------------------------------- | ----------------------------------------------------------------------- | --------------------------------------------------- |
| 선언 개수(`token_examples_declared`) ≠ 실제 원소 수 → FAIL | "N종 검사한다"는 주장을 기계가 대조한다. 없으면 원소 소실이 조용히 통과 |
| `tok == ex` → 자기매칭 FAIL                                | `case "$ex" in *"$tok"*)` 가 자기 자신에 매칭돼 무조건 통과             |
| 구분자 `                                                   | ` 없는 원소 → FAIL                                                      | 생략 기호 `…` 를 그대로 옮기면 **그게 원소**가 된다 |

`token_examples_declared` 도 기본값이 없다 — 없으면 G0c 는 BLOCKED 다.

## G6 / G6d, G9 / G9d 를 나눈 이유

마크다운에는 "값 노드"가 없다. 문서가 토큰을 **금지하려고 인용한 줄**과 실제로 쓴 줄을
파서로 구분할 방법이 없어서 규약이 §5-2 / §7-2 의 **선언 라인 마커**를 요구한다.

- `G6` / `G9` — 렌더 leaf. red 면 **안전 위반**.
- `G6d` / `G9d` — 문서. red 면 **마커 규율 부채**(안전 위반이 아니다).

같은 red 라도 원인이 오귀속되면 안 된다(⑫ 가 지적한 그 병리). 그래서 게이트 id 를 나눴다.

## grep 을 쓰지 않는다

macOS BSD grep 에 `-P` 가 없고(㉔), 이 개발 셸의 `grep` 은 ugrep 래퍼라 대화형과 스크립트
결과가 갈린다(㉘). `${N^^}` 는 bash 4+ 인데 macOS 는 3.2 고(⑭), 비따옴표 `$DOCS` 워드분리는
zsh 에서 안 일어난다(⑬). 전부 셸 이식성 함정이고, **순수 python 으로 구현하면 전부 무의미해진다.**

## 설정 파일에 쓰는 것 / 안 쓰는 것

`scripts/gates/{인물}.yml` 의 키는 전부 **데이터**다. 게이트를 끄는 키는 없다.
미정의 키는 해당 게이트를 **BLOCKED** 로 만든다 — 없는 걸 있는 척하지 않는다.

주요 키: `character` `ruleset` `scene_count` `docs` `doc_min_lines` `verses_file`
`forbidden` `exclusions`(value/scope/expect) `token_examples`
`token_examples_declared` `polite_evasive` `trigger_scenes` `crisis_reminder_scenes`
`test_identifier` `application_yml` `forbidden_token_test`.

## 게이트 자신의 테스트

`scripts/gates/tests/run.sh` — **RED 픽스처가 없는 게이트는 받지 않는다.**

- `base/` = GREEN 기준선. 27개 게이트 전부 PASS 여야 러너가 케이스 검사를 시작한다.
- `cases/<게이트>__red_*/` = 그 게이트가 잡아야 할 위반을 심은 픽스처.
- RED 가 vacuous 해지는 두 경로를 둘 다 막는다:
  1. `reason_contains` 로 **실패 사유까지** 대조 — 다른 이유로 난 FAIL 은 통과시키지 않는다.
  2. **델타 검사** — RED 케이스에서 비-PASS 인 게이트 집합이 정확히 `{그 게이트}` 여야 한다.
     부수 피해로 다른 게이트까지 무너지면 그 픽스처는 아무것도 증명하지 못한다.
     불가피한 경우만 `delta_check: false` + `delta_note` 로 명시 면제한다(2건).
- `expect: BLOCKED` 케이스로 "빈 순회는 BLOCKED" 가 실제로 그렇게 나오는지도 검사한다.

테스트 자체가 초록인지 확인하려면 게이트를 일부러 망가뜨려 보면 된다(변이 검사 6건 실측):
`G5a-ii` 의 축 0개 BLOCKED 제거 → 해당 케이스 실패 /
`G3d` 를 파서 대신 텍스트 검색으로 → 해당 케이스 실패 /
`G6` 를 leaf 대신 줄 단위로 → base 기준선이 즉시 깨짐 /
`G0b` 의 verse_ref 를 본문 열에 대고 재게 → `G0b__red_verse_ref_in_used_label` 만 PASS 로 뒤집힘(㉝ 재현) /
`scope` 에 기본값 부여 → `G0cfg__red_exclusion_scope_missing` 실패 /
`token_examples_declared` 대조 생략 → `G0c__red_declared_count_mismatch` 실패.
