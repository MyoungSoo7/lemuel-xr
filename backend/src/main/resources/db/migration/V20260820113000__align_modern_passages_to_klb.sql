-- V20260820113000__align_modern_passages_to_klb.sql
--
-- `translation = 'modern'` 인데 현대인의 성경 자구가 아닌 기존 46행을, 원문에서
-- 기계 추출한 **축자 본문** 으로 정렬한다.
--
-- ## 왜
--
-- `scripts/scripture_text_check.py` 를 붙이자 `modern` 92행 중 44행이 FAIL 이었다.
-- 전부 기존 시드(`V20260522003800` · `V20260717073355`) 이고, 같은 날 새로 넣은
-- 46행(`V20260820051500`) 은 전부 PASS 다. 즉 문제는 "예전 행들의 본문이 라벨이
-- 말하는 번역본이 아니다" 하나다.
--
-- 고치는 길은 셋이었다.
--   (a) 본문을 현대인의 성경 축자로 맞춘다      <- 이 마이그레이션
--   (b) `translation` 을 'rev'(개역개정) 로 정정한다
--   (c) 공개 전까지 둔다
--
-- (b) 를 먼저 쟀다. 대한성서공회 개역개정판(GAE)에서 같은 46행을 수거해 대조한
-- 결과 **축자 일치 6행 / 불일치 40행** 이었다. 개역한글 철자(`의뢰하고` ↔ 개역개정
-- `신뢰하고`), 절의 앞부분만 잘라 온 인용(`ps-13:1` · `matt-6:26`), 그리고 어느
-- 판본에도 없는 의역이 섞여 있다. 그러니까 이 코퍼스는 현대인의 성경도 개역개정도
-- 아닌 **혼합** 이고, 라벨만 'rev' 로 바꾸는 것은 틀린 라벨을 다른 틀린 라벨로
-- 바꾸는 일이다. 라벨을 바꾸면 런타임도 깨진다 — `ScriptureController` 는
-- `defaultValue = "modern"` 이고 폴백이 없어서(`E_SCRIPTURE_NOT_FOUND`),
-- 프런트가 `translation: "modern"` 으로 묻는 `/topics` 본문이 전부 404 가 된다.
-- (c) 는 부채를 그대로 두는 것이다. 그래서 (a).
--
-- ## 어떻게
--
-- BibleGateway KLB(Korean Living Bible = 현대인의 성경, © 1985 Biblica) 장 페이지에서
-- 절 span 을 축자 추출해, 행의 `verse_start..verse_end` 를 덮는 단위를 이어 붙였다.
-- 사람이 옮겨 적지 않았다 — `V20260717073355` 가 의역을 시드했다가
-- `V20260718004903` 으로 되돌린 사고가 정확히 손으로 옮긴 데서 났다.
-- 정렬 후 `scripts/scripture_text_check.py` 가 92행 전부 PASS 로 판정한다.
--
-- 46행인 이유: FAIL 44행 + 자구는 맞지만 본문이 작은따옴표로 **감싸져** 저장된 2행
-- (`gen-37:11` · `num-20:11`, `V20260718004903` 이 교정하며 껍데기를 남겼다).
-- 해시 대조는 양끝 따옴표를 벗기고 비교하므로 그 2행은 PASS 로 보였지만, 화면에는
-- 따옴표가 그대로 나간다.
--
-- ## 저작권
--
-- 이 마이그레이션은 인용 범위를 **넓히지 않는다.** 이미 시드된 것과 같은 참조,
-- 같은 절 범위이고 자구만 정확해진다. 개역개정 본문은 **넣지 않았다** — 두 번째
-- 저작권 코퍼스를 리포에 복제하면 노출이 줄지 않고 늘어난다. 개역개정은 검사기가
-- 해시로만 들고 있어(`scripts/krv_reference_hashes.json`) 위 진단을 언제든 재현할
-- 수 있으면서 본문은 리포에 남지 않는다.
-- 시드 파일이 선언한 태도(현대인의 성경 fair use · 공개 전 판권 정리)는 그대로다.

UPDATE scripture_passages SET text = '하루 종일 혼자 광야로 들어가 싸리나무 아래 앉아서 죽기를 바라며 “이제 더 바랄 것이 없습니다. 내 생명을 거둬 가소서. 내가 내 조상들보다 나은 것이 아무것도 없습니다” 하였다.'
 WHERE reference = '1kgs-19:4' AND translation = 'modern';

