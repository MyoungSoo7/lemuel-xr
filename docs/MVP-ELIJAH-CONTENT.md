# MVP-ELIJAH — XR 실 콘텐츠 (Scene 별 대본·인터랙션·자원)

> **상위 문서**: `MVP-ELIJAH.md` (구조·설계 — 5 Scene / 번아웃 회복 곡선)
> **본 문서**: 실제 사용자에게 노출되는 *대본·자산·인터랙션 트리거* — Unity 6 LTS + OpenXR 환경에서 구현 가능한 수준의 데이터.
> **타겟 디바이스**: Quest 3 (기준) / Quest Pro / Vision Pro / Galaxy XR — capabilities-min: 6DoF + hand tracking + spatial audio + haptic. eye tracking optional.
> **세션 길이**: 5~7분. *짧은 무너짐(Scene 2) + 충분한 돌봄(Scene 3)* 의 리듬이 핵심.
> **포지셔닝**: *영적 비상 대비 교육 콘텐츠* (큐티 + 번아웃 회복 metaphor). *임상/의료 도구 아님 — 누구나 대상*. 단 **R1(자살사고 본문) 민감도가 4 인물 중 최고** — 안전 게이트가 콘텐츠보다 우선.
> **신학·안전 톤**: `lemuel-xr-theology-tone` 사전 가이드 적용 — disputed_points 명시, R1(위기 자원 {{crisis_resources.default}}) 최우선 + R2~R5 잔류. *AI 보조 — 본문은 성경 참조 — storyteller 역할* footer 자동.
> **근거 인용**: THEOLOGY-REFERENCES.md 에 **엘리야 전용 확정 논문 미확보(E1 후보 드릴다운 필요)**. 인접 서지(Affect Labeling·Frankl·ACT)만 *내적 단련 근거 서지* 로 격하 인용.

> ⚠️ **초안 고지** — 이 문서는 ouroboros interview 대체 **수기 크리스탈라이즈 seed 기반** 초안(≤70%)이다. `lemuel-theology-reviewer` + `lemuel-mental-health-safety` 사후 검토 필수. Scene 2 의 R1 처리는 안전 검토자 최종 승인 전 프로덕션 반영 금지.

---

## 0. 공통 XR 컨벤션 (Scene 전체에 적용)

### 0.1 진입·동의·안전 게이트 (Pre-Scene 0)

Scene 1 진입 *전* 반드시 노출. 건너뛰기 불가.

```
┌─────────────────────────────────────────────────────────┐
│  로뎀나무 아래 — 엘리야의 미션                              │
│                                                          │
│  · 길이: 약 5~7분                                        │
│  · 내용: 큰 승리 다음의 무너짐·탈진·돌봄·고요·다시 걸음      │
│  · 강한 정서적 표현 (탈진·"생명을 거두소서"는 성경 본문      │
│    인용으로 절제하여 다룹니다) 포함                        │
│                                                          │
│  [지금 시작]  [건너뛰기 — 다른 미션 보기]                    │
│  ─────────────────────────────────────────────────────   │
│  지금 많이 지치거나 힘드시면, 게임보다 먼저 함께할 자원이     │
│  있어요.  위기 시:                                          │
│  {{crisis_resources.default}} (자살예방상담 · 24시간)       │
│  (메뉴 → "안전 자원" 에서 언제든 다시 열 수 있어요)          │
└─────────────────────────────────────────────────────────┘
```

→ R4 충족. 위기 자원 카드는 *씬 진행 중에도 메뉴 → "안전 자원"* 으로 항상 노출.

### 0.2 인터랙션 추상화

| 인터랙션 의도 | Quest 3 (기준) | Vision Pro | Galaxy XR | Web 폴백 |
|---|---|---|---|---|
| 물체 잡기 (항아리·떡·물병·겉옷) | controller grip / pinch | pinch + dwell | controller grip | click + hold |
| 먹기/마시기 (손을 입으로) | 손을 얼굴 근접 | pinch + 얼굴 근접 | 손을 얼굴 근접 | 버튼 "먹는다" |
| 물 붓기 (기울이기) | grip + 회전 | pinch + 회전 | grip + 회전 | click + drag |
| **무동작 유지 (세미한 소리)** | 손 내려놓고 정지 dwell | hand 휴식 자세 | 동일 | "가만히 있기" 버튼 |
| 얼굴 가리기 (왕상 19:13) | 손을 얼굴 앞으로 | 동일 | 동일 | 자동 연출 |
| 다음 진행 | A button / pinch | pinch | A button | click "다음" |
| 메뉴/안전 자원 | menu button | 손바닥 위로 | menu | top-right ⋯ |

→ 엘리야 미션의 정체성은 *"행위" 만큼 "쉼과 무동작"*. 특히 **Scene 4 의 무동작(stillness)** 은 lemuel-xr 유일하게 *반응하지 않는 것* 을 인터랙션으로 삼는다.

### 0.3 시간·공간 안락성

