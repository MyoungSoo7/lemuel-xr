# MVP-SOLOMON — XR 실 콘텐츠 (Scene 별 대본·인터랙션·자원)

> **상위 문서**: `MVP-SOLOMON.md` (구조·설계 — 5 Scene / 성공 속 허무 재정향 곡선)
> **본 문서**: 실제 사용자에게 노출되는 *대본·자산·인터랙션 트리거* — Unity 6 LTS + OpenXR 환경에서 구현 가능한 수준의 데이터.
> **타겟 디바이스**: Quest 3 (기준) / Quest Pro / Vision Pro / Galaxy XR — capabilities-min: 6DoF + hand tracking + spatial audio + haptic. eye tracking optional.
> **세션 길이**: 5~7분. *짧은 허무(Scene 4) + 안착의 재정향(Scene 5)* 의 리듬이 핵심.
> **포지셔닝**: *영적 비상 대비 교육 콘텐츠* (큐티 + 실존적 공허 재정향 metaphor). *임상/의료 도구 아님 — 누구나 대상*. Scene 3(영아 상실 언급, **mid**)·Scene 4(허무 정서, **low~mid**) 안전 게이트가 콘텐츠보다 우선.
> **신학·안전 톤**: `lemuel-xr-theology-tone` 사전 가이드 적용 — disputed_points 명시, R1(위기 자원 `{{crisis_resources.default}}`) 최우선 + R2~R5 잔류. *AI 보조 — 본문은 성경 참조 — storyteller 역할* footer 자동.
> **근거 인용**: THEOLOGY-REFERENCES.md 에 **솔로몬 전용 확정 논문 미확보(S1 후보 드릴다운 필요)**. 인접 서지(Frankl·Affect Labeling·ACT·hedonic adaptation)만 *내적 단련 근거 서지* 로 격하 인용.

> ⚠️ **초안 고지** — 이 문서는 **수기 크리스탈라이즈 seed(v1.1, 독립 채점 0.82 통과) 기반** 초안(≤70%)이다. `lemuel-theology-reviewer` + `lemuel-mental-health-safety` 사후검토 필수. Scene 3 의 R4 mid 처리와 Scene 4 의 허무 정서 처리는 안전 검토자 최종 승인(Scene 4 는 인간 사인오프 권고) 전 프로덕션 반영 금지. 게이트 기록: `docs/CONTENT-EVALUATION-GATES.md` 1~2단.

---

## 0. 공통 XR 컨벤션 (Scene 전체에 적용)

### 0.1 진입·동의·안전 게이트 (Pre-Scene 0)

Scene 1 진입 *전* 반드시 노출. 건너뛰기 불가.

```
┌─────────────────────────────────────────────────────────┐
│  해 아래, 빈 손 — 솔로몬의 미션                             │
│                                                          │
│  · 길이: 약 5~7분                                        │
│  · 내용: 젊은 왕의 부담·지혜 구함·재판·성취 뒤의 공허·       │
│    재정향("하나님을 경외하라")                              │
│  · 아기를 잃은 어머니의 재판 이야기(대사 언급, 시각 묘사      │
│    없음)와 허무·공허의 정서가 포함됩니다. 각 장면 앞에서      │
│    다시 안내하고, 건너뛸 수 있습니다.                       │
│                                                          │
│  [지금 시작]  [건너뛰기 — 다른 미션 보기]                    │
│  ─────────────────────────────────────────────────────   │
│  지금 많이 지치거나 힘드시면, 게임보다 먼저 함께할 자원이     │
│  있어요.  위기 시: {{crisis_resources.default}}            │
│  (메뉴 → "안전 자원" 에서 언제든 다시 열 수 있어요)          │
└─────────────────────────────────────────────────────────┘
```

→ R4 충족. 위기 자원 카드는 *씬 진행 중에도 메뉴 → "안전 자원"* 으로 항상 노출. crisis 번호는 **하드코딩 금지** — `{{crisis_resources.default}}` 토큰을 catalog 에서 locale·연령 맞춤 렌더(엘리야 컨벤션 동일).

### 0.2 인터랙션 추상화

| 인터랙션 의도 | Quest 3 (기준) | Vision Pro | Galaxy XR | Web 폴백 |
|---|---|---|---|---|
| 물체 잡기 (제물·왕관·칼/두루마리·보물) | controller grip / pinch | pinch + dwell | controller grip | click + hold |
| 제단에 놓기 (내려놓기) | grip 해제 @ socket | pinch 해제 | grip 해제 | click "내려놓는다" |
| 판결 카드 선택 (3택) | ray + trigger / 손 터치 | gaze + pinch | ray + trigger | click |
| 허무 라벨 카드 선택 (3택, 선택적) | 동일 | 동일 | 동일 | click (건너뛰기 버튼 병행) |
| 다음 진행 | A button / pinch | pinch | A button | click "다음" |
| 메뉴/안전 자원 | menu button | 손바닥 위로 | menu | top-right ⋯ |

→ 솔로몬 미션의 정체성은 *잡는 것* 과 *놓는 것* 의 대비 — Scene 1 에서 처음 잡은 것들(왕관·칼·보물)을 Scene 5 에서 *놓을 수 있다* (강제 아님).

### 0.3 시간·공간 안락성