UPDATE scripture_passages SET text = '그러고서 그는 그 나무 아래 누워 잠이 들었다. 갑자기 한 천사가 그를 어루만지며 “일어나 먹어라” 하였다. 그래서 일어나 보니 이제 막 불에 구운 빵 하나와 물 한 병이 머리맡에 있었다. 그는 그것을 먹고 마신 다음 다시 누웠는데 여호와의 천사가 또 와서 그를 어루만지며 말하였다. “일어나서 좀더 먹어라. 네가 갈 길이 너무 멀다.” 그래서 그는 일어나 먹고 마시고 힘을 얻어 40일 동안 밤낮 걸어 하나님의 산인 시내산에 도착하였다.'
 WHERE reference = '1kgs-19:5' AND translation = 'modern';

UPDATE scripture_passages SET text = '그러자 여호와께서 “너는 나와서 내 앞에 서 있거라” 하셨다. 바로 그때 여호와께서 지나가시고 무서운 강풍이 산을 쪼개며 바위를 부수었으나 여호와는 그 가운데 계시지 않았다. 바람이 그친 후에 또 지진이 있었으나 그 지진 가운데도 여호와는 계시지 않았으며 지진 후에 불이 있었으나 그 불 속에도 여호와는 계시지 않았다. 그런데 그 불이 있은 후에 부드럽게 속삭이는 소리가 있었다. 엘리야가 이것을 듣고 자기 겉옷으로 얼굴을 가리고 굴 입구에 나가 서자 “엘리야야, 네가 여기서 무엇을 하느냐?” 라는 음성이 들려왔다.'
 WHERE reference = '1kgs-19:11' AND translation = 'modern';

UPDATE scripture_passages SET text = '그러나 내가 이스라엘 사람 가운데 아직 바알에게 무릎을 꿇지 않고 그 우상에게 입을 맞추지 않은 사람 7,000명을 남겨 두었다.”'
 WHERE reference = '1kgs-19:18' AND translation = 'modern';

UPDATE scripture_passages SET text = '세상의 모든 일은 다 정한 때와 기한이 있다:'
 WHERE reference = 'eccl-3:1' AND translation = 'modern';

UPDATE scripture_passages SET text = '이제 모든 것을 다 들었으니 결론은 이것이다: 하나님을 두려운 마음으로 섬기고 그의 명령에 순종하라. 이것이 사람의 본분이다.'
 WHERE reference = 'eccl-12:13' AND translation = 'modern';

UPDATE scripture_passages SET text = '요셉의 형들은 그를 시기하였으나 그의 아버지는 그 말을 마음에 새겨 두었다.'
 WHERE reference = 'gen-37:11' AND translation = 'modern';

UPDATE scripture_passages SET text = '요셉을 잡아 감옥에 처넣었는데 그 곳은 궁중 죄수들을 가두는 곳이었다. 요셉이 감옥에 갇혔으나'
 WHERE reference = 'gen-39:20' AND translation = 'modern';

UPDATE scripture_passages SET text = '그러자 요셉이 바로에게 말하였다. “그 두 가지 꿈은 한 가지 일을 뜻합니다. 하나님이 앞으로 일어날 일을 왕에게 보이신 것입니다. 일곱 마리의 살진 소는 7년을 가리키며 일곱 개의 알찬 이삭도 7년을 말합니다. 그래서 그 꿈은 동일한 것입니다. 그 후에 올라온 여위고 흉측한 소도 7년을 가리키며 사막의 바람에 말라붙어 쭉정이가 된 그 일곱 이삭은 7년 동안의 기근을 뜻합니다. 내가 왕에게 말씀드린 대로 바로 이것이 하나님께서 왕에게 보여 주신 앞으로 일어날 일입니다. 앞으로 7년 동안 이집트 전역에 큰 풍년이 있을 것입니다. 그러나 그 후 7년 동안은 흉년이 들 것이며 흉년으로 풍요롭던 시절은 다 잊혀지고 이 땅은 황폐해질 것입니다. 그 기근이 너무 심하므로 이 땅에서 이전의 풍년을 기억하지 못할 것입니다. 왕이 꿈을 두 번 연달아 꾸신 것은 하나님이 이 일을 정하셨으며 그 일을 속히 행하실 것을 뜻합니다.'
 WHERE reference = 'gen-41:25' AND translation = 'modern';