- 카메라 *이동 최소* — Scene 2 의 *광야 하룻길* 만 짧은 자동 locomotion(속도 낮게, fade 병행, 모션시크 회피). 나머지 정지 시점.
- 큰 환경 전환은 fade-to-black 0.8초 + 페이드인 1.2초.
- *3D 사운드 우선 + 텍스트 자막 동기* (청각 장애 / 무음 환경 대응).
- NPC 음성(엘리야·전령·천사·세미한 소리)은 Coqui XTTS-v2 사전 캐시(오프라인·R5 opt-out 시에도 작동). voice_id:
  - `elijah_v1` — 지친 중년 남성. Scene 1 승리 톤 / Scene 2 탈진 톤 대비.
  - `messenger_v1` — 전령. 이세벨의 위협을 *전달* (냉정·짧게).
  - `angel_v1` — 성별 중립·부드러움. *재촉이 아닌 다독임*.
  - `whisper_v1` — **세미한 소리**. 극저볼륨 + 근접감. *큰 소리가 아니라 가까운 소리*.

### 0.4 결정 기록 (백엔드)

각 Scene 결정 → `POST /api/game/elijah/scene/{n}/decide` →
- `game_decisions` JSONB 기록 (V4 schema)
- `scene_views` entered/exited (Window of Tolerance 분석). **Scene 2 는 decide 없음** — 무너짐은 선택이 아님, entered/exited 만.
- `interaction_meta` 마이크로 신호: 물 붓기 횟수, 떡 먹기까지 걸린 시간, **Scene 4 무동작 유지 초(stillness_seconds)**, 겉옷 던짐 여부.

### 0.5 항상 노출되는 footer

화면 좌하단(UI 안전 영역) 작은 글씨 *영구* 노출:

```
AI 보조 — 본문은 성경 참조 — storyteller 역할 | 위기 시 {{crisis_resources.default}}
```

→ R5 + theology-tone 7번 + R1({{crisis_resources.default}}) 항상 충족. *AI 는 치료자/자살예방 전문가가 아닌 이야기 안내자 — 위기 영역은 외부 자원으로 즉시 연결*.
> (2026-08-02 해소: 코드베이스 footer 도 `109` 로 교정됐고, 런타임 시나리오는 `{{crisis_resources.default}}` 토큰만 쓴다 — 실제 번호는 `crisis_resources` 정본에서 주입된다.)

### 0.6 엘리야 미션 *전체* 톤 정책 (R2/R3 사전 가드)

- *"믿음이 강하면 번아웃 안 온다"* 류 **차단**. 대신 *"불로 응답받은 선지자도 다음 날 무너졌다"*.
- *"빨리 회복해서 다시 사역하라"* 류 **차단**. 대신 *"오늘은 먹고 잔 것으로 충분합니다"*. <!-- lint_forbidden_tokens 인용 -->
- *"쉬는 건 게으름/불신앙"* 류 **차단**. 하나님이 *두 번 재우신* 순서(왕상 19:5~7)로 *쉼의 정당성* 명문화.
- *"무너짐 = 죄"* 가 아니라 *"무너짐 = 돌봄이 필요한 신호"* — 자기비난 reframe.

---

## 1. Scene 1 — 갈멜산의 불 (1분)

### 1.1 배경·환경

- **공간**: 갈멜산 정상, 저녁. 사용자가 다시 쌓은 12 돌 제단, 도랑, 젖은 제물. 하늘은 3년 닫혀 있었다(건조·먼지 톤).
- **사용자 위치**: 제단 앞 1.2m, 손에 *물 항아리* 자동 fade-in.
- **에셋**: glb 12MB (manifest: `elijah/scene1/quest3/v1.0.0.json`). 불 VFX(Visual Effect Graph) 별도.
- **사운드**: 낮은 바람, 먼 군중 웅성거림(작게), 물 붓는 소리. 불 임할 때 저주파 + 화이트 임팩트(강도 토글).

### 1.2 대본

**[내레이션 — 0:00]**
> *"삼 년 동안 하늘이 닫혔다.*
> *한 사람이 갈멜산에 제단을 다시 쌓고,*
> *젖은 나무 위에 물을 부었다 — 세 번."*

**[사용자 손에 물 항아리 fade-in — 0:10]**

UI: 사용자가 *항아리를 기울여 제물 위에 붓는다*. 세 번 반복(왕상 18:33~35). 도랑에 물이 찬다.

**[불의 응답 — VFX — 0:40]**
> *"이에 여호와의 불이 내려서 번제물과 나무와 돌과 흙을 태우고*
> *또 도랑의 물을 핥은지라"* (왕상 18:38)

**[엘리야 내면 — 0:50]**
> *"응답하셨다. 온 백성이 엎드렸다.*
> *… 그런데 왜 나는 이렇게 비어 있는가."*  ← 번아웃 복선

→ 자동 Scene 2 진입 (fade out 0.8초).

### 1.3 인터랙션

- 물 붓기 3회. `interaction_meta.water_pour_count` 기록.
- 불 임팩트 강도 토글(off/약/강, R4) — 큰 소리·번쩍임 민감 사용자 보호.

### 1.4 안전·신학 footer

- **폭력 배제 (필수)**: *바알 선지자 처형(왕상 18:40)은 다루지 않음*. Scene 1 은 *불의 응답까지만*. 트라우마 자극·폭력 본문 회피. (디스클레이머: *"이 미션은 갈멜산 사건의 승리와 그 다음 날의 회복에 집중합니다."*)
- **신학**: 물 붓기 = *인간의 준비*, 불 = *하나님의 몫* — 둘의 *구분*. *"다 맡기고 아무것도 안 함"* 이 아니라 *준비하되 응답은 하나님께* (다윗 D1 *믿음+능력* 균형 톤과 일관).
- **disputed**: 없음(Scene 1). 폭력 본문은 §12.6-3 로 이관.