- 카메라 이동 없음 — 전 Scene 정지 시점 (모션시크 리스크 최소).
- 큰 환경 전환은 fade-to-black 0.8초 + 페이드인 1.2초. Scene 4→5 만 *fade-to-gentle-light* (재정향 예고).
- *3D 사운드 우선 + 텍스트 자막 동기* (청각 장애 / 무음 환경 대응).
- NPC 음성은 Coqui XTTS-v2 사전 캐시(오프라인·R5 opt-out 시에도 작동). voice_id:
  - `solomon_young_v1` — 젊은 왕. Scene 1 부담 톤 / Scene 3 판결 톤.
  - `solomon_old_v1` — 노년 회고(코헬렛) 톤. *같은 인물, 무게가 달라진 목소리* (Scene 4~5).
  - `dream_voice_v1` — 하나님의 꿈 속 음성. 형상 없음·부드러움 (Scene 2).
  - `woman_a_v1` / `woman_b_v1` — 두 여인. 절제 호소 / 왜곡 주장 — 악마화 금지.

### 0.4 결정 기록 (백엔드)

각 Scene 결정 → `POST /api/game/solomon/scene/{n}/decide` →
- `game_decisions` JSONB 기록 (V4 schema)
- `scene_views` entered/exited + `sceneN_skipped` (skip 경로 — Scene 5 오브젝트 결정에 사용). **Scene 1·2 는 decide 없음** — Scene 1 제물은 서사 상태 불변(비핵심 연출), Scene 2 는 분기 없음.
- `interaction_meta` 마이크로 신호: 판결 첫 선택·재고 횟수, 허무 카드 망설임 시간, **내려놓은 오브젝트 목록·망설임 시간**.

### 0.5 항상 노출되는 footer

화면 좌하단(UI 안전 영역) 작은 글씨 *영구* 노출:

```
AI 보조 — 본문은 성경 참조 — storyteller 역할 | 위기 시 {{crisis_resources.default}}
```

→ R5 + theology-tone 7번 + R1 항상 충족. *AI 는 치료자/전문가가 아닌 이야기 안내자 — 위기 영역은 외부 자원으로 즉시 연결*.

### 0.6 솔로몬 미션 *전체* 톤 정책 (R2/R3 사전 가드)

- *"허무를 느끼는 건 믿음/감사가 모자라서"* 류 **차단** (lint: "믿음이 부족"·"감사가 부족"·"욕심이 많아서"·"만족을 모르"). 대신 *"천하 제일의 왕도 '헛되다'고 적었다"*. <!-- lint_forbidden_tokens 인용 -->
- *"이제 깨달았으니 열심히 살라"* 류 **차단** (lint: "이제 깨달았으니"·"다시 열심히"·"허무를 극복"). 대신 *"경외는 숙제가 아니라 방향이다"*. <!-- lint_forbidden_tokens 인용 -->
- *"성공·부 = 죄"* 도 *"성공·부 = 축복의 증거"* 도 아님 — 부귀는 *덤*(왕상 3:13), 의미는 *별도의 축*(전 2:11).
- 허무는 *극복 대상* 이 아니라 *정직하게 말해도 되는 것* — 전도서 자체의 문법.

---

## 1. Scene 1 — 기브온의 일천번제 (1분)

### 1.1 배경·환경

- **공간**: 기브온 산당, 밤. 제단의 불, 별. 젊은 왕의 시점.
- **사용자 위치**: 제단 앞 1.2m. 손 근처에 *제물 꾸러미* fade-in. 머리에 *왕관* (무게 햅틱 — 낮은 지속 압력 은유).
- **에셋**: glb 10MB (manifest: `solomon/scene1/quest3/v1.0.0.json`).
- **사운드**: 제단 불 타는 소리, 밤 벌레, 고지대 바람.

### 1.2 대본

**[내레이션 — 0:00]**
> *"다윗의 아들, 젊은 왕이 기브온 산당에 올라 일천 번제를 드렸다."* (왕상 3:4, 요약)

**[내면 — 왕관의 무게 — 0:12]**
> *"아버지의 자리는 크고, 나는 작다. 이 왕관은 왜 이렇게 무거운가."*  ← 왕관 = Scene 5 내려놓기 오브젝트 #1

**[제물 올리기 (선택적·비핵심) — 0:20]**
UI: 사용자가 제물 꾸러미를 제단에 올린다(grab-and-place). **서사 상태 불변 — 올리지 않아도 25초 후 자동 진행.** 구현 optional(자동 연출 대체 허용).

**[내면 — 0:30]**
> *"종은 작은 아이라 출입할 줄을 알지 못하나이다"* (왕상 3:7, 발췌·요약 — 원문 어미는 "알지 못하고")

**[간구 — 0:44]**
> *"듣는 마음을 종에게 주사 주의 백성을 재판하여 선악을 분별하게 하옵소서"* (왕상 3:9)

→ 자동 Scene 2 진입 (fade out 0.8초).

### 1.3 인터랙션

- 제물 올리기 1회 (optional). `interaction_meta.offering_placed` 기록만 — 분기·평가에 사용하지 않음.

### 1.4 안전·신학 footer

- **신학**: 지혜 = *구함의 대상, 하나님의 선물* — 인간 성취 아님. *"작은 아이"* 의 정직한 자기 평가는 결핍이 아니라 출발점.
- **disputed**: 산당 일천번제(왕상 3:3~4) — 성전 건축 전 *과도기* 주류 해설 병기 (§12.6-4).

---

## 2. Scene 2 — 꿈에 응답하신 하나님 (45초)

### 2.1 배경·환경

- **공간**: 꿈 공간 — 기브온의 밤이 부드러운 볼륨광으로 풀어짐. 하나님 형상 없음 — *꿈 속 음성*(`dream_voice_v1`)과 빛만.
- **에셋**: 6MB. 빛 VFX 중심.
- **사운드**: 낮은 하프 드론, 꿈결 공기.

### 2.2 대본

