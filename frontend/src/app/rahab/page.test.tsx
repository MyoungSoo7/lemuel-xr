import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
// 번호 리터럴은 `@/lib/crisis-resources` 에만 산다 (`scripts/check_frontend_hotline.py`).
// 픽스처는 백엔드 `CrisisTokenResolver` 가 `{{crisis_resources.default}}` 를 치환해
// 내려보낸 결과를 흉내 내되, 번호를 여기 적지 않고 정본에서 파생시킨다.
import { CRISIS_DEFAULT } from "@/lib/crisis-resources";
import { cardDoors } from "@/components/TriggerWarningGate";

/*
  라합 미션 화면 테스트.

  ⚠️ **이 화면은 아직 열려 있지 않다.** `Character` enum 에 `RAHAB` 이 없어서 홈에도
  카드가 없고 `/api/game/rahab/start` 도 열리지 않는다. 이 파일이 초록인 것을 「열렸다」로
  읽지 않는다 — 여는 조건은 `docs/RAHAB-RUNTIME-SIGNOFF.md` 의 결정 줄이다.

  이 인물 고유의 위험은 **통제가 하나뿐이라는 것** 에서 나온다. rev.15 D1 로 라합은
  미션이 아니라 낭독 트랙이 됐고(`interactions: []` · `branches: []`), 사용자가 하는 일은
  속도·동의/거절·이탈 셋뿐이다. 갈래도 마감 문구 표도 없으니 **동의 카드가 이 미션의
  유일한 안전 장치** 다. 그래서 여기서 재는 것은 전부 한 문장으로 모인다 —
  **카드가 없애 주겠다고 한 것이 실제로 화면에서 빠지는가.**

  1) **카드가 다섯이고 모양이 세 가지다.** Scene 1·3·4·5 는 목적지가 문자열(같은 씬의
     축약 블록)이라 씬 번호가 안 바뀐다 — `conditionalBlockId` 가 박혔는지로만 「이미
     건너뛴 상태」를 안다. 이 한 줄이 없으면 같은 카드가 영원히 다시 뜬다. Scene 2 만
     목적지가 정수 3(씬 전체 건너뛰기)이고, Scene 5 는 `covered_by_scene: 1` 이라
     Scene 1 에서 이미 거절한 사람에겐 카드가 다시 뜨지 않는다.

  2) **Scene 4 는 자막만 빼서는 카드가 한 약속을 못 지킨다.** 둘째 잠긴 문구가
     「창문과 줄은 화면에 띄우지 않는다」를 약속하는데 창과 줄은 배경에 있다. 축약
     블록이 `background_variant` 를 함께 갈아 끼우고 화면은 그 **값** 으로 그림을
     고른다. 값이 바뀌었는데 그림이 그대로면 카드가 한 약속이 화면에서 거짓이 된다.

  3) **「본문이 아닙니다」 표지가 유일한 구별 수단이다.** 성경 자구와 안내 문장이 같은
     글꼴로 흐르면 사용자는 둘을 구별할 방법이 없다. 다리 문장도 첫 줄이 그 표지다.

  4) **낭독 트랙이라는 것 자체가 불변식이다.** payload 에 `options` 가 실려 와도 화면에
     선택 버튼이 생기면 안 된다 — 그 순간 본문이 라합에게 돌린 행위를 사용자가
     가로채는 형태가 열린다.
*/

vi.mock("@/lib/api/game", () => ({
  startMission: vi.fn(),
  decideMission: vi.fn(),
  completeMission: vi.fn(),
}));

import { startMission, decideMission, completeMission } from "@/lib/api/game";
import RahabPage from "./page";

const SESSION = "sess-rahab";

