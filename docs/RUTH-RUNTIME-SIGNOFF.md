# 룻 런타임 노출 사인오프 대장

이 파일은 **기계가 읽는다.** `RuntimeExposureSignoffTest` 가 아래 두 줄을 파싱해서,
`Character` enum 에 `RUTH` 가 들어가는 순간 두 사인오프가 다 있는지 판정한다.

## 왜 이 파일이 생겼나

룻 콘텐츠는 완성돼 있지만 **닫혀 있다.** `ScenarioYamlLoader.loadAll()` 은
`Character.entries` 를 순회하므로, enum 에 `RUTH` 가 없는 한
`backend/src/main/resources/scenarios/ruth.yml` 은 존재하되 로드되지 않는다.
**enum 한 줄이 곧 사용자 노출이고, 그 한 줄이 사인오프 게이트다.**

2026-08-12 까지 이 게이트는 문서의 _문장_ 이었다 —
`docs/MVP-RUTH-CONTENT.md` 머리말과 `scenarios/ruth.yml` 주석에 "사인오프는 아직 없다"
고 적혀 있을 뿐이었다. 문장은 실행되지 않는다. enum 에 `RUTH("ruth")` 한 줄을 넣으면
기존 백엔드 스위트는 **전부 초록으로 통과한다** — `모든 Character 에 시나리오 yml 이
존재하고 로드된다` 는 오히려 파일이 있으니 더 만족스럽게 통과한다. 즉 검사 방향이
반대였다: 있어야 할 것이 없는 경우만 봤고, **있으면 안 될 것이 생긴 경우**는 아무도 안 봤다.

R1(자해 발화) 콘텐츠는 어떤 자동 합의로도 최종 승인할 수 없다
(`docs/CONTENT-EVALUATION-GATES.md` §1 3단). 룻은 5개 Scene 전부에 R1 리스너가 있다.
그래서 이 게이트는 사람 두 명의 이름을 요구한다.

## 사인오프

두 줄의 **형식이 곧 계약** 이다. 체크박스를 `[x]` 로 바꾸고 검토자 이름과 날짜
(YYYY-MM-DD)를 채우면 통과한다. 이름이 비었거나 날짜 형식이 아니면 통과하지 않는다.

- [ ] 신학 검토 사인오프 — 검토자: / 날짜:
- [ ] 정신건강·안전 검토 사인오프 — 검토자: / 날짜:

## 사인오프 전에 검토자가 볼 것

| 무엇               | 어디                                                                |
| ------------------ | ------------------------------------------------------------------- |
| 설계               | `docs/MVP-RUTH.md` (§4-2 배제표 · §4-3 DP1~DP7 미확정 논점)         |
| 런타임 대본        | `docs/MVP-RUTH-CONTENT.md`                                          |
| 고정 문자열 정본   | `docs/RUTH-LOCKED-STRINGS.md` (동의 카드 3장 · 마감 3줄 · 면책 4줄) |
| 성구 자구          | `docs/VERSES-RUTH-GAE.md` (개역개정 실측)                           |
| 실제 로드될 산출물 | `backend/src/main/resources/scenarios/ruth.yml`                     |
| 기계 검사 현황     | `scripts/gates/ruth.yml` · `scripts/check_ruth_captions.py`         |

미해소로 남아 있는 것 (숨기지 않는다):

- `scenarios/ruth.yml` 머리 주석의 배제 개수 표기는 어느 검사기의 사정권에도 없다
  (`.yml` 이라 `code_claims_check.py` 밖, AC 31 은 다른 두 파일만 대조). 사람이 읽어야 한다.