**[내레이션 — 0:00]**
> *"기브온에서 밤에 여호와께서 꿈에 솔로몬에게 나타나셨다."* (왕상 3:5, 요약)

**[꿈 속 음성 — 0:10]**
> *"네가 이것을 구하도다 자기를 위하여 장수하기를 구하지 아니하며 부도 구하지 아니하며 자기 원수의 생명을 멸하기도 구하지 아니하고 오직 송사를 듣고 분별하는 지혜를 구하였으니"* (왕상 3:11)

**[0:23]**
> *"내가 네 말대로 하여 네게 지혜롭고 총명한 마음을 주노니"* (왕상 3:12)

**[0:33]**
> *"내가 또 네가 구하지 아니한 부귀와 영광도 네게 주노니"* (왕상 3:13)

부드러운 빛이 사용자 손에 잠시 머문다(받는 자의 자세 — 입력 요구 없음). → Scene 3 진입 직전 **R4 mid 동의 게이트**(§3.2).

### 2.3 안전·신학 footer

- 이 Scene 에 *책망·조건 연출 0건* — 구함이 기뻐 받아들여지는 톤만 (왕상 3:14 조건절은 범위 제외·disputed 이관).
- **신학**: 부귀·영광 = *덤* — 소유 죄악시 없음. Scene 4 대비 축.

---

## 3. Scene 3 — 두 여인 재판 (2분, 핵심 인터랙션 #1) ★ R4 mid 민감

### 3.1 배경·환경

- **공간**: 예루살렘 보좌 홀, 아침. 사용자는 보좌 위 왕의 시점(약간 높음). 두 여인이 앞에 서 있다. 산 아기는 *추상 강보(얼굴 미표현)*. **죽은 아기는 시각화·등장 없음.**
- **에셋**: 12MB. 보좌 홀 + 여인 2 + 강보 + 칼(별도, sword_route 에서만).
- **사운드**: 홀 잔향, 낮은 웅성거림.

### 3.2 R4 추가 동의 게이트 — Scene 3 진입 직전 (필수 · **mid 강도**)

```
┌─────────────────────────────────────────────────────────┐
│  다음 장면은 성경에서 가장 유명한 재판                       │
│  (왕상 3:16~28) 을 다룹니다.                               │
│                                                          │
│  · 갓난아기를 잃은 어머니의 이야기가 대사로 언급됩니다.       │
│    (시각적 묘사는 없습니다)                                 │
│  · "칼을 가져오라" 는 명령이 나옵니다. 칼은 아기에게 닿지     │
│    않으며, 마음을 드러내는 시험의 말로만 쓰입니다.            │
│  · 아기를 잃은 경험, 유산·사산·산후 상실의 경험이            │
│    있으시다면 이 장면이 힘드실 수 있습니다.                  │
│    건너뛰어도 이야기는 온전히 이어집니다.                    │
│                                                          │
│  계속하시겠어요?                                            │
│                                                          │
│  [계속한다]  [건너뛰기 — 재판 결과 요약 자막 후 다음 장면]    │
│  ─────────────────────────────────────────────────────   │
│  음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]                 │
│  지금 힘드시면: {{crisis_resources.default}}               │
└─────────────────────────────────────────────────────────┘
```

→ **skip 대체 경로 (결정 D — 저강도 1줄 아님)**: 요약 자막 1장(비묘사) 노출 후 Scene 4 진행 — *서사 연속성 유지*:
> *"왕은 칼을 가져오라 명하여 두 여인의 마음을 드러냈고, 산 아기는 참 어머니의 품으로 돌아갔다. 온 이스라엘이 왕 안에 있는 하나님의 지혜를 보고 두려워하였다."* (왕상 3:27~28 요약)

skip 사용자는 Scene 5 에서 *칼 대신 판결 두루마리* (칼 시각 트리거 회피 일관성).

### 3.3 대본

**[두 여인 등장 — 0:00]**
> *"그 때에 두 여자가 왕에게 와서 그 앞에 섰더라"* (왕상 3:16, 요약 — '창기' 생략은 여인 비낙인화 목적의 의도적 각색)

**[여인 A 의 호소 — 0:10]** (절제 톤 — 영아 사망은 "아이를 잃고" 로만, 경위 묘사 배제)
> *"이 여자가 밤에 아이를 잃고, 내가 잠든 사이에 내 아들을 데려다가 자기 품에 뉘었나이다. 산 아이가 내 아들이니이다."* (왕상 3:19~21, 요약)

**[여인 B 의 반박 — 0:26]**
> *"아니라 산 것은 내 아들이요 죽은 것은 네 아들이라"* (왕상 3:22)

**[판결 프롬프트 — 0:38]**
> *"증거는 없다. 두 주장만 남았다. 왕이여 — 어떻게 판결하시겠습니까."*

**[판결 카드 3택 fade-in — 0:46]**

| 카드 | 텍스트 | 경로 |
|---|---|---|
| (a) | *"산 아이를 첫째 여인에게 주라"* | 정적 재고 텍스트 → (c) 수렴 |
| (b) | *"산 아이를 둘째 여인에게 주라"* | 정적 재고 텍스트 → (c) 수렴 |
| (c) | *"칼을 가져오라"* | 성경 경로 — 지혜 시연 |

(a)/(b) 재고 텍스트 예 (전부 사전 작성 — LLM 0회):
> *"왕이 첫째 여인에게 아이를 주려 하자, 둘째 여인이 부르짖었다 — '아니라, 산 것은 내 아들이라.' 증거는 없고 두 주장만 남았다. 왕은 판결을 멈추고, 두 여인의 마음을 드러낼 다른 길을 생각했다."*