---

## 2. Scene 2 — 이세벨의 위협과 로뎀나무 (1~2분) ★ 최고 민감

### 2.1 배경·환경

- **공간(전반)**: 갈멜산의 밤. *전령* 한 명이 다가와 이세벨의 말을 전한다(실사 이세벨 등장 없음 — 전령의 전달로만).
- **공간(후반)**: 광야. *하룻길 자동 이동*(속도 낮게 + fade, 모션시크 회피). 지평선까지 빈 땅. 한 그루 *로뎀나무*.
- **사용자 위치**: 로뎀나무 아래 *주저앉는 시점*(카메라가 낮아짐).
- **에셋**: 10MB. 로뎀나무 단일 + 광야 스카이박스.
- **사운드**: 밤바람, 사용자 *지친 숨소리*(느리고 무겁게), 멀어지는 갈멜산의 소리.

### 2.2 R4 추가 동의 게이트 — Scene 2 진입 직전 (필수)

```
┌─────────────────────────────────────────────────────────┐
│  다음 장면은 *큰 승리 다음의 무너짐과 탈진* 을 다룹니다.     │
│                                                          │
│  · 엘리야가 지쳐 "생명을 거두소서" 라고 말하는 성경 본문     │
│    (열왕기상 19:4) 이 *인용 자막* 으로 잠시 나옵니다.       │
│  · 약 1~2분. 곧이어 *돌봄과 회복* 의 장면으로 이어집니다.   │
│                                                          │
│  계속하시겠어요?                                            │
│                                                          │
│  [계속한다]  [건너뛰기 — 바로 회복 장면(Scene 3)으로 이동]   │
│  ─────────────────────────────────────────────────────   │
│  음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]                 │
│  지금 힘드시면:                                             │
│  {{crisis_resources.default}} (자살예방상담 · 24시간)       │
└─────────────────────────────────────────────────────────┘
```

→ R4 충족. *건너뛰기 = Scene 3 직행*(회복 장면부터). 무너짐을 *보지 않고도* 돌봄을 받을 수 있다.

### 2.3 대본

**[전령, 갈멜산 밤 — 0:00]**
> *"이세벨이 전하기를 —*
> *'내가 내일 이맘때에는 반드시 네 생명을*
> *저 사람들 중 한 사람의 생명 같게 하리라'"* (왕상 19:2)

**[엘리야, 일어나 도망 — 하룻길 자동 이동 — 0:20]**
> *"그가 이 형편을 보고 일어나 자기의 생명을 위해 도망하였다."* (왕상 19:3)

**[로뎀나무 아래 주저앉음 — 0:50]**

화면에 한 줄 (인용 자막, `elijah_v1` 절제된 낭독 — R1):
> *"여호와여 넉넉하오니 지금 내 생명을 거두시옵소서*
> *나는 내 조상들보다 낫지 못하니이다"* (왕상 19:4)

**[3초 정적]** — *무너짐에 오래 머물지 않는다.* 곧 부드러운 빛이 스며들며 Scene 3 자동 전환.

### 2.4 R1 처리 — 절대 원칙 (안전 검토 게이트)

1. 왕상 19:4 는 **성경 인용 자막 + 절제 낭독으로만**. *사용자 선택지·게임 목표·성취로 만들지 않는다.*
2. Scene 2 에는 **사용자 결정 분기가 없다**(decide 엔드포인트 없음). 사용자는 *앉아 쉴 뿐*. 무너짐은 *체험*, 성취 아님.
3. 본문 노출 직후 *최대 3초* → 즉시 Scene 3(돌봄). **절망 상태 고착 방지.**
4. *음성 입력* 기능이 켜져 있고 사용자 발화에 자해·자살 키워드 감지 시 → `SafetyController.detectSelfHarmRisk` → severity=HIGH → *즉시 fade-to-black + 위기 자원 카드({{crisis_resources.default}}) + 미션 일시정지*. 게임 로직보다 우선. `safety_alerts` row(원문 미저장, 해시만, category='suicidal_ideation_semantic').
5. AI 생성 문구를 이 Scene 에 두지 않는다 — *결정론적 본문 텍스트만*.

### 2.5 안전·신학 footer

- **R2**: *"엘리야가 약해서 이렇게 됐다"* 자동 해석 차단. *"불로 응답받은 자도 무너질 수 있다 — 그것은 실패가 아니다"* 톤.
- **참고 서지(내적 단련 근거)** — Affect Labeling(Lieberman 2007): 탈진 상태를 *부인하지 않고 인정* 하는 것이 회복의 시작. *치료 메커니즘 아닌 storyteller 격하 인용*.
- **disputed**: §12.6-1 — 로뎀나무 탄식의 *임상적 우울/자살사고 vs 영적 탈진* 해석. *임상 진단 주장 안 함.*

---

## 3. Scene 3 — 천사의 떡과 물, 그리고 잠 (1~2분, 게임의 심장) ★