/*
  동의 카드 정본 5장 — backend/src/main/resources/scenarios/rahab.yml 의
  `consent_card_ko` 를 그대로 옮긴 것. 마지막 줄의 위기 안내만 서버가 치환한 뒤 모양으로
  둔다. 문 이름은 이 파일 어디에도 *따로* 적지 않는다 — 기대값도 이 상수에서 뽑는다.
*/
const CARD_S1 = `이 이야기에는 한 여인이 나옵니다. 성경 본문은 그를 '기생'이라고 부릅니다.
그 호칭은 인용되는 자막에 다섯 번 나옵니다.
· 이 미션은 그 호칭을 평가하지 않습니다. 좋다고도 나쁘다고도 말하지 않습니다.
· 그 여인이 겪은 일을 재연하는 장면은 없습니다.
· 건너뛰시면 호칭이 든 자막이 나오지 않습니다. 마지막 장면에서 그가 어떤
  사람들 가운데 살게 되었는지를 말하는 줄도 함께 빠집니다.
· 그 두 자리에는 성경 본문이 아닌 안내 문장이 대신 뜹니다. 「본문이 아닙니다」
  표지가 함께 붙습니다.
· 전체 7분입니다. 어느 지점에서든 멈추거나 나갈 수 있습니다.
· 지금 보지 않으셔도 이야기는 온전히 끝맺어집니다.
계속하시겠어요?
[계속한다]  [호칭이 든 자막은 건너뛴다]
음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]
이 앱은 당신을 이 호칭으로 규정하지 않습니다. 성경 본문에 있는 낱말이라 인용할
뿐입니다. 지금 힘들다면 ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

const CARD_S2 = `다음 장면에는 권력을 가진 사람이 한 여인의 집으로 사람을 보내, 집 안에 있는
이들을 끌어내라고 요구하는 대목이 나옵니다.
· 폭력이 일어나는 장면은 없습니다. 요구하는 말과, 뒤이어 성문이 닫히는 데까지입니다.
· 건너뛰시면 이 장면 전체가 나오지 않고 다음 장면으로 넘어갑니다.
· 다음 장면 첫머리에는 성경 본문이 아닌 안내 문장이 한 줄 뜹니다. 「본문이
  아닙니다」 표지가 함께 붙습니다.
· 어느 지점에서든 멈추거나 나갈 수 있습니다.
계속하시겠어요?
[계속한다]  [이 장면은 건너뛴다]
음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]
당신에게 일어난 일은 당신의 잘못이 아닙니다. 지금 힘들다면 ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

const CARD_S3 = `다음 장면에는 한 사람이 자기 부모와 형제를 살려 달라고 청하는 대목이 나옵니다.
상대는 자기들의 목숨으로 대신하겠다고 답합니다.
· 누가 죽는 장면은 없습니다. 말로 오가는 약속입니다.
· 건너뛰시면 청하는 줄과 답하는 줄이 나오지 않습니다. 장면은 이어집니다.
· 어느 지점에서든 멈추거나 나갈 수 있습니다.
계속하시겠어요?
[계속한다]  [약속하는 두 줄은 건너뛴다]
음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]
이 장면에는 목숨을 걸고 맺는 약속이 나옵니다. 침묵을 조건으로 안전을 얻어 본 적이
있다면 건너뛰셔도 됩니다. ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

/*
  ⚠️ Scene 4 만 카드가 **두 장을 이어 붙인 것** 이라 대괄호 줄이 둘이다. `cardDoors()` 는
  첫 줄만 문 이름으로 쓰고 나머지 대괄호 줄은 본문에서 지운다 — 그래서 화면의 문은
  하나인데 그 문이 약속 둘(계약 문구를 빼기 + 창문과 줄을 안 띄우기)을 지고 있다.
  저작과 갈라진 자리이고 대장(`docs/RAHAB-RUNTIME-SIGNOFF.md`)에 적혀 있다. 이 픽스처를
  한 장으로 줄이면 그 갈라짐이 테스트에서 사라진다.
*/
const CARD_S4 = `다음 장면에는 고대의 계약 문구가 나옵니다. 집 밖으로 나가면 그 피가 그의 머리로
돌아간다는 말입니다.
· 누구에게 책임이 있는지를 정하던 옛 표현이며, 이 미션은 그 말을 오늘의 당신에게
  적용하지 않습니다.
· 건너뛰시면 그 두 줄이 나오지 않습니다. 같은 장면의 창문과 줄도 화면에 띄우지
  않습니다.
· 어느 지점에서든 멈추거나 나갈 수 있습니다.
계속하시겠어요?
[계속한다]  [계약 문구 두 줄은 건너뛴다]
음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]
이 장면의 자막은 고대의 계약 문구입니다. 누가 다치든 그것이 당신 책임이라는 뜻이
아닙니다. 지금 안전하지 않은 곳에 있다면 ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}

다음 장면에는 사람이 창문에서 줄에 매달려 성벽 아래로 내려가는 대목이 나옵니다.
· 내려가는 동작은 어느 쪽을 고르셔도 화면에 나오지 않습니다.
· 건너뛰시면 창문과 줄이 화면에 나오지 않습니다. 같은 장면의 계약 문구 두 줄도
  함께 빠집니다.