→ *오답 처리 아님* — 재고는 실패가 아니라 분별의 일부. 미선택 40초 → (c) 자동 진행.

**[칼 명령 — 1:10]** (칼은 들어올려질 뿐 아기에게 닿지 않음. "자막만" 강도 시 정지 이미지+자막)
> *"칼을 가져오라 … 산 아이를 둘로 나누어 반은 이 여자에게 주고 반은 저 여자에게 주라"* (왕상 3:24~25)

**[참 어머니의 외침 — 1:24]**
> *"청하건대 내 주여 산 아이를 그에게 주시고 아무쪼록 죽이지 마옵소서"* (왕상 3:26)

**[판결 — 즉시 — 1:36]**
> *"산 아이를 저 여자에게 주고 결코 죽이지 말라 저가 그의 어머니이니라"* (왕상 3:27)

**[경외 — 1:48]**
> *"온 이스라엘이 … 왕을 두려워하였으니 이는 하나님의 지혜가 그의 속에 있어 판결함을 봄이더라"* (왕상 3:28)

→ Scene 4 진입 직전 R4 low~mid 동의 게이트(§4.2).

### 3.4 결정 기록

```json
{
  "scene_number": 3,
  "scene_name": "two_women_judgment",
  "decision": { "judgment_choice": "first_woman", "converged_to": "sword_test" },
  "interaction_meta": {
    "judgment_first_choice": "first_woman",
    "reconsider_count": 1,
    "card_hesitation_seconds": 7.2
  }
}
```

### 3.5 안전·신학 footer

- **안전**: 죽은 아기 비시각화·경위 묘사 배제·칼 비접촉·위협 최소 시간 후 즉시 판결. 여인 B 악마화 금지 — *상실이 만든 왜곡* 으로 절제.
- **신학**: 판결의 성공조차 *"그의 속에 있는 하나님의 지혜"*(3:28) — 선물의 발현. Scene 5 내려놓기의 복선.
- **disputed**: §12.6-5 — 칼 명령의 성격(실제 집행 의도 vs 시험 장치). 시험 장치 해석으로 연출하되 논쟁 명시.

---

## 4. Scene 4 — 영광의 정점에서 "헛되다" (1~2분) ★ R4 low~mid 민감

### 4.1 배경·환경