- `gates:ruth` 의 G0e 는 의도적으로 FAIL 상태다. 초록으로 만들지 말 것.
- DP1~DP7 은 확정하지 않은 논점이다. 사인오프가 이것들을 확정한다는 뜻이 아니다.
- ✅ **2026-08-20 — 런타임이 룻의 동의 카드 설계를 이제 표현한다.** (이 항목은 해소됐다.
  경위를 남긴다.) 2026-08-20 까지 백엔드에는 skip 분기가 **없었다** — 결정을 기록한 뒤
  무조건 `next` 를 따라갔다. Scene 1 의 `ruth_entry_consent` 는 `covers_scenes: [1, 2]`
  라서 스킵 목적지가 **Scene 3** 인데 그 씬의 `next` 는 2다. 그래서 그 상태로 enum 을
  열었다면 **사별 서사를 건너뛰겠다고 고른 사용자가 Scene 2 — 그 카드가 덮기로 한 바로
  그 씬 — 으로 들어갔다.** 다른 인물들은 `skip == next` 라 우연히 맞아떨어졌을 뿐이다.
  이제 `SceneSkipResolver` 가 세 목적지를 집행한다 —
  ① 정수 `skip_alternative_scene_id` → 그 Scene 으로 점프(룻 1→3, 4→5),
  ② 문자열 → 마지막 Scene 의 축약 블록(`ruth_scene5_alt_short`, `captions` 가 실제로 비워짐),
  ③ `declined_route: closing` → 종결. **스킵과 거절은 다른 동작이다**(Scene 3 주석의 요구).
  카드가 여러 씬을 덮으면(중간 카드 = Scene 3·5) 세션에 남은 결정을 근거로 **선택을 뒤
  씬까지 들고 간다** — 목적지 4를 지나 `next` 로 5에 도착해도 축약 경로가 유지된다.
  목적지가 실재하지 않으면 `next` 로 흘려보내지 않고 던진다(조용한 실패의 대가가 정확히
  이 통제가 막으려던 노출이다). `ScenarioYamlLoaderTest` 의 불변식도 `skip == next` 에서
  **"스킵 목적지가 그 카드가 덮은 씬이면 안 된다"** 로 바뀌었다.
  Scene 3 블록에 `skip_alternative_scene_id: 4` 가 **두 번** 적혀 있던 YAML 중복 키도
  한 줄로 정리했다(두 값이 같아 의미 변화는 없다).
  ⚠️ 이것은 _엔진_ 이 저작 의도를 지킨다는 뜻이지 콘텐츠가 승인됐다는 뜻이 아니다.
  enum 은 여전히 닫혀 있고, 아래 두 사인오프가 이 파일의 게이트다.
- 🚧 **2026-08-19 — 자산 층은 먼저 들어왔다. 이 게이트 밖이다.**
  `backend/src/main/resources/manifests/ruth/` 에 VR 20장 + AR 15장이 시드됐고
  `lemuel.xr.ar-enabled-missions` 에 `ruth` 가 들어갔다. 시나리오 노출은 그대로 닫혀
  있지만(위 enum 한 줄), **manifest 조회에는 이 사인오프 게이트가 걸려 있지 않다** —
  `AssetManifestController` 는 미션 id 를 문자열로 받고 `Character` 를 보지 않으므로,
  `/api/config/asset-manifest?mission=ruth&device=quest3&scene=1` 은 지금 200 을 준다.
  나가는 것은 자산 목록(모델·오디오·텍스처 id 와 CDN 경로, 낭독 트랙의 `note` 로 적힌
  안전 계약)이고 자막·동의 카드 **문안은 들어 있지 않다.** 그래도 「룻은 닫혀 있다」의
  범위가 좁아졌으므로 검토자가 이 사실을 알고 사인오프해야 한다.
  자산 설계가 지킨 것: 아기·출산 자산 0(Scene 5 caps 에 `baby_asset_present: false` ·
  `birth_scene_rendered: false` 로 명시) · 햅틱 0 · 시야 차단 0 · 인물은 전신 원경
  프레이밍만 · Scene 4 는 조명만으로 밤을 만들고 최소 오디오 거리 2m 를 caps 에 박았다.
  ⚠️ AR 은 이 미션에서 새로운 축을 하나 연다 — 패스스루는 씬을 **사용자의 실제 방**에
  놓는다. Scene 4(등급 C · 밤 · 권력 비대칭)를 사용자의 방에 놓는 것이 VR 과 같은
  노출인지는 `staging_constraints` 다섯 항이 답하지 않는다(그 다섯 항은 VR 을 전제로
  쓰였다). 정신건강·안전 검토자가 이 씬의 AR 을 별도로 판단해야 한다.
- 위 항목들은 2026-08-12 에 기계로 확인한 것이다(자산 층 항목은 2026-08-19).
  그 밖에 사람이 봐야 할 것은 여전히 사람이 봐야 한다.

## 이 대장이 보증하지 않는 것

체크박스는 **사람이 봤다** 는 기록이지 콘텐츠가 안전하다는 증명이 아니다.
이 테스트가 재는 것은 "두 사인오프 없이 노출이 열리지 않았는가" 하나뿐이다.