### 3.1 배경·환경

- **공간**: 같은 로뎀나무 아래. 부드러운 새벽빛. *천사*는 실사 인물이 아닌 *추상적 광휘*(따뜻한 볼륨광). 사용자 앞에 *숯불에 구운 떡 + 물 한 병*.
- **사용자 위치**: 여전히 나무 아래 앉은 시점.
- **에셋**: 8MB. 떡·물병 mesh + 광휘 VFX.
- **사운드**: 아주 낮은 하프 드론, 부드러운 숨결, 물 따르는 소리.

### 3.2 대본

**[천사, 어루만짐 — 0:00]**
> *"일어나서 먹으라"* (왕상 19:5)

**[XR 인터랙션 — 떡·물 — 0:10]**
1. 떡 fade-in → 사용자가 *손으로 잡아 든다*(따뜻한 햅틱, 온기 지속 진동 강도 0.25).
2. 떡을 *입 쪽으로 가져간다*(먹기 제스처).
3. 물병을 *들어 마신다*.

**[다시 눕기 — 잠 연출 — 0:35]**
화면이 부드럽게 어두워짐(잠). *어떤 요구·질책·평가도 없음.*

**[천사, 두 번째로 깨움 — 0:50]**
> *"일어나 먹으라 네가 갈 길을 다 가지 못할까 하노라"* (왕상 19:7)

4. *두 번째 떡* 을 먹고 물을 마신다. 다시 잠시 쉰다.

**[내레이션 — 1:15]**
> *"그가 일어나 먹고 마시고*
> *그 음식물의 힘을 의지하여 사십 주 사십 야를 갔다."* (왕상 19:8, 요약)

→ Scene 4 진입(fade out 0.8초).

### 3.3 인터랙션

- 떡 잡기 → 먹기 → 물 마시기 → 눕기, *두 번 반복*. `interaction_meta.time_to_first_bite_seconds`(먹기까지 망설인 시간 — *쉼에 대한 죄책감* 의 미세 신호).
- **강제 없음**: 사용자가 먹지 않고 시간이 지나면 천사가 *한 번 더 부드럽게* 권함(재촉 아님). 그래도 안 먹으면 자동 진행(먹은 것으로 처리).

### 3.4 안전·신학 footer — R2 핵심 ★

- **R2 (가스라이팅 차단, 영구 표시)**:
  > *하나님은 무너진 엘리야에게 "왜 도망쳤느냐" 를 *먼저 묻지 않으셨습니다*. 떡과 물과 잠이 먼저였습니다. 회복은 교훈보다 돌봄이 앞섭니다. {{crisis_resources.default}}* <!-- lint_forbidden_tokens 인용 -->
- **신학 — 돌봄의 순서**: 왕상 19:5~7 의 *책망 없는 돌봄* + *두 번 재우심*. *"쉼은 게으름이 아니라 갈 길을 위한 준비"*(19:7 "네가 갈 길을 다 가지 못할까 하노라").
- **참고 서지(내적 단련 근거)** — 번아웃 회복의 *생리적 안정 우선성*: 과각성 상태에서 *수면·영양* 이 인지적 재구성보다 선행. *치료 처방 아닌 서사 근거 서지*.
- **disputed**: 없음(Scene 3). *천사 형상* 은 추상 광휘로 처리(실사 배제) — 우상·과잉 시각화 우려 차단.

---

## 4. Scene 4 — 호렙산의 세미한 소리 (1~2분, XR 핵심) ★

### 4.1 배경·환경

- **공간**: 호렙산 동굴 어귀. 사용자는 동굴 안에서 밖을 향해 섬. 밤. 별.
- **사용자 위치**: 정지 시점, 동굴 입구 안쪽 1m.
- **에셋**: 14MB. 동굴 + 바람/지진/불 VFX 3종.
- **사운드**: 정적이 기본. 바람(강한 저주파), 지진(럼블 + 햅틱), 불(크래클) 이 *차례로* 지나감. 그 뒤 *극도의 정적* → `whisper_v1` 근접 세미한 소리.

### 4.2 대본

**[하나님의 질문 — 0:00]**
> *"엘리야야 네가 어찌하여 여기 있느냐"* (왕상 19:9)

**[세 현상이 밖을 지나감 — 사용자는 반응하지 않는다 — 0:15]**
1. **크고 강한 바람** — *"여호와께서 바람 가운데에 계시지 아니하며"* (왕상 19:11)
2. **지진** — *"지진 가운데에도 계시지 아니하며"* (왕상 19:11)
3. **불** — *"불 가운데에도 계시지 아니하더니"* (왕상 19:12)

→ 각 현상에서 사용자가 *손을 뻗거나 피하지 않고 가만히 있을수록* 다음 단계 진행(무동작 dwell). *반응하면* 현상이 잠시 반복되며 *"이 안에 그분이 계시지 않는다"* 를 다시 안내(부드럽게, 실패 아님).

**[완전한 정적 → 세미한 소리 — 0:50]**
> *"불 후에 세미한 소리가 있는지라"* (왕상 19:12)

**[얼굴 가리기 — 0:58]**
> *"엘리야가 듣고 겉옷으로 얼굴을 가리고 나가 굴 어귀에 서매"* (왕상 19:13)
사용자가 *손을 얼굴 앞으로* 가져가는 제스처.