UPDATE scripture_passages SET text = '“이제 왕은 총명하고 지혜로운 사람을 택하여 나라 일을 맡겨야 합니다. 행정 구역을 다섯으로 나누고 각 구역 마다 관리를 두어 풍년이 든 7년 동안에 잉여 농산물을 모조리 거두어 왕의 권한으로 각 성의 창고에 비축해 두십시오. 이와 같이 식량을 비축해 두시면 앞으로 이집트 땅에 7년 동안 흉년이 들어도 백성들이 굶어 죽지는 않을 것입니다.”'
 WHERE reference = 'gen-41:33' AND translation = 'modern';

UPDATE scripture_passages SET text = '요셉에게 이렇게 말하였다. “하나님이 이 모든 일을 너에게 알게 하셨으니 너처럼 총명하고 지혜로운 사람이 없구나. 너는 내 나라를 다스려라. 내 백성이 다 네 명령에 복종할 것이다. 내가 너보다 높은 것은 이 왕좌뿐이다.” 그러고서 바로는 요셉에게 “내가 너를 이집트 전국을 다스릴 총리로 임명한다” 하며'
 WHERE reference = 'gen-41:39' AND translation = 'modern';

UPDATE scripture_passages SET text = '이집트 땅에 7년 동안의 풍년이 끝나고 요셉의 말대로 7년 흉년이 시작되었다. 이때 다른 나라에는 기근이 들어 굶주렸으나 이집트 전역에는 식량이 있었다.'
 WHERE reference = 'gen-41:53' AND translation = 'modern';

UPDATE scripture_passages SET text = '이때 요셉은 나라의 총리가 되어 모든 백성에게 곡식을 팔고 있었다. 요셉의 형들이 그 앞에 와서 땅에 엎드려 절하자'
 WHERE reference = 'gen-42:6' AND translation = 'modern';

UPDATE scripture_passages SET text = '그때 요셉이 형들에게 “나에게 가까이 오십시오” 하자 그들이 가까이 다가갔다. 그래서 요셉이 그들에게 이렇게 말하였다. “나는 형님들이 이집트에 판 동생 요셉입니다. 형님들이 나를 이 곳에 팔았다고 근심하거나 한탄하지 마십시오. 하나님께서 우리 가족을 구하시려고 나를 형님들보다 먼저 이 곳에 보내셨습니다.'
 WHERE reference = 'gen-45:4' AND translation = 'modern';

UPDATE scripture_passages SET text = '형님들이 나를 이 곳에 팔았다고 근심하거나 한탄하지 마십시오. 하나님께서 우리 가족을 구하시려고 나를 형님들보다 먼저 이 곳에 보내셨습니다.'
 WHERE reference = 'gen-45:5' AND translation = 'modern';

UPDATE scripture_passages SET text = '형님들은 나를 해치려고 하였으나 하나님은 그것을 선으로 바꾸셔서 오늘날 내가 많은 사람의 생명을 구할 수 있게 하셨습니다.'
 WHERE reference = 'gen-50:20' AND translation = 'modern';

UPDATE scripture_passages SET text = '“옳은 것이 무엇인지 아는 자들아, 마음 가운데 내 율법을 간직한 사람들아, 너희는 내 말을 들어라. 사람들이 너희를 조롱하고 비웃어도 너희는 두려워하거나 놀라지 말아라.'
 WHERE reference = 'isa-51:7' AND translation = 'modern';

UPDATE scripture_passages SET text = '“내가 태어난 날이여, 저주를 받아라. 내가 임신이 되던 그 밤도 저주를 받아라.'
 WHERE reference = 'job-3:3' AND translation = 'modern';

UPDATE scripture_passages SET text = '“내가 어머니 뱃속에서 태어날 때 차라리 죽었더라면 좋았을 걸! 어째서 어머니가 나를 무릎에 받아 젖을 빨게 하였는가? 내가 그때 죽었더라면 지금쯤은 평안히 잠들어 쉬고 있을 텐데.'
 WHERE reference = 'job-3:11' AND translation = 'modern';

UPDATE scripture_passages SET text = '“내가 구하고 사모하는 것을 하나님이 주셨으면 얼마나 좋을까! 하나님이 내 생명을 끊어 나를 기꺼이 죽여 주셨으면 좋으련만!'
 WHERE reference = 'job-6:8' AND translation = 'modern';

UPDATE scripture_passages SET text = '그러므로 내가 침묵을 지키지 않고 내 괴로움을 말하며 내 영혼의 슬픔을 털어놓아야겠습니다.'
 WHERE reference = 'job-7:11' AND translation = 'modern';