- **공간**: 금과 상아의 궁정(왕상 10 요약). 장면 진행에 따라 **채도가 서서히 빠짐** — 영광→공허의 시각 전이. 후반 실내에 낮은 *빈 바람* 소리 상승.
- **에셋**: 13MB. 금 방패·상아 보좌·건축 모형·**보물 더미**(Scene 5 오브젝트 #3).
- **음성**: `solomon_old_v1` — 노년 회고(코헬렛) 톤.

### 4.2 R4 추가 동의 게이트 — Scene 4 진입 직전 (필수 · low~mid)

```
┌─────────────────────────────────────────────────────────┐
│  다음 장면은 모든 것을 가진 왕이 느낀 공허 —               │
│  "헛되다"(전도서) — 를 다룹니다.                           │
│                                                          │
│  · 성취 뒤의 허무·공허의 정서가 약 1~2분 이어집니다.        │
│    곧이어 재정향의 마지막 장면으로 이어집니다.               │
│  · 지금 마음이 무거우시면 건너뛰어도 이야기는 온전히         │
│    끝맺어집니다.                                           │
│                                                          │
│  계속하시겠어요?                                            │
│                                                          │
│  [계속한다]  [건너뛰기 — 바로 마지막 장면(Scene 5)으로]      │
│  ─────────────────────────────────────────────────────   │
│  음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]                 │
│  지금 힘드시면: {{crisis_resources.default}}               │
└─────────────────────────────────────────────────────────┘
```

→ skip = **Scene 5 직행** (재정향 결말 유지 — 미션 완결성 보존). skip 시 Scene 5 내려놓기 오브젝트에서 *보물 미등장* (결정 A).

### 4.3 대본

**[내레이션 — 0:00]**
> *"솔로몬 왕의 재산과 지혜가 세상의 그 어느 왕보다 큰지라"* (왕상 10:23)

**[노년의 회고 — 0:12]**
> *"나는 은을 돌 같이 흔하게 하였다. 집들을 짓고, 포도원을 일구고, 못을 팠다."* (왕상 10:27; 전 2:4~6, 요약)

**[0:27]**
> *"무엇이든지 내 눈이 원하는 것을 내가 금하지 아니하며 무엇이든지 내 마음이 즐거워하는 것을 내가 막지 아니하였으니"* (전 2:10)

**[전환 — 채도 저하 절정 — 0:42]**
> *"그 후에 내가 생각해 본즉 내 손으로 한 모든 일과 내가 수고한 모든 것이 다 헛되어 바람을 잡는 것이며 해 아래에서 무익한 것이로다"* (전 2:11)

**[인용 자막 — 0:55]**
> *"헛되고 헛되며 헛되고 헛되니 모든 것이 헛되도다"* (전 1:2)

**[공감 블록 — 교정 없음 (R2) — 1:06]**
> *"이만큼 가진 사람도 이렇게 적었다. 공허는 배부른 투정이 아니다. 지금 그 마음 그대로, 이름을 붙여 보아도 좋다."*

**[허무 라벨 카드 3택 fade-in — 1:16]** (선택은 초대 — 미선택 30초 시 라벨 없이 진행)

| 카드 | 라벨 | 손동작 |
|---|---|---|
| A *"다 이루었는데, 비어 있습니다"* | `emptiness` | 카드를 *가슴으로* 가져옴 |
| B *"멈추면 무너질 것 같아, 계속 쌓기만 합니다"* | `restlessness` | 카드를 *내려놓음* |
| C *"이 모든 것이 무슨 의미인지 모르겠습니다"* | `loss_of_meaning` | 카드를 *두 손으로 감쌈* |

→ **어느 카드에도 "죽고 싶다" 류 없음(R1).** 셋 다 정당한 명명(틀린 답 없음). 선택 → Scene 5 재정향 문구 결정.

### 4.4 결정 기록

```json
{
  "scene_number": 4,
  "scene_name": "glory_and_hevel",
  "decision": { "hevel_label": "emptiness" },
  "interaction_meta": { "card_hesitation_seconds": 6.8 }
}
```

### 4.5 안전·신학 footer

- **R1 (최우선)**: 죽음선호 구절(전도서 4장 초반 류) *인용·각색·암시 전면 금지* — 이 Scene 이 기계 검증 최우선 대상. 음성 리스너 상시.
- **R2 (가스라이팅 차단, 영구 오버레이)**:
  > *다 가진 사람에게도 공허는 옵니다. 공허를 느끼는 것은 잘못이 아닙니다. 지금 힘드시면 {{crisis_resources.default}}*
- **참고 서지(내적 단련 근거)** — Frankl 실존적 공허 + Affect Labeling(Lieberman 2007) + hedonic adaptation. *치료 메커니즘 아닌 storyteller 격하 인용*.
- **disputed**: §12.6-1(전도서 저작)·§12.6-3(헤벨 번역 스펙트럼) — 다중 입장 병기.

---

## 5. Scene 5 — 재정향: "하나님을 경외하라" + 내려놓기 (1분, 핵심 인터랙션 #2) ★

### 5.1 배경·환경

- **공간**: **기브온 제단, 새벽** — Scene 1 공간 재사용, 빛만 변화. *처음 구하던 자리로 돌아옴*(수미상관).
- **사용자 손·주변**: 경로 의존 오브젝트 (결정 A):
  - **왕관** (항상 — Scene 1 의 부담)
  - **칼** (Scene 3 완주 시) / **판결 두루마리** (Scene 3 skip 시 — 칼 시각 트리거 회피)
  - **보물** (Scene 4 방문 시 — skip 시 미등장)
- **사운드**: 새벽 새소리(드문), 낮은 하프 드론, 잦아드는 불씨.

### 5.2 대본

**[내레이션 — 0:00]**
> *"왕은 처음 구하던 자리로 돌아왔다. 제단의 불씨가 아직 남아 있었다."*

**[결론 본문 — 안착 톤(명령 톤 아님) — 0:10]**
> *"일의 결국을 다 들었으니 하나님을 경외하고 그의 명령들을 지킬지어다 이것이 모든 사람의 본분이니라"* (전 12:13)

**[내려놓기 초대 — 0:24]**
> *"손에 들려 있던 것들을 제단 앞에 내려놓아도 좋다. 내려놓지 않아도 괜찮다. 이 자리는 시험이 아니다."*

**[XR 인터랙션 — 내려놓기 (비강제) — 0:33]**
grab-and-place. 하나만/셋 다/하나도 안 내려놓아도 **모두 완주**. **대기 20초 → `treat_as_complete`** (결정 E). 재촉 프롬프트 없음 — 침묵 대기만.

**[재정향 문구 fade-in — 0:52]** — `hevel_label` × `faith_tone` 매칭(§6). 라벨 없으면(Scene 4 skip/미선택) 기본:
> *"가장 많이 가진 왕이 마지막에 남긴 말은 하나뿐이었다 — 하나님을 경외하라."* (전 12:13)

**[3초 후 자동 종료, 메인 복귀 / 트랙 A bridge 제안]**

### 5.3 재정향 문구 매트릭스 (balanced tone 기본)

| Scene 4 라벨 | 재정향 문구 |
|---|---|
| A 공허 | *"다 가진 뒤에도 비어 있던 왕이 마지막에 남긴 말은 '더 가지라'가 아니었다. 하나님을 경외하라 — 채움이 아니라 방향이었다."* |
| B 멈추지 못함 | *"쌓는 손을 잠시 멈춰도 된다. 해 아래 수고보다 먼저, 너를 지으신 분 앞에 서는 자리가 있다."* |
| C 의미 상실 | *"의미를 다 알 수 없어도 된다. 전도서는 답을 다 주지 않고, 경외라는 출발점 하나를 준다."* |

### 5.4 남은 상태의 *지속성* 메타포

- 내려놓은 오브젝트는 다음 입장 시 *다시 손에 들려 있다* — 메타포: *내려놓음은 한 번의 이벤트가 아니라 반복하는 방향이다.* (부담 톤 금지 — "또 내려놓아라" 재촉 없음)
- `users.persisted_state.solomon_last_hevel_label` 저장 → 재방문 시 부드러운 연결(강제 아님).

### 5.5 안전·신학 footer

- **R3 (재정향 압박 차단)**: *"이제 깨달았으니 열심히"* 류 어휘 0건(lint). 내려놓기 비강제·재촉 0건. F-6.6 상태 게이트 — 취약 이력 사용자에게 재정향 문구 자동 소프트닝/스킵. <!-- lint_forbidden_tokens 인용 -->
- **신학**: 전도서의 결론은 허무의 *부정* 이 아니라 허무 *곁의 방향*. "경외"는 숙제가 아니라 안착.
- **disputed**: §12.6-2 — 말년 타락(왕상 11) *범위 제외* scoping 정직 명시.

---

## 6. faith_tone 3단 분기 (Scene 5 재정향 문구 — 3 라벨 × 3 tone = 9 사전 큐레이션)

`users.faith_tone` 에 따라 동일 재정향 문구가 3단 분기. **전부 사전 작성 정적 텍스트 — 기본 경로에서 LLM 0회** (결정 F: 엘리야 lament_label 체계 동형, 9조합 정적 큐레이션).

**라벨 = 공허 (A · emptiness)**:

| tone | 출력 |
|---|---|
| **strong** | *"부귀와 영광은 하나님이 덤으로 주신 것이었다 (왕상 3:13). 채움이 끝난 자리에서 남는 것은 한 가지 — 하나님을 경외하라 (전 12:13). 그 빈 자리는 실패의 자리가 아니라 그분 앞에 서는 자리다."* |
| **balanced** | *"다 가진 왕도 비어 있었다. 전도서는 그 공허를 꾸짖지 않고 먼저 끝까지 말하게 한다. 그리고 채움이 아니라 방향 하나를 남긴다 — 경외."* |
| **soft** | *"3000년 전, 가장 많이 가진 사람도 '헛되다'고 적었다. 비어 있다고 느끼는 당신이 이상한 것이 아니다. 오늘은 그 느낌을 인정한 것으로 충분하다."* |

**라벨 = 멈추지 못함 (B · restlessness)**:

| tone | 출력 |
|---|---|
| **strong** | *"은을 돌같이 쌓은 왕이 생각해 본즉 바람을 잡는 것이었다 (전 2:11). 쌓는 손을 멈추는 것은 신앙의 후퇴가 아니다. 경외는 달리는 것이 아니라 그분 앞에 서는 것이다."* |
| **balanced** | *"멈추면 무너질 것 같은 마음도, 지금까지 쌓아 온 성실의 다른 얼굴이다. 다만 방향을 묻는 일은 멈춘 사람만 할 수 있다."* |
| **soft** | *"계속 쌓아 온 당신은 게으르지 않았다. 오늘 잠시 멈춘 것도 잘못이 아니다. 방향은 천천히 물어도 된다."* |

**라벨 = 의미 상실 (C · loss_of_meaning)**:

| tone | 출력 |
|---|---|
| **strong** | *"전도서는 '해 아래'의 무의미를 끝까지 말한 뒤에야 '하나님을 경외하라'로 맺는다 (전 12:13). 의미는 성취 안이 아니라 경외 안에서 다시 물을 수 있다."* |
| **balanced** | *"의미를 잃은 것 같을 때, 전도서는 답을 서두르지 않는다. 다만 출발점 하나를 남긴다 — 사람의 본분은 소유가 아니라 경외라는 것."* |
| **soft** | *"이 모든 게 무슨 의미인지 모르겠는 날이 있다. 전도서를 쓴 사람도 그랬다. 다 알지 못한 채로 여기 있어도 된다."* |

→ 3 라벨 × 3 tone = **9 사전 큐레이션** (LLM 캐시 sweet spot). 라벨 없음(skip/미선택) 경로는 기본 문구 1종 추가 — 총 10 정적 텍스트. 진입 감정 추가 매칭은 opt-in LLM 경로에서만(§8).

---

## 7. 디바이스별 변형 (capabilities 매핑)

```yaml
mission: solomon
scenes:
  - id: 1
    asset_manifest:
      quest3:     solomon/scene1/quest3/v1.0.0.json     # 10MB
      visionpro:  solomon/scene1/visionpro/v1.0.0.json  # 12MB
      galaxyxr:   solomon/scene1/galaxyxr/v1.0.0.json    # 8MB
      web:        solomon/scene1/web/v1.0.0.json         # 4MB (360 + 클릭형 제물)
    interactions_required: [grab_optional, place_optional]
  - id: 2
    asset_manifest:
      quest3:     solomon/scene2/quest3/v1.0.0.json      # 6MB
      visionpro:  solomon/scene2/visionpro/v1.0.0.json
      galaxyxr:   solomon/scene2/galaxyxr/v1.0.0.json
      web:        solomon/scene2/web/v1.0.0.json
    interactions_required: []
  - id: 3
    asset_manifest:
      quest3:     solomon/scene3/quest3/v1.0.0.json      # 12MB
      visionpro:  solomon/scene3/visionpro/v1.0.0.json
      galaxyxr:   solomon/scene3/galaxyxr/v1.0.0.json
      web:        solomon/scene3/web/v1.0.0.json
    interactions_required: [select_card]
    trigger_warning:
      level: medium                      # R4 mid (결정 D)
      content: ["infant_loss_mention", "sword_threat_scripture"]
      consent_card_id: "solomon_scene3_judgment_warning"
      skip_alternative_scene_id: 4
      skip_bridge: summary_caption_then_next   # 요약 자막 1장 후 진행 — 서사 연속성
  - id: 4
    asset_manifest:
      quest3:     solomon/scene4/quest3/v1.0.0.json      # 13MB
      visionpro:  solomon/scene4/visionpro/v1.0.0.json
      galaxyxr:   solomon/scene4/galaxyxr/v1.0.0.json
      web:        solomon/scene4/web/v1.0.0.json
    interactions_required: [select_card_optional]
    trigger_warning:
      level: low_medium                  # R4 low~mid
      content: ["existential_emptiness", "hevel_refrain"]
      consent_card_id: "solomon_scene4_hevel_warning"
      skip_alternative_scene_id: 5
    capabilities_min:
      r1_listener_required: true         # 허무 정서 Scene — 음성 입력 시 자해 키워드 리스너 필수
  - id: 5
    asset_manifest:
      quest3:     solomon/scene5/quest3/v1.0.0.json      # 9MB
      visionpro:  solomon/scene5/visionpro/v1.0.0.json
      galaxyxr:   solomon/scene5/galaxyxr/v1.0.0.json
      web:        solomon/scene5/web/v1.0.0.json
    interactions_required: [grab_optional, place_optional]
    conditional_assets_by_path: true     # 왕관(항상)·칼/두루마리(scene3)·보물(scene4) — 결정 A
```

→ Vision Pro *eye gaze* 있으면 Scene 5 내려놓기 대상에 시선이 머물 때 부드러운 하이라이트. Web 폴백은 내려놓기를 *"내려놓는다" 버튼* 으로 대체(의미 약화 알림).

---

## 8. 백엔드 호출 흐름 (Spring + LLM)

```
[Scene 1 시작]
  POST /api/game/solomon/start
    → game_sessions row (character='solomon')
    → 진입 감정(emotion_logs.latest) 조회
    → users.persisted_state.solomon_last_hevel_label 조회 (이전 미션 라벨)

[Scene 1] (decide 없음 — 제물은 서사 상태 불변) interaction_meta.offering_placed 만
[Scene 2] (decide 없음 — 분기 없음) scene_views entered/exited 만
[Scene 3] POST /api/game/solomon/scene/3/decide  body: {"judgment_choice": "first_woman", "reconsider_count": 1}  # 전 경로 정적, LLM 0회
[Scene 4] POST /api/game/solomon/scene/4/decide  body: {"hevel_label": "emptiness"}  # 라벨링 — 기본 경로에 LLM 없음
[Scene 5] POST /api/game/solomon/complete        → 재정향 문구 fetch (faith_tone × hevel_label = 9 정적 + 기본 1), persisted_state 업데이트, outbox_events(game.completed)
[bridge]  GET /api/track-a/ecclesiastes-bridge?session_id=...&theme=3  → Track A Theme 3 (전도서와 인생) 연결
```

→ **기본 경로 runtime LLM 호출 0회** — 재정향 문구 9+1 전부 사전 정적. LLM 은 *opt-in 예외 경로* 만(`llm_optin_only: true`): 사용자가 명시적으로 AI 응답을 켠 경우 `solomon_s5_reorientation_{label}` 키로 진입 감정 반영 톤 생성(그마저 캐시 우선).
→ 비용: 사용자당 미션 1회 = $0(전부 정적/캐시). opt-in 캐시 빌드 1회 ≈ $0.01.

---

## 9. 안전 게이트 — 실 동작 예

> *본 절은 임상 위기 개입이 아닌 **storyteller 의 안전 매너**. AI 는 치료자/전문가가 아니라 이야기 안내자. 위기 영역은 외부 자원({{crisis_resources.default}})으로 즉시 연결.*

### 9.1 R1 시뮬레이션 — Scene 4 자해 키워드 (최고 우선)

Scene 4(허무) 진행 중 사용자 *음성 입력* 에 자해·자살 키워드 감지 시:
1. `SafetyController.detectSelfHarmRisk` → severity=HIGH
2. *현재 Scene 즉시 일시정지* (fade-to-black) — `action: pause_fade_black_show_crisis_card`, 게임 로직보다 우선
3. 위기 자원 카드:
   > *"잠시 멈추겠습니다. 지금 많이 힘드시죠.*
   > *혼자 감당하지 않으셔도 돼요. 지금 바로 함께할 곳이 있어요.*
   > *[{{crisis_resources.default}} — 24시간·무료] [지금 닫고 메인으로]"*
4. *"메인으로"* 또는 60초 대기 → 미션 종료, `game_sessions.abandoned_at` 기록.
5. `safety_alerts` row(severity=high, source=game, **원문 미저장·해시만**, category='suicidal_ideation_semantic').
6. *"다 허무해" 단독* 은 severity=medium → 위기 자원 카드 *동반 노출* 하되 흐름 차단 없이 공감 블록 강조 — *"이 이야기는 그 마음을 위한 것입니다"* reframing.

추가 기계 검증: content/solomon 전체에서 *죽음선호 구절(전도서 4장 초반 류) 참조 문자열 0건* — CI grep 게이트.

### 9.2 R2 시뮬레이션 — 공감 우선·교정 없음 보장

Scene 4 는 *어떤 교정·훈계·해답도 렌더링하지 않는다*. QA 체크: 텍스트 풀에 *"믿음이 부족", "감사가 부족", "욕심이 많아서", "만족을 모르"* 어휘가 있으면 빌드 실패(lint — 5/5 yml 선언). <!-- lint_forbidden_tokens 인용 -->

### 9.3 R3 시뮬레이션 — 재정향 압박 차단

Scene 5 텍스트 풀에 *"이제 깨달았으니 / 다시 열심히 / 허무를 극복"* 어휘 감지 시 빌드 실패. 내려놓기 미수행도 *완주* 처리(20초 timeout — 결정 E). F-6.6 상태 게이트: 취약 이력 사용자에게 재정향 문구 자동 소프트닝/스킵. <!-- lint_forbidden_tokens 인용 -->

### 9.4 R4 시뮬레이션 — Scene 3·4 진입 게이트

- Scene 3 (mid): §3.2 카드. *건너뛰기* = 요약 자막 1장(비묘사) → Scene 4. *자막만/약/기본* 강도 — "자막만" 시 칼 연출 정지 이미지 처리.
- Scene 4 (low~mid): §4.2 카드. *건너뛰기* = Scene 5 직행 — 재정향 결말 유지, 보물 오브젝트만 미등장.
- 어느 skip 조합에서도 미션은 5~7분 내 완결(AC — 완결성 보존).

### 9.5 R5 시뮬레이션 — AI 응답 OFF (기본값)

`users.allow_ai_response = false` (기본):
- Scene 3 분기 → 전부 정적 (원래 LLM 없음).
- Scene 4 라벨 → 기록만, 생성 없음.
- Scene 5 재정향 문구 → *faith_tone × hevel_label* 9 정적 큐레이션 + 기본 1 (진입 감정 분기 제거).
- bridge → Track A Theme 3 정적 묵상 고정.
→ opt-out 사용자도 **동일한 서사 완결 경험** — 분기 텍스트 전부 정적 존재 (AC5·R5).

---

## 10. 콘텐츠 검수 체크리스트

| 항목 | 결과 |
|---|---|
| Pre-Scene 0 R4 동의 + 위기 자원 토큰 | ☑ |
| Scene 3 진입 시 **mid** consent_card + skip 요약 자막 경로 + 강도 토글 (결정 D) | ☑ |
| Scene 4 진입 시 low~mid consent_card + Scene 5 직행 skip | ☑ |
| 죽음선호 구절 *참조 0건* (R1 — grep) + 리스너 5/5 Scene | ☑ (CI 배선 필요) |
| 죽은 아기 시각화 0건·칼 비접촉·즉시 판결 전환 | ☑ |
| Scene 4 *공감 먼저·교정 없음* (R2 lint 5/5 선언) | ☑ |
| Scene 5 *재정향 압박 어휘 0건* (R3 lint) + 내려놓기 비강제(20초 timeout) | ☑ |
| Scene 3 분기 3개 전부 정적 — LLM 없이 완주 (AC5) | ☑ |
| 경로 의존 오브젝트(왕관/칼·두루마리/보물) 배선 (결정 A) | ☑ |
| `default_path: static_curation` + `llm_optin_only: true` (R5) | ☑ |
| crisis 하드코딩 0건 — `{{crisis_resources.default}}` 토큰만, yml 내 실측 9곳 (결정 G) | ☑ (**9곳** — Pre-Scene 0 게이트 1 + consent 2 + overlay 1 + footer 5) |
| faith_tone 3단 × 3 라벨 = 9 재정향 문구 + 기본 1 | ☑ (§6) |
| disputed_points 4개 이상 (MVP-SOLOMON §12.6 — 6개) | ☑ |
| 영구 footer *"AI 보조 — 본문은 성경 참조 \| 위기 시 {{crisis_resources.default}}"* | ☑ |
| **솔로몬 전용 확정 서지(S1·S2) 확보** | ☐ **미확보 — 드릴다운 필요** |
| **`lemuel-theology-reviewer` 사후 검토** | ☐ **미완 — 필수** |
| **`lemuel-mental-health-safety` 사후 검토 (Scene 3·4 중점)** | ☐ **미완 — 필수** |
| **CONTENT-EVALUATION-GATES 1~2단 통과 기록** (3단 합의 비발동 — R1 본문 부재. 단 Scene 4 는 인간 사인오프 권고) | ☐ **미완 — 필수** |

→ 출판 전 **운영자 self-review + 두 에이전트 사후 검토 + 게이트 기록** 이 검수 게이트.

---

## 11. 다음 단계

1. 본 콘텐츠를 *Scene yml + AssetManifest JSON* 으로 분해 (content/solomon/scene{1..5}.yml — 본 커밋에 초안 포함).
2. Coqui XTTS-v2 로 6 voice 사전 합성 (`solomon_young_v1`·`solomon_old_v1`·`dream_voice_v1`·`woman_a_v1`·`woman_b_v1`·`narrator_v1`). *같은 인물의 젊음/노년 톤 대비* 튜닝이 핵심.
3. 재정향 문구 *9+1 조합* 정적 확정 — Scene 5 runtime LLM 0회.
4. **솔로몬 전용 확정 서지(S1·S2) 드릴다운** — THEOLOGY-REFERENCES.md 에 추가 (전도서 헤벨 주해 / 실존적 공허 목회상담 / hedonic adaptation).
5. **`lemuel-theology-reviewer` + `lemuel-mental-health-safety` 사후 검토** — Scene 3 mid 처리·Scene 4 허무 처리 승인 전 프로덕션 반영 금지. Scene 4 인간 사인오프 권고.
6. 경로 의존 conditional_assets 배선 *실사용자 테스트* — skip 조합 4가지(3O4O/3O4X/3X4O/3X4X) 전부 완주 확인 (5명).
7. Track A Theme 3 bridge 연동 — 성공 속 허무 → 전도서 묵상 연결.

---

*이 문서는 lemuel-xr Phase 2+ 의 솔로몬 MVP XR 실 콘텐츠 초안 — 구원 카테고리 6번째 **성공 속 허무(실존적 공허) 재정향**, Theme 13. *영적 비상 대비 교육*, *누구나* 대상, *임상/의료 도구 아님*. **수기 seed(독립 채점 0.82 통과) 기반**이며 `lemuel-theology-reviewer` + `lemuel-mental-health-safety` 사후검토 필수. `lemuel-xr-theology-tone` 사전 가이드 + R1~R5 반영. AI 역할은 치료자/전문가 아닌 storyteller.*
*핵심 통찰 — 전도서는 허무를 극복하라 하지 않는다. 끝까지 말하게 한 뒤, 방향 하나를 남긴다 — 하나님을 경외하라. (전 12:13)* <!-- lint_forbidden_tokens 인용 -->