**[하나님, 다시 물으심 — 유일한 분기 — 1:05]**
> *"네가 어찌하여 여기 있느냐"* (왕상 19:13)

사용자 정면 1.2m 에 *정직한 토로* 3 카드 fade-in:

| 카드 | 엘리야 톤 (본문) | 감정 라벨 | 손동작 |
|---|---|---|---|
| A | *"오직 나만 홀로 남았습니다"* (왕상 19:14) | loneliness | 카드를 *가슴으로* 가져옴 |
| B | *"내가 열심을 다했으나 소용이 없습니다"* (왕상 19:14) | burnout | 카드를 *내려놓음* |
| C | *"저는 두렵습니다"* (왕상 19:3 정서) | fear | 카드를 *두 손으로 감쌈* |

→ **어느 카드에도 "죽고 싶다" 없음.** 세 카드 모두 *정당한 토로*(틀린 답 없음, R3). 선택 → Scene 5 하나님 응답 톤 결정.

### 4.3 인터랙션

- **무동작(stillness) dwell** — lemuel-xr 유일. `interaction_meta.stillness_seconds` = 세 현상 동안 반응 없이 머문 시간(*과각성 완화* 미세 신호).
- 토로 카드 선택 → `POST /api/game/elijah/scene/4/decide` body `{"lament_label": "loneliness|burnout|fear", "stillness_seconds": 14.2}`.

### 4.4 결정 기록

```json
{
  "scene_number": 4,
  "scene_name": "still_small_voice",
  "decision": { "lament_label": "loneliness" },
  "interaction_meta": {
    "stillness_seconds": 14.2,
    "reaction_to_phenomena_count": 1,
    "card_hesitation_seconds": 5.4
  }
}
```

### 4.5 안전·신학 footer

- **참고 서지(내적 단련 근거)** — Affect Labeling(Lieberman 2007) + ACT(Hayes): *부정 감정에 정직하게 이름 붙이기*. *없애려* 하지 않고 *말할 수 있게*. *치료 메커니즘 아닌 격하 인용*.
- **신학 — 고요 속 임재**: 하나님은 *극적인 것(바람·지진·불)이 아니라 고요* 속에 계신다. 번아웃은 *더 큰 자극* 이 아니라 *조용함* 으로 회복된다.
- **disputed**: §12.6-2 — *"세미한 소리"* 번역(still small voice / gentle whisper / sheer silence). 다중 입장 병기, *"극적인 것이 아닌 고요 속 임재"* 공통 함의만 채택.

---

## 5. Scene 5 — 다시 걸음 + 회복 메시지 (30초~1분) ★

### 5.1 배경·환경

- **공간**: 동굴 어귀. 세미한 소리 이어짐. 저 멀리 어둠 속 *작은 불빛 여럿*(칠천 명 상징) 서서히 켜짐.
- **사용자 손**: *겉옷(mantle)* 이 들려 있음. 멀리 *엘리사*(추상 실루엣, 얼굴 디테일 최소).
- **사운드**: 낮은 드론, 세미한 소리, 불빛 켜질 때 따뜻한 차임.

### 5.2 대본

**[하나님의 응답 — Scene 4 카드에 따라 — 0:00]**

`lament_label` 별 하나님 응답 1줄 (사전 캐싱, R5 off 시 본문만):

| Scene 4 카드 | 하나님 응답 (whisper_v1) |
|---|---|
| A loneliness | *"내가 이스라엘 가운데에 칠천 명을 남겼다 — 다 바알에게 무릎 꿇지 아니한 자다. (왕상 19:18) 너만 남은 것이 아니다."* |
| B burnout | *"네 열심을 내가 안다. 이제는 네가 혼자 지지 않도록, 엘리사를 네 곁에 둔다. (왕상 19:16)"* |
| C fear | *"바람도 지진도 불도 아니었다. 나는 가장 조용한 곳에서 너를 만났다. 두려움 가운데서도."* |

**[XR 인터랙션 — 겉옷 던지기 (선택) — 0:20]**
> *"엘리야가 그리로부터 떠나 … 겉옷을 그의 위에 던지니라"* (왕상 19:19)
사용자가 *겉옷을 엘리사에게 가볍게 던진다*(Grab + Throw, 다윗 sling 재사용). *던지지 않고 지나가도 무방* — 사명 수락은 강제 아님(R3).

**[회복 문구 fade-in — 0:35]** — 진입 감정 + `lament_label` 조합 매칭(§5.3).

**[3초 후 자동 종료, 메인 복귀]**

### 5.3 회복 문구 매트릭스 (balanced tone 기본)

| Scene 4 라벨 | 회복 문구 |
|---|---|
| A 외로움 | *"너만 남은 것이 아니었다. 보이지 않는 곳에 칠천이 무릎 꿇지 않고 있었다."* |
| B 소진 | *"네 열심이 헛되지 않았다. 다만 지금은 걸을 사람 하나를 곁에 두는 때다."* |
| C 두려움 | *"바람도 지진도 불도 아니었다. 하나님은 가장 조용한 곳에서 너를 만나셨다."* |

### 5.4 남은 상태의 *지속성* 메타포