UPDATE scripture_passages SET text = '내가 하나님을 발견할 수 있는 곳을 알고 그 곳으로 갈 수 있다면 내가 그분 앞에 나아가 내 문제를 내어놓고 변명하며 또 나에게 대답하시는 말씀을 듣고 그 말씀을 깨달을 수 있을 텐데.'
 WHERE reference = 'job-23:3' AND translation = 'modern';

UPDATE scripture_passages SET text = '그때 여호와께서 폭풍 가운데서 욥에게 말씀하셨다. “무식한 말로 내 뜻을 흐리게 하는 자가 누구냐? 이제 너는 남자답게 일어나 내가 묻는 말에 대답하라. 내가 땅의 기초를 놓을 때에 너는 어디 있었느냐? 네가 그렇게 많이 알면 한번 말해 보아라. 누가 그 크기를 정하였으며 누가 그 위에 측량줄을 대어 보았는지 너는 알고 있느냐? 땅의 기초를 받치고 있는 것이 무엇이 냐? 새벽 별들이 함께 노래하며 하늘의 천사들이 기뻐 외치는 가운데 땅의 모퉁잇돌을 놓은 자가 누구냐?'
 WHERE reference = 'job-38:1' AND translation = 'modern';

UPDATE scripture_passages SET text = '내가 땅의 기초를 놓을 때에 너는 어디 있었느냐? 네가 그렇게 많이 알면 한번 말해 보아라.'
 WHERE reference = 'job-38:4' AND translation = 'modern';

UPDATE scripture_passages SET text = '전에는 내가 주께 대하여 귀로 듣기만 했는데 이제는 내 눈으로 주를 직접 보았습니다. 그래서 내가 말한 모든 것을 부끄럽게 여기며 티끌과 재 가운데서 회개합니다.”'
 WHERE reference = 'job-42:5' AND translation = 'modern';

UPDATE scripture_passages SET text = '공중의 새를 보아라. 새는 씨를 뿌리거나 거두지도 않고 곳간에 모아들이지도 않는다. 그러나 하늘에 계시는 너희 아버지께서 새를 기르신다. 너희는 새보다 더 귀하지 않느냐?'
 WHERE reference = 'matt-6:26' AND translation = 'modern';

UPDATE scripture_passages SET text = '조금 더 나아가 땅에 엎드려 이렇게 기도하셨다. “아버지, 할 수만 있으면 이 고난의 잔을 내게서 거두어 주십시오. 그러나 내 뜻대로 마시고 아버지의 뜻대로 하십시오.”'
 WHERE reference = 'matt-26:39' AND translation = 'modern';

UPDATE scripture_passages SET text = '오후 3시쯤에 예수님은 큰 소리로 “엘리, 엘리, 라마 사박다니” 하고 외치셨다. 이 말씀은 “나의 하나님, 나의 하나님, 왜 나를 버리셨습니까” 라는 뜻이다.'
 WHERE reference = 'matt-27:46' AND translation = 'modern';

UPDATE scripture_passages SET text = '지팡이를 들어 바위를 두 번 쳤다. 그러자 물이 분수처럼 솟구쳐나와 백성과 그들의 짐승이 다 그 물을 마셨다.'
 WHERE reference = 'num-20:11' AND translation = 'modern';

UPDATE scripture_passages SET text = '너는 마음을 다하여 여호와를 신뢰하고 네 지식을 의지하지 말아라.'
 WHERE reference = 'prov-3:5' AND translation = 'modern';

UPDATE scripture_passages SET text = '그 무엇보다도 네 마음을 지켜라. 여기서부터 생명의 샘이 흘러나온다.'
 WHERE reference = 'prov-4:23' AND translation = 'modern';

UPDATE scripture_passages SET text = '말이 많으면 죄를 짓기 쉬우니 말을 삼가는 사람이 지혜로운 자이다.'
 WHERE reference = 'prov-10:19' AND translation = 'modern';

UPDATE scripture_passages SET text = '사람을 두려워하면 덫에 걸리지만 여호와를 신뢰하면 안전할 것이다.'
 WHERE reference = 'prov-29:25' AND translation = 'modern';

UPDATE scripture_passages SET text = '여호와여, 언제까지 나를 잊으시겠습니까? 영원히 잊으실 작정이십니까? 나에게 주의 얼굴을 언제까지 숨기시겠습니까?'
 WHERE reference = 'ps-13:1' AND translation = 'modern';

