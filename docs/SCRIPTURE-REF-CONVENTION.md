# `scripture_ref` 참조 규약

시나리오(`backend/src/main/resources/scenarios/*.yml`)가 적는 성경 참조 문자열과
`scripture_passages` 테이블의 `reference` 열을 맞추는 규약. 2026-08-20 제정.

기계 검사: `scripts/scripture_ref_check.py` (CI 러너 `scripture-ref:all`).

## 왜 규약이 필요했나

규약이 없던 동안 시나리오 8편이 서로 다른 문법으로 참조를 적었다.

| 적힌 것                | 어디        | 조회 결과                  |
| ---------------------- | ----------- | -------------------------- |
| `job-42:5`             | `job.yml`   | 열림                       |
| `ps-23:1-6`            | `david.yml` | 안 열림 — 범위 문자열      |
| `ruth-1:8,1:9,1:16`    | `ruth.yml`  | 안 열림 — 쉼표 묶음        |
| `ruth-1:19,1:20,1:21a` | `ruth.yml`  | 안 열림 — 반절 접미사 `a`  |
| `mt-5:3`               | `jesus.yml` | 안 열림 — 책 약어가 `matt` |

`reference` 열은 단일 절 키(`book_code-chapter:verse`) 하나만 안다.
`findFirstByReferenceAndTranslation` 은 정확 일치 조회다 —
범위·묶음·접미사는 파싱되지 않고 그냥 **안 맞는다**.

2026-08-20 실측으로 참조 44개 중 31개가 이 이유로 열리지 않았다.

## 규약

**1. `scripture_ref` 는 언제나 단일 절 키다.**

```
<book_code>-<chapter>:<verse>
```

정규식: `^[a-z0-9]+-\d+:\d+$`. 범위(`-6`)·쉼표 묶음(`,1:9`)·반절 접미사(`21a`) 금지.

**2. 절이 더 필요하면 `extras.additional_refs` 에 단일 절 키의 배열로 적는다.**

```yaml
scripture_ref: ruth-1:8
extras:
  additional_refs: ["ruth-1:9", "ruth-1:16"]
```

**3. `book_code` 는 시드가 이미 쓰는 약어를 따른다.**

정본 목록의 근거는 `V6__scripture_embeddings.sql:11` 주석
(`'gen' | 'ex' | '1sam' | 'ps' | 'matt'`) 과 실제 시드 행이다.
같은 책이 두 약어로 살면 안 된다 — `mt` 와 `matt` 가 그랬다.

**4. 한 절이 여러 절과 합쳐 번역된 경우, 키는 시작 절이고 `verse_end` 가 범위를 담는다.**

현대인의 성경은 삼상 17:38-39 · 창 41:34-35 · 룻 4:18-22 를 한 덩어리로 번역한다.
이때 행은 하나(`1sam-17:38`, `verse_end=39`)이고, 시나리오도 그 시작 절 키를 적는다.
39절을 따로 참조할 방법은 없다 — 번역본에 그런 단위가 없기 때문이다.

## 이 규약이 보장하지 않는 것

키가 맞는다는 것은 **행이 있다** 는 뜻이지 **자구가 맞다** 는 뜻이 아니다.
본문 자구는 별도 검사(`scripts/scripture_text_check.py`) 가 잰다. 그리고 지금
미션 화면(`frontend/src/app/<char>/page.tsx`)은 `scriptureRef` 를 렌더하지
않는다 — `ScenePayloadAssembler:51` 이 문자열을 조인 없이 그대로 실어 보낼 뿐이다.
초록은 "열 수 있다" 이지 "열린다" 가 아니다.