- *로뎀나무* 는 다음 입장 시 다시 나타남 — *"무너짐은 다시 올 수 있고, 그때도 떡과 잠이 먼저다."*
- `users.persisted_state.elijah_last_lament` 저장 → 재방문 시 *"지난번엔 '나만 남았다'고 했었지"* 로 부드럽게 이어짐(강제 아님, 부담 톤 금지).

### 5.5 안전·신학 footer

- **R3 (회복 압박 차단)**: *"이제 다시 사역하라/이겨라"* 어휘 0건. *"오늘은 먹고 잔 것으로 충분하다"* 가 기본. 사명은 *혼자 지는 것 → 나누는 것*(엘리사).
- *"하나님이 너를 도구로 다시 쓰신다"* 같은 *도구화* 차단 → *"너의 걸음에 동행을 주셨다"* 주체성·관계 톤.
- 영구 footer: *"AI 보조 — 본문은 성경 참조 — storyteller 역할 | 위기 시 {{crisis_resources.default}}"*.

---

## 6. faith_tone 3단 분기 (Scene 5 회복 문구 예)

`users.faith_tone` 에 따라 동일 회복 문구가 3단 분기.

**라벨 = 외로움 (A)**:

| tone | 출력 |
|---|---|
| **strong** | *"여호와께서 칠천을 남기셨다 — 네가 세지 못한 동행이 있다. 너의 외로움은 끝이 아니라 하나님이 채우실 자리다. (왕상 19:18)"* |
| **balanced** | *"너만 남은 것이 아니었다. 보이지 않는 곳에 칠천이 무릎 꿇지 않고 있었다. 외로움이 사실을 다 말해주지는 않는다."* |
| **soft** | *"3000년 전 지친 사람도 '나만 남았다' 고 느꼈다. 그 느낌이 틀릴 때도 있다. 지금은 그저 여기 있어도 된다."* |

**라벨 = 소진 (B)**:

| tone | 출력 |
|---|---|
| **strong** | *"네 열심을 하나님이 아신다. 그분은 책망 대신 떡과 잠을 먼저 주셨고, 이제 엘리사를 곁에 두신다. 너는 혼자 지지 않아도 된다. (왕상 19:7, 16)"* |
| **balanced** | *"네 열심이 헛되지 않았다. 다만 지금은 걸을 사람 하나를 곁에 두는 때다. 소진은 게으름이 아니다."* |
| **soft** | *"다 소진된 날엔, 먹고 자는 것만으로 충분한 하루가 있다. 다음 걸음은 내일 생각해도 된다."* |

**라벨 = 두려움 (C)**:

| tone | 출력 |
|---|---|
| **strong** | *"바람도 지진도 불도 아니었다. 여호와는 가장 조용한 곳에서 두려운 너를 만나셨다. 두려움이 있는 채로도 그분은 가까이 계신다. (왕상 19:12)"* |
| **balanced** | *"바람도 지진도 불도 아니었다. 하나님은 가장 조용한 곳에서 너를 만나셨다. 두려움을 없애야 만나지는 게 아니다."* |
| **soft** | *"큰 것이 지나가고 나서야 들리는 조용한 소리가 있다. 두려운 채로 여기 있어도, 너는 잘못한 것이 아니다."* |

→ 3 라벨 × 3 tone = **9 사전 캐싱** (LLM 캐시 sweet spot). 진입 감정 추가 매칭은 §12 next-steps 에서 확장.

---

## 7. 디바이스별 변형 (capabilities 매핑)

```yaml
mission: elijah
scenes:
  - id: 1
    asset_manifest:
      quest3:     elijah/scene1/quest3/v1.0.0.json     # 12MB (+불 VFX)
      visionpro:  elijah/scene1/visionpro/v1.0.0.json  # 14MB
      galaxyxr:   elijah/scene1/galaxyxr/v1.0.0.json    # 9MB
      web:        elijah/scene1/web/v1.0.0.json         # 4MB (360 + 클릭형 물붓기)
    interactions_required: [grab, pour]
  - id: 2
    asset_manifest:
      quest3:     elijah/scene2/quest3/v1.0.0.json
      visionpro:  elijah/scene2/visionpro/v1.0.0.json
      galaxyxr:   elijah/scene2/galaxyxr/v1.0.0.json
      web:        elijah/scene2/web/v1.0.0.json
    interactions_required: [short_locomotion, sit]
    trigger_warning:
      level: high
      content: ["suicidal_ideation_scripture", "exhaustion", "abandonment"]
      consent_card_id: "elijah_scene2_rotem_warning"
      skip_alternative_scene_id: 3
    capabilities_min:
      r1_listener_required: true       # 음성 입력 켜진 경우 자해 키워드 리스너 필수
  - id: 3
    asset_manifest:
      quest3:     elijah/scene3/quest3/v1.0.0.json      # 8MB
      visionpro:  elijah/scene3/visionpro/v1.0.0.json
      galaxyxr:   elijah/scene3/galaxyxr/v1.0.0.json
      web:        elijah/scene3/web/v1.0.0.json
    interactions_required: [grab, eat, drink]
    capabilities_min:
      haptic_warmth: preferred         # 온기 표현. 없으면 부드러운 시각광으로 대체
  - id: 4
    asset_manifest:
      quest3:     elijah/scene4/quest3/v1.0.0.json      # 14MB
      visionpro:  elijah/scene4/visionpro/v1.0.0.json
      galaxyxr:   elijah/scene4/galaxyxr/v1.0.0.json
      web:        elijah/scene4/web/v1.0.0.json
    interactions_required: [stillness_dwell, face_cover, select_card]
    trigger_warning:
      level: low
      content: ["loud_wind", "quake_rumble"]
      consent_card_id: "elijah_scene4_phenomena_warning"
      skip_alternative_scene_id: 5
  - id: 5
    asset_manifest:
      quest3:     elijah/scene5/quest3/v1.0.0.json
      visionpro:  elijah/scene5/visionpro/v1.0.0.json
      galaxyxr:   elijah/scene5/galaxyxr/v1.0.0.json
      web:        elijah/scene5/web/v1.0.0.json
    interactions_required: [throw_optional, gaze]
```