UPDATE scripture_passages SET text = '나의 하나님, 나의 하나님, 어찌하여 나를 버리셨습니까? 어째서 나를 돕지 않으시고 내가 신음하는 소리에 귀를 기울이지 않으십니까? 나의 하나님이시여, 내가 밤낮 울부짖어도 주께서는 아무 대답도 없으십니다.'
 WHERE reference = 'ps-22:1' AND translation = 'modern';

UPDATE scripture_passages SET text = '이제 나는 물같이 쏟아졌고 나의 모든 뼈는 어그러졌으며 내 마음은 양초같이 되어 내 속에서 녹아 버렸습니다. 내 힘이 말라 질그릇 조각 같고 내 혀가 입천장에 달라붙었으니 주께서 나를 죽음의 먼지 속에 버려 두셨기 때문입니다.'
 WHERE reference = 'ps-22:14' AND translation = 'modern';

UPDATE scripture_passages SET text = '여호와는 나의 목자시니 내가 부족함이 없으리라.'
 WHERE reference = 'ps-23:1' AND translation = 'modern';

UPDATE scripture_passages SET text = '하나님이시여, 사슴이 시냇물을 갈망하듯이 내 영혼이 주를 갈망합니다. 내 영혼이 살아 계신 하나님을 애타게 그리워하는데 내가 언제나 나아가서 하나님을 뵐 수 있을까? 내가 밤낮 부르짖어 눈물이 내 음식이 되었으나 사람들은 “네 하나님이 어디 있느냐?” 하고 종일 나를 비웃는구나.'
 WHERE reference = 'ps-42:1' AND translation = 'modern';

UPDATE scripture_passages SET text = '내 영혼아, 어째서 네가 낙심하며 내 속에서 불안해 하는가? 너는 네 희망을 하나님께 두어라. 나는 내 구원이 되시는 하나님을 찬양하리라.'
 WHERE reference = 'ps-42:11' AND translation = 'modern';

UPDATE scripture_passages SET text = '하나님이시여, 나를 불쌍히 여기시고 나를 불쌍히 여기소서. 내 영혼이 주를 의지합니다. 내가 이 재난이 지날 때까지 주의 날개 그늘 아래 피하겠습니다.'
 WHERE reference = 'ps-57:1' AND translation = 'modern';

UPDATE scripture_passages SET text = '여호와여, 내 구원의 하나님이시여, 내가 밤낮 주 앞에서 부르짖습니다. 나의 기도를 들으시고 내가 부르짖는 소리에 귀를 기울이소서. 내 영혼이 너무 많은 고통에 시달려 내가 거의 죽게 되었습니다.'
 WHERE reference = 'ps-88:1' AND translation = 'modern';

UPDATE scripture_passages SET text = '여호와여, 내가 주께 부르짖고 아침마다 주께 기도합니다. 어째서 나를 버리시며 어째서 주의 얼굴을 나에게 숨기십니까?'
 WHERE reference = 'ps-88:13' AND translation = 'modern';

UPDATE scripture_passages SET text = '주는 나의 사랑하는 자들과 친구들이 나를 버리게 하셨으므로 흑암이 나의 유일한 친구가 되었습니다.'
 WHERE reference = 'ps-88:18' AND translation = 'modern';

UPDATE scripture_passages SET text = '여호와여, 주는 항상 우리의 안식처가 되셨습니다.'
 WHERE reference = 'ps-90:1' AND translation = 'modern';

UPDATE scripture_passages SET text = '여호와여, 내가 절망의 늪에서 주께 부르짖습니다. 여호와여, 내 소리를 듣고 나의 간절한 기도에 귀를 기울이소서. 여호와여, 만일 주께서 우리 죄를 일일이 기록하신다면 누가 감히 주 앞에 설 수 있겠습니까? 그러나 주께서 우리를 용서하시므로 우리가 두려운 마음으로 주를 섬깁니다.'
 WHERE reference = 'ps-130:1' AND translation = 'modern';

UPDATE scripture_passages SET text = '우리는 바빌론 강변에 앉아서 시온을 기억하며 울었다. 우리가 수금을 버드나무 가지에 걸었으니 우리를 사로잡은 자들이 우리에게 노래를 청하고 우리를 괴롭히는 자들이 즐거운 노래를 요구하며 “시온의 노래 중 하나를 불러라” 하고 말하였음이라. 우리가 외국 땅에서 어떻게 여호와의 노래를 부를 수 있겠는가?'
 WHERE reference = 'ps-137:1' AND translation = 'modern';