· 어느 지점에서든 멈추거나 나갈 수 있습니다.
계속하시겠어요?
[계속한다]  [창문과 줄은 화면에 띄우지 않는다]
음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]
이 장면에는 줄을 타고 성벽을 내려가는 대목이 나옵니다. 자막은 성경 본문 그대로지만,
창문과 줄은 화면에 띄우지 않도록 고르실 수 있습니다. 지금 힘들다면
${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

const CARD_S5 = `마지막 장면에는 한 사람이 아이를 낳았다는 계보 문장이 한 줄 나옵니다.
· 출산 장면은 나오지 않습니다. 이름을 잇는 한 줄입니다.
· 이 미션은 아이가 태어난 것을 보상이나 회복의 증거로 말하지 않습니다.
· 건너뛰시면 그 한 줄이 나오지 않습니다. 이 장면에서 호칭이 든 줄들도 함께
  빠집니다. 이야기는 그대로 끝맺어집니다.
· 그 자리에는 성경 본문이 아닌 안내 문장이 대신 뜹니다. 「본문이 아닙니다」
  표지가 함께 붙습니다.
· 어느 지점에서든 멈추거나 나갈 수 있습니다.
계속하시겠어요?
[계속한다]  [계보 문장은 건너뛴다]
음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]
이 장면은 출산을 언급합니다. 이 미션은 출산을 보상이나 회복의 증거로 말하지
않습니다. 지금 힘들다면 ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

/** 다섯 카드에서 문 이름을 뽑는다 — 기대값도 정본에서 파생시키기 위한 것. */
const DOORS_S1 = cardDoors(CARD_S1)!;
const DOORS_S2 = cardDoors(CARD_S2)!;
const DOORS_S3 = cardDoors(CARD_S3)!;
const DOORS_S4 = cardDoors(CARD_S4)!;
const DOORS_S5 = cardDoors(CARD_S5)!;

/*
  건너뛴 사람에게 저작이 남겨 둔 다리. 두 줄 다 첫 줄이 「본문이 아닙니다」 표지다 —
  이 줄이 표지 없이 흐르면 사용자는 안내 문장을 성경 자구로 읽는다.
*/
const BRIDGE_S1 = `본문이 아닙니다
라합이 여기서 말을 건네는 상대는 성 밖에서 온 두 사람입니다.`;
const BRIDGE_S2 = `본문이 아닙니다
한 장면을 건너뛰었습니다. 라합이 여기서 말을 건네는 상대는 성 밖에서 온 두 사람입니다.`;

/*
  두 다리 문장은 **줄바꿈을 품고 있다.** testing-library 의 문자열 매처는 노드 쪽 텍스트만
  정규화하고 기대 문자열은 적은 그대로 비교하므로, 여러 줄 문자열을 그냥 넘기면 화면에
  멀쩡히 떠 있어도 영영 안 잡힌다. 여기서 줄바꿈을 지우고 한 줄로 적으면 통과는 하지만
  **첫 줄이 표지라는 것** 이 기대값에서 사라진다 — 표지를 지운 회귀가 초록으로 지나간다.
  그래서 줄바꿈을 그대로 둔 채 요소의 텍스트와 축자 비교한다(자식이 같은 텍스트를 가진
  조상 요소는 뺀다 — 안 그러면 감싼 section 까지 걸려 「여럿 찾음」이 된다).
*/
function multiline(text: string) {
  return (_content: string, el: Element | null) =>
    el?.textContent === text &&
    !Array.from(el.children).some((c) => c.textContent === text);
}

/** 다섯 씬 전부에 같은 문구로 실려 있다. 서버가 번호를 치환한 뒤의 모양. */
const CRISIS_LINE = `지금 힘드시면: ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

/*
  자막 정본. 축약 블록이 실제로 어떤 줄을 빼는지가 이 파일의 핵심이라, 빠지는 줄과
  남는 줄을 둘 다 상수로 둔다. 자구 자체의 정합은 백엔드
  `RahabWithheldNarrativeTest` 가 저작 정본과 축자 대조한다 — 여기서 재는 것은
  「화면에 떴는가 / 안 떴는가」다.
*/
const C_2_1 =
  "눈의 아들 여호수아가 싯딤에서 두 사람을 정탐꾼으로 보내며 이르되 가서 그 땅과 여리고를 엿보라 하매 그들이 가서 라합이라 하는 기생의 집에 들어가";
const C_2_11 =
  "우리가 듣자 곧 마음이 녹았고 너희로 말미암아 사람이 정신을 잃었나니 너희의 하나님 여호와는 위로는 하늘에서도 아래로는 땅에서도 하나님이시니라";
const C_2_3 =
  "네게로 와서 네 집에 들어간 그 사람들을 끌어내라 그들은 이 온 땅을 정탐하러 왔느니라";
const C_2_8 = "또 그들이 눕기 전에 라합이 지붕에 올라가서";
const C_2_13 =
  "그리고 나의 부모와 나의 남녀 형제와 그들에게 속한 모든 사람을 살려 주어 우리 목숨을 죽음에서 건져내라";
const C_2_15 =
  "라합이 그들을 창문에서 줄로 달아 내리니 그의 집이 성벽 위에 있으므로 그가 성벽 위에 거주하였음이라";
const C_2_19 =
  "누구든지 네 집 문을 나가서 거리로 가면 그의 피가 그의 머리로 돌아갈 것이요 우리는 허물이 없으리라 그러나 누구든지 너와 함께 집에 있는 자에게 손을 대면 그의 피는 우리의 머리로 돌아오려니와";
const C_6_23 =
  "정탐한 젊은이들이 들어가서 라합과 그의 부모와 그의 형제와 그에게 속한 모든 것을 이끌어 내고 또 그의 친족도 다 이끌어 내어 그들을 이스라엘의 진영 밖에 두고";
const C_MT_1_5 = "살몬은 라합에게서 보아스를 낳고";
const C_6_27 =
  "여호와께서 여호수아와 함께 하시니 여호수아의 소문이 그 온 땅에 퍼지니라";

/** 계보 문장을 건너뛴 사람의 자리에 대신 들어가는 안내 문장. 성경 자구가 아니다. */
const NOT_SCRIPTURE =
  "지금 보신 자막에는 라합이 그 뒤에 어디에서 누구와 함께 살게 되었는지를 말하는 줄들이 없습니다. 그 줄들이 빠진 것은 이 미션이 보여 드리는 범위 때문이지, 이야기가 거기서 끝나서가 아닙니다. 이 미션은 방금 보신 줄을 라합의 마지막 자리로 말하지 않습니다.";

const CLOSING_LINE = "여호와께서 여호수아와 함께 하시니";
/** 범위를 못 박는 문장. 위기 자원 토큰이 이 문장 *안* 에 있다 — 잘라 내면 마감에서 안내가 사라진다. */
const CLOSING_FOOTER = `이 미션은 라합이 겪은 일이 어떤 목적을 위해 주어졌다고 말하지 않습니다. 지금 힘들다면 ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

function scene(currentScene: number, scenePayload: Record<string, unknown>) {
  return {
    sessionId: SESSION,
    userId: "guest-1",
    currentScene,
    scenePayload,
    responseText: null,
  };
}

/*
  payload 는 두 겹이다 — `ScenePayloadAssembler.build` 가 표준 필드를 세팅한 뒤
  `putAll(sc.extras)` 를 하므로 yml 의 `extras:` 블록이 `payload.extras` 로 한 겹 더
  들어가고, 그 밖의 씬 키(trigger_warning·value_prompt·scriptureRef)는 루트에 온다.
  픽스처를 평평하게 적으면 화면이 두 겹을 잘못 읽어도 초록이 된다.
*/
const SCENE1 = scene(1, {
  title: "우리가 듣자 곧",
  type: "cinematic",
  scriptureRef: "jos-2:11",
  exposure_grade: "C",
  trigger_warning: {
    level: "mid",
    content: ["sex_work_stigma", "collective_terror_response"],
    consent_card_id: "rahab_stigma",
    // 이 카드가 Scene 5 까지 덮는다 — 같은 낙인 호칭을 두 번 묻지 않는다.
    covers_scenes: [1, 5],
    skip_alternative_scene_id: "s1_no_epithet",
    consent_card_ko: CARD_S1,
    skip_bridge_narration_ko: BRIDGE_S1,
  },
  extras: {
    anchor: "여리고 성벽 위의 집. 실내. 문은 닫혀 있다. 저녁.",
    additional_refs: ["jos-2:1", "jos-2:9"],
    crisis_reminder: CRISIS_LINE,
    captions: [
      { verse_ref: "수 2:1", text_ko: C_2_1 },
      { speaker_ko: "라합", verse_ref: "수 2:9", text_ko: "수 2:9 자막" },
      { speaker_ko: "라합", verse_ref: "수 2:11", text_ko: C_2_11 },
    ],
    npc_note: "정탐꾼 둘은 얼굴을 렌더하지 않는다.",
    interactions: [],
    branches: [],
  },
});

/** 호칭이 든 자막을 뺀 판. 씬 번호가 그대로이고 `conditionalBlockId` 만 박힌다. */
const SCENE1_ALT = scene(1, {
  ...SCENE1.scenePayload,
  conditionalBlockId: "s1_no_epithet",
  extras: {
    ...(SCENE1.scenePayload.extras as Record<string, unknown>),
    captions: [
      { speaker_ko: "라합", verse_ref: "수 2:9", text_ko: "수 2:9 자막" },
      { speaker_ko: "라합", verse_ref: "수 2:11", text_ko: C_2_11 },
    ],
  },
});

const SCENE2 = scene(2, {
  title: "전갈이 왔다",
  type: "cinematic",
  scriptureRef: "jos-2:3",
  trigger_warning: {
    level: "mid",
    content: ["sexual_vulnerability_context", "home_intrusion_search"],
    consent_card_id: "rahab_coercion",
    covers_scenes: [2],
    // 다섯 중 유일한 **정수** 목적지 — 씬 전체를 건너뛴다.
    skip_alternative_scene_id: 3,
    consent_card_ko: CARD_S2,
    skip_bridge_narration_ko: BRIDGE_S2,
  },
  extras: {
    anchor: "같은 실내. 문은 닫혀 있고 프레임 밖에 있다. 밤.",
    crisis_reminder: CRISIS_LINE,
    captions: [
      { speaker_ko: "여리고 왕", verse_ref: "수 2:3", text_ko: C_2_3 },
    ],
    interactions: [],
    branches: [],
  },
});

const SCENE3 = scene(3, {
  title: "지붕에서 한 말",
  type: "cinematic",
  scriptureRef: "jos-2:8",
  trigger_warning: {
    level: "low_mid",
    content: ["family_annihilation_risk", "conditional_survival_pact"],
    consent_card_id: "rahab_family_risk",
    covers_scenes: [3],
    skip_alternative_scene_id: "s3_omit_2_13_2_14",
    consent_card_ko: CARD_S3,
    // 다리 문장이 없다 — 같은 씬 안에서 두 줄만 빠지므로 이을 것이 없다.
  },
  extras: {
    anchor: "집 지붕. 밤. 삼대는 프레임 밖에 있다.",
    crisis_reminder: CRISIS_LINE,
    captions: [
      { verse_ref: "수 2:8", text_ko: C_2_8 },
      { speaker_ko: "라합", verse_ref: "수 2:12", text_ko: "수 2:12 자막" },
      { speaker_ko: "라합", verse_ref: "수 2:13", text_ko: C_2_13 },
    ],
    /*
      D1 이후 이 인물에는 갈래가 없다. 그런데도 payload 에 `options` 를 실어 두는 이유는
      **화면이 그것을 읽지 않는다는 것** 을 재기 위해서다 — 앞 인물 화면을 베껴 오다
      선택 버튼이 딸려 들어오면 여기서 잡힌다.
    */
    options: [{ id: "should_not_render", label_ko: "숨는 것을 돕는다" }],
    interactions: [],
    branches: [],
  },
});

const SCENE4 = scene(4, {
  title: "창문과 붉은 줄",
  type: "cinematic",
  scriptureRef: "jos-2:15",
  trigger_warning: {
    level: "mid",
    content: [
      "blood_guilt_attribution",
      "height_suspension",
      "family_annihilation_risk",
    ],
    consent_card_id: "rahab_blood_guilt",
    covers_scenes: [4],
    skip_alternative_scene_id: "s4_declined",
    consent_card_ko: CARD_S4,
  },
  extras: {
    anchor: "창문 안쪽. 성벽 위의 집. 밤.",
    crisis_reminder: CRISIS_LINE,
    background_variant: "window_and_cord",
    captions: [
      { verse_ref: "수 2:15", text_ko: C_2_15 },
      { speaker_ko: "정탐꾼", verse_ref: "수 2:19", text_ko: C_2_19 },
    ],
    interactions: [],
    branches: [],
  },
});

/** 계약 문구를 뺀 판 — 자막과 **배경** 을 함께 갈아 끼운다. */
const SCENE4_ALT = scene(4, {
  ...SCENE4.scenePayload,
  conditionalBlockId: "s4_declined",
  extras: {
    ...(SCENE4.scenePayload.extras as Record<string, unknown>),
    background_variant: "no_window_no_cord",
    captions: [{ verse_ref: "수 2:15", text_ko: C_2_15 }],
  },
});

const SCENE5 = scene(5, {
  title: "진영 밖에서 이스라엘 중으로",
  type: "outro",
  scriptureRef: "jos-6:23",
  value_prompt:
    "오늘 한 가지만 — 남들이 나를 부르는 이름 말고, 내가 오늘 들은 것을 한 줄로 적어 보는 것. 그 줄이 누구에게도 가지 않아도 됩니다.",
  trigger_warning: {
    level: "low_mid",
    content: ["sex_work_stigma", "ethnic_labeling", "pregnancy_childbirth"],
    consent_card_id: "rahab_lineage_birth",
    covers_scenes: [5],
    // Scene 1 에서 이미 거절한 사람에겐 서버가 도착 시점에 축약 블록을 얹어 내려준다.
    covered_by_scene: 1,
    skip_alternative_scene_id: "s5_close_on_6_23_6_27",
    consent_card_ko: CARD_S5,
  },
  extras: {
    anchor: "이스라엘의 진영 밖. 정지 시점. 낮.",
    crisis_reminder: CRISIS_LINE,
    captions: [
      { verse_ref: "수 6:23", text_ko: C_6_23 },
      { verse_ref: "마 1:5", text_ko: C_MT_1_5 },
      { verse_ref: "수 6:27", text_ko: C_6_27 },
    ],
    closing_screen: {
      presentation_mode: "still_frame",
      environment_disabled: true,
      npc_disabled: true,
      mission_title_ko: "먼저 있었던 일",
      closing_line_ko: CLOSING_LINE,
      closing_footer_ko: CLOSING_FOOTER,
    },
    dwell: {
      min_seconds: 3,
      on_timeout: "auto_proceed_after_seconds_15",
      note_ko: "머무는 시간에 정답이 없습니다. 먼저 나가셔도 됩니다.",
    },
    interactions: [],
    branches: [],
  },
});

/*
  계보 문장을 건너뛴 사람이 받는 것. 블록이 덮는 것은 `captions` 뿐이고
  `closing_screen` 은 base 의 것이 그대로 남는다(`renders:` 는 base 에 있어야 하는 키의
  선언이지 블록이 들고 있는 값이 아니다). 그래서 **두 경로의 마감 화면이 같아야 한다.**
*/
const SCENE5_ALT = scene(5, {
  ...SCENE5.scenePayload,
  conditionalBlockId: "s5_close_on_6_23_6_27",
  extras: {
    ...(SCENE5.scenePayload.extras as Record<string, unknown>),
    captions: [
      { verse_ref: "수 6:23", text_ko: C_6_23 },
      { speaker_ko: "본문이 아닙니다", text_ko: NOT_SCRIPTURE },
      { verse_ref: "수 6:27", text_ko: C_6_27 },
    ],
  },
});

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <RahabPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(startMission).mockReset();
  vi.mocked(decideMission).mockReset();
  vi.mocked(completeMission).mockReset();
  vi.mocked(completeMission).mockResolvedValue(undefined);
});

describe("라합 미션 — 씬 상태 기계", () => {
  it("다섯 씬을 동의하며 끝까지 걸어간다 — 고를 것은 속도뿐이다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission)
      .mockResolvedValueOnce(SCENE2)
      .mockResolvedValueOnce(SCENE3)
      .mockResolvedValueOnce(SCENE4)
      .mockResolvedValueOnce(SCENE5);
    renderPage();

    // Scene 1 — 동의 전에는 본문이 한 줄도 뜨지 않는다.
    expect(
      await screen.findByRole("button", { name: DOORS_S1.continueLabel }),
    ).toBeInTheDocument();
    expect(screen.queryByText(C_2_1)).toBeNull();

    await user.click(
      screen.getByRole("button", { name: DOORS_S1.continueLabel }),
    );
    expect(await screen.findByText(C_2_1)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(1, "rahab", SESSION, 1, {
      value: "next",
    });

    // Scene 2~4 — 씬마다 카드가 **다시** 뜬다. 앞 씬의 동의를 상속하지 않는다.
    for (const [i, doors, caption] of [
      [2, DOORS_S2, C_2_3],
      [3, DOORS_S3, C_2_13],
      [4, DOORS_S4, C_2_19],
    ] as const) {
      expect(
        await screen.findByRole("button", { name: doors.continueLabel }),
      ).toBeInTheDocument();
      expect(screen.queryByText(caption)).toBeNull();
      await user.click(
        screen.getByRole("button", { name: doors.continueLabel }),
      );
      expect(await screen.findByText(caption)).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: "계속 →" }));
      expect(decideMission).toHaveBeenNthCalledWith(i, "rahab", SESSION, i, {
        value: "next",
      });
    }

    // Scene 5 — outro. 카드를 지나면 마감 화면이 뜬다.
    await user.click(
      await screen.findByRole("button", { name: DOORS_S5.continueLabel }),
    );
    expect(await screen.findByText(C_MT_1_5)).toBeInTheDocument();
    expect(screen.getByText(CLOSING_LINE)).toBeInTheDocument();
    expect(screen.getByText(CLOSING_FOOTER)).toBeInTheDocument();
    expect(
      screen.getByText(/누구에게도 가지 않아도 됩니다/),
    ).toBeInTheDocument();
    expect(
      screen.getByText("머무는 시간에 정답이 없습니다. 먼저 나가셔도 됩니다."),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "미션 완료" }));
    expect(completeMission).toHaveBeenCalledWith("rahab", SESSION, "completed");
  });

  it("시작이 실패하면 부팅 화면에서 다시 시도할 수 있다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission)
      .mockRejectedValueOnce(new Error("boom"))
      .mockResolvedValueOnce(SCENE1);
    renderPage();

    await screen.findByRole("button", { name: /다시 시도/ });
    await user.click(screen.getByRole("button", { name: /다시 시도/ }));
    expect(
      await screen.findByRole("button", { name: DOORS_S1.continueLabel }),
    ).toBeInTheDocument();
  });
});

describe("라합 미션 — 카드가 유일한 통제다", () => {
  it("같은 씬으로 돌아오는 거절은 카드를 다시 띄우지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission).mockResolvedValue(SCENE1_ALT);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S1.skipLabel }),
    );
    expect(decideMission).toHaveBeenCalledWith("rahab", SESSION, 1, {
      value: "skip",
    });

    /*
      목적지가 문자열이라 씬 번호가 1 그대로 돌아온다. `conditionalBlockId` 를 읽지
      않으면 화면은 「아직 동의 안 한 Scene 1」로 보고 같은 카드를 영원히 다시 띄운다 —
      거절한 사람이 갇힌다.
    */
    expect(await screen.findByText(C_2_11)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: DOORS_S1.skipLabel }),
    ).toBeNull();
    // 카드가 없애 주겠다고 한 그 줄이 실제로 빠져 있다.
    expect(screen.queryByText(C_2_1)).toBeNull();
    // 표지가 붙은 다리 한 줄이 그 자리를 잇는다.
    expect(screen.getByText(multiline(BRIDGE_S1))).toBeInTheDocument();
  });

  it("Scene 2 를 건너뛴 사람은 Scene 3 으로 넘어가고 다리 한 줄을 받는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    vi.mocked(decideMission).mockResolvedValue(SCENE3);
    renderPage();

    // 다섯 중 이 카드만 목적지가 정수다 — 카드에 적힌 목적지와 서버가 주는 씬이 같아야 한다.
    expect(
      await screen.findByText(/건너뛰면 Scene 3 으로 이어집니다/),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: DOORS_S2.skipLabel }));

    expect(await screen.findByText(multiline(BRIDGE_S2))).toBeInTheDocument();
    // 건너뛴 사람도 Scene 3 의 카드는 자기 몫으로 받는다.
    expect(
      screen.getByRole("button", { name: DOORS_S3.continueLabel }),
    ).toBeInTheDocument();
  });

  it("다리는 다음 진행에서 지워진다 — 이야기 위에 겹쳐 읽히지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    vi.mocked(decideMission)
      .mockResolvedValueOnce(SCENE3)
      .mockResolvedValueOnce(SCENE4);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S2.skipLabel }),
    );
    await screen.findByText(multiline(BRIDGE_S2));
    await user.click(
      screen.getByRole("button", { name: DOORS_S3.continueLabel }),
    );
    await user.click(screen.getByRole("button", { name: "계속 →" }));

    expect(
      await screen.findByRole("button", { name: DOORS_S4.continueLabel }),
    ).toBeInTheDocument();
    expect(screen.queryByText(multiline(BRIDGE_S2))).toBeNull();
  });

  it("같은 씬으로 돌아오는 네 카드는 목적지 번호를 말하지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    renderPage();

    /*
      `skip_alternative_scene_id` 가 문자열(같은 씬의 블록 id)이면 씬 번호로 정규화되지
      않는다. 여기서 블록 id 를 씬 번호처럼 적어 버리면 카드가 「Scene s1_no_epithet
      으로 이어집니다」라고 말하게 된다.
    */
    expect(
      await screen.findByText(/건너뛰어도 이야기는 이어집니다/),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Scene s1_no_epithet/)).toBeNull();
  });

  it("Scene 1 에서 거절한 사람은 Scene 5 카드를 다시 보지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    /*
      `covered_by_scene: 1` — 같은 낙인 호칭을 두 번 묻지 않는다는 뜻이다. 서버가 도착
      시점에 축약 블록을 얹어 내려주므로 카드가 없어야 한다. 여기서 카드가 다시 뜨면
      Scene 1 카드가 한 약속(「마지막 장면의 그 줄도 함께 빠집니다」)이 깨진다.
    */
    expect(await screen.findByText(C_6_23)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: DOORS_S5.continueLabel }),
    ).toBeNull();
    expect(screen.queryByText(C_MT_1_5)).toBeNull();

    // 대체 문장에는 「본문이 아닙니다」 표지가 붙는다 — 자구와 안내를 가르는 유일한 표시.
    expect(screen.getByText(NOT_SCRIPTURE)).toBeInTheDocument();
    expect(screen.getByText("본문이 아닙니다")).toBeInTheDocument();
  });

  it("건너뛴 사람도 같은 마감 화면을 받는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    // 블록은 자막만 덮는다. 끝을 본 사람만 마감을 받으면 그게 곧 거절의 대가가 된다.
    expect(await screen.findByText(CLOSING_LINE)).toBeInTheDocument();
    expect(screen.getByText(CLOSING_FOOTER)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "미션 완료" }),
    ).toBeInTheDocument();
  });
});

describe("라합 미션 — 카드가 배경까지 약속한다", () => {
  it("Scene 4 는 기본 배경을 쓴다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE4);
    const { container } = renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S4.continueLabel }),
    );
    expect(
      container.querySelector('[style*="/images/scenes/rahab/4.webp"]'),
    ).not.toBeNull();
  });

  it("창문과 줄을 거절하면 자막과 배경이 함께 바뀐다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE4);
    vi.mocked(decideMission).mockResolvedValue(SCENE4_ALT);
    const { container } = renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S4.skipLabel }),
    );
    await screen.findByText(C_2_15);

    /*
      둘째 잠긴 문구가 「창문과 줄은 화면에 띄우지 않는다」를 약속한다. 창과 줄은
      배경에 있으므로 자막만 빼서는 지킬 수 없는 약속이고, 그림이 그대로면 카드가 한
      말이 화면에서 거짓이 된다.
    */
    expect(screen.queryByText(C_2_19)).toBeNull();
    expect(
      container.querySelector('[style*="/images/scenes/rahab/4-alt.webp"]'),
    ).not.toBeNull();
  });
});

describe("라합 미션 — 낭독 트랙 불변식", () => {
  it("payload 에 선택지가 실려 와도 선택 버튼을 만들지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S3.continueLabel }),
    );
    await screen.findByText(C_2_13);

    // D1 — 사용자가 하는 일은 속도·동의/거절·이탈 셋뿐이다.
    expect(
      screen.queryByRole("button", { name: /숨는 것을 돕는다/ }),
    ).toBeNull();
    expect(screen.getByRole("button", { name: "계속 →" })).toBeInTheDocument();
  });

  it("위기 안내는 동의 카드 앞에서도 떠 있다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    renderPage();

    /*
      카드를 읽는 동안이 이 미션에서 가장 버거운 순간일 수 있다. 안내가 게이트 *안쪽*
      으로 들어가면 그 순간에만 사라진다. 번호는 프론트가 모른다 — 서버
      `CrisisTokenResolver` 가 치환해 보낸 문자열이 여기까지 도달하는지만 잰다.
    */
    expect(await screen.findByRole("note")).toHaveTextContent(
      CRISIS_DEFAULT.tel,
    );
  });

  it("자막의 참조는 자구와 분리된 별도 줄로 뜬다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S1.continueLabel }),
    );
    await screen.findByText(C_2_1);

    /*
      앞 인물들은 참조를 `text_ko` 안에 「(창 32:25)」로 붙여 왔는데 라합은 그러지
      않았다 — 저작 참조 24개 중 11개가 근거 미확인인 「중」 표시를 달고 있어서다.
      화자가 없는 자막(성경 서술)에는 화자가 붙지 않아야 한다.
    */
    expect(screen.getByText("수 2:1")).toBeInTheDocument();
    expect(screen.getByText("라합 · 수 2:11")).toBeInTheDocument();
  });
});