→ Vision Pro *eye gaze* 있으면 Scene 4 의 *세미한 소리* 를 *시선이 고요를 향할 때* 볼륨 미세 상승으로 업그레이드. Quest 3 *hand tracking* 있으면 Scene 3 떡을 *컨트롤러 없이 진짜 손으로*.
→ Galaxy XR / Web 폴백은 *stillness_dwell* 을 *"가만히 있기" 버튼 유지* 로, *throw* 를 *click* 으로 대체. 의미 약화 알림.

---

## 8. 백엔드 호출 흐름 (Spring + LLM)

```
[Scene 1 시작]
  POST /api/game/elijah/start
    → game_sessions row (character='elijah')
    → 진입 감정(emotion_logs.latest) 조회 → Scene 5 분기 결정
    → users.persisted_state.elijah_last_lament 조회 (이전 미션 토로)

[Scene 1] POST /api/game/elijah/scene/1/decide  body: {"water_pour_count": 3}
[Scene 2] (decide 없음 — 무너짐은 선택 아님) scene_views entered/exited 만. R1 리스너 always.
[Scene 3] POST /api/game/elijah/scene/3/decide  body: {"ate": true, "time_to_first_bite_seconds": 8.1}  # 평가 없음, 진행 기록만
[Scene 4] POST /api/game/elijah/scene/4/decide  body: {"lament_label": "loneliness", "stillness_seconds": 14.2} → LLM(or cache) 하나님 응답 1줄 (3 패턴)
[Scene 5] POST /api/game/elijah/complete         → 회복 문구 fetch (faith_tone × lament_label × 진입 감정 = 캐싱), persisted_state 업데이트, outbox_events(game.completed)
[bridge]  GET /api/track-a/psalm-bridge?session_id=...&psalm=42  → 시편 42편 일기(트랙 A) 연결
```

→ LLM 호출은 *Scene 4(3 패턴) + Scene 5(3 라벨 × 3 tone × 진입감정 N)* 만. 전부 *사전 캐싱 가능* — runtime 호출 0회 목표.
→ 비용: 사용자당 미션 1회 = $0(전부 cache hit). 사전 캐시 빌드 1회 ≈ $0.02 (수십 조합 × $0.001).

---

## 9. 안전 게이트 — 실 동작 예

> *본 절은 임상 위기 개입이 아닌 **storyteller 의 안전 매너**. AI 는 치료자/자살예방 전문가가 아니라 이야기 안내자. 위기 영역은 외부 자원({{crisis_resources.default}})으로 즉시 연결.*

### 9.1 R1 시뮬레이션 — Scene 2 자살 키워드 (최고 우선)

Scene 2 진행 중 사용자 *음성 입력* 에 *"나도 사라지고 싶어" / "다 끝내고 싶어"* 등:
1. `SafetyController.detectSelfHarmRisk` → severity=HIGH
2. *현재 Scene 즉시 일시정지*(fade-to-black)
3. 위기 자원 카드:
   > *"잠시 멈추겠습니다. 지금 많이 힘드시죠.*
   > *혼자 감당하지 않으셔도 돼요. 지금 바로 함께할 곳이 있어요.*
   > *[{{crisis_resources.default}} 자살예방상담 — 24시간·무료] [지금 닫고 메인으로]"*
4. *"메인으로"* 또는 60초 대기 → 미션 종료, `game_sessions.abandoned_at` 기록.
5. `safety_alerts` row(severity=high, source=game, 원문 미저장·해시만, category='suicidal_ideation_semantic').
6. *"많이 지쳤어" 단독* 은 severity=medium → 위기 자원 카드 *동반 노출* 하되 흐름 차단 없이 Scene 3(돌봄) 강조 진입 — *"이 이야기는 지친 사람을 위한 것입니다"* reframing.

### 9.2 R2 시뮬레이션 — 돌봄 우선 순서 보장

Scene 3 은 *어떤 질책·평가·요구도 렌더링하지 않는다*. QA 체크: Scene 3 텍스트 풀에 *"왜", "너 때문에", "믿음이"* 등 책망 어휘가 있으면 빌드 실패(lint 규칙). <!-- lint_forbidden_tokens 인용 -->

### 9.3 R3 시뮬레이션 — 회복 압박 차단

Scene 5 텍스트 풀에 *"다시 이겨라 / 빨리 회복 / 사역으로 돌아가라"* 어휘 감지 시 빌드 실패. 겉옷 던지기 미수행도 *완주* 로 처리(수락 강제 금지). <!-- lint_forbidden_tokens 인용 -->

### 9.4 R4 시뮬레이션 — Scene 2 진입 게이트

§2.2 동의 카드. 옵션:
- *자막만* — 왕상 19:4 낭독 mute, 자막만.
- *약* — 볼륨 -40%.
- *기본* — 정상.
- *건너뛰기* — Scene 3(돌봄) 직행. 무너짐을 보지 않고도 회복 서사 진입 가능.

### 9.5 R5 시뮬레이션 — AI 응답 OFF

`users.allow_ai_response = false`:
- Scene 4 하나님 응답 → *본문 한 줄만*(왕상 19:18 or 19:12).
- Scene 5 회복 문구 → *faith_tone × lament_label* 사전 큐레이션만(진입 감정 분기 제거).
- bridge 시편 → *시편 42:11* *"내 영혼아 네가 어찌하여 낙심하며… 너는 하나님께 소망을 두라"* 고정.

---

## 10. 콘텐츠 검수 체크리스트

| 항목 | 결과 |
|---|---|
| Pre-Scene 0 R4 동의 + 위기 자원({{crisis_resources.default}}) | ☑ |
| Scene 2 진입 시 *추가* R4 동의 + 건너뛰기(→Scene 3) + 강도 토글 | ☑ |
| 자살사고 본문(왕상 19:4) *선택지·목표화 0건* (R1) | ☑ 분기 없음 |
| Scene 2 R1 음성 리스너 always + 위기 카드({{crisis_resources.default}}) | ☑ (구현 게이트) |
| Scene 3 *질책·평가·요구 0건* (R2 lint) | ☑ |
| Scene 5 *회복 압박 어휘 0건* (R3 lint) | ☑ |
| 겉옷 던지기(사명 수락) 강제 아님 (R3) | ☑ |
| Scene 4·5 AI opt-out 시 본문+시편 대체 (R5) | ☑ |
| 바알 선지자 처형(왕상 18:40) 완전 배제 | ☑ |
| 천사·하나님 실사 형상 배제(추상 광휘·소리) | ☑ |
| faith_tone 3단 × 3 라벨 = 9 회복 문구 작성 | ☑ (§6) |
| disputed_points 5개 명시 | ☑ (MVP-ELIJAH §12.6) |
| 영구 footer *"AI 보조 — 본문은 성경 참조 | 위기 시 {{crisis_resources.default}}"* | ☑ |
| 위기 자원 catalog 참조(hardcode 지양, `crisis_resources.default`) | ☑ (109 통일 권장) |
| **엘리야 전용 확정 서지(E1) 확보** | ☐ **미확보 — 드릴다운 필요** |
| **`lemuel-theology-reviewer` 사후 검토** | ☐ **미완 — 필수** |
| **`lemuel-mental-health-safety` 사후 검토 (R1 중점)** | ☐ **미완 — 필수** |

→ 출판 전 **운영자 self-review + R1({{crisis_resources.default}}) 통과 + 두 에이전트 사후 검토** 가 검수 게이트.

---

## 11. 다음 단계

1. 본 콘텐츠를 *Scene yml + AssetManifest JSON* 으로 분해 (content/elijah/scene{1..5}.yml — 본 커밋에 초안 포함).
2. Coqui XTTS-v2 로 4 voice 사전 합성 (`elijah_v1`·`messenger_v1`·`angel_v1`·`whisper_v1`). *세미한 소리* 근접감 튜닝이 핵심.
3. 회복 문구 *9 조합(+진입감정 확장)* 사전 캐싱 — Scene 5 runtime LLM 0회.
4. **엘리야 전용 확정 서지(E1·E2) 드릴다운** — THEOLOGY-REFERENCES.md 에 추가 (구약논집·JBTR *"콜 데마마 다카"* 주해 / 목회상담 번아웃).
5. **`lemuel-theology-reviewer` + `lemuel-mental-health-safety` 사후 검토** — 특히 Scene 2 R1 처리 승인 전 프로덕션 반영 금지.
6. 무동작(stillness) 인터랙션 *실사용자 테스트* — dwell 임계값·과각성 완화 효과 튜닝(5명).
7. 시편 42·43 bridge 연동 (트랙 A) — 번아웃 → 탄식시 연결.

---

*이 문서는 lemuel-xr Phase 2+ 의 엘리야 MVP XR 실 콘텐츠 초안 — 구원 카테고리 5번째 **정서적 소진(번아웃) 회복**. *영적 비상 대비 교육*, *누구나* 대상, *임상/의료 도구 아님*. ouroboros interview 대체 **수기 크리스탈라이즈 seed 기반**이며 `lemuel-theology-reviewer` + `lemuel-mental-health-safety` 사후 검토 필수. `lemuel-xr-theology-tone` 사전 가이드 + R1~R5 반영. AI 역할은 치료자/전문가 아닌 storyteller.*
*핵심 통찰 — 무너진 선지자에게 하나님이 먼저 하신 일은 책망이 아니라 떡과 잠이었다. 회복은 교훈보다 돌봄이 앞선다. (왕상 19:5~7)*
