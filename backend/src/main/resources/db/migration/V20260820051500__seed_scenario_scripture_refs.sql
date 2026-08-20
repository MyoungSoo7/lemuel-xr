-- V20260820051500: 시나리오가 가리키던 미시드 `scripture_ref` 46행 시드.
--
-- 배경 (2026-08-20):
--   scripts/scripture_ref_check.py 도입 시점에 시나리오 8편의 참조 44개 중 31개가
--   scripture_passages 에 대응 행이 없었다 (예수·모세 100%, 룻 100%, 다윗 100%).
--   ScenePayloadAssembler:51 은 이 문자열을 조인 없이 그대로 실어 보내므로 지금은
--   보이는 고장이 아니지만, 미션 화면에 본문 열람을 붙이는 순간 조용히 빈다.
--
-- 이 마이그레이션은 그 포인터들이 가리킬 행을 만든다.
--
-- 출처·방법:
--   BibleGateway 현대인의 성경(Korean Living Bible, KLB) 각 장 페이지 HTML 을
--   기계로 파싱해 절 텍스트를 축자 추출했다 (2026-08-20 수거).
--   https://www.biblegateway.com/passage/?search=<Book+Chapter>&version=KLB
--   사람이 옮겨 적거나 모델이 재구성한 문장이 아니다 — V20260717073355 가
--   "의역 재구성" 을 시드했다가 V20260718004903 에서 원문 대조로 교정해야 했던
--   사고의 재발 방지책이다.
--
--   KLB 가 합쳐 번역한 절(삼상 17:38-39 · 창 41:34-35 · 룻 4:18-22)은 시작 절을
--   reference 키로 한 행 하나로 넣고 verse_end 에 끝 절을 적었다.
--
--   추출기 함정 하나 — 절 경계를 "다음 절 마커까지" 로 자르면 **장의 마지막 절만**
--   페이지 꼬리(내비게이션·저작권 고지·스크립트)를 통째로 삼킨다. 초안의
--   ruth-4:18 이 1958자로 부풀어 있었고, scripts/scripture_text_check.py 가
--   그 한 행만 FAIL 로 잡아 냈다. 지금은 닫는 </span> 까지로 자른다.
--
-- ⚠️ 이 마이그레이션이 하지 않는 것 — 기존 46행은 건드리지 않는다:
--   같은 방법으로 기존 시드를 대조해 보니, 절 단위 비교가 가능한 27행 중 25행이
--   KLB 축자와 다르다 (예: ps-23:1 시드 "내게 부족함이 없으리로다" vs KLB
--   "내가 부족함이 없으리라." / 왕상 19:4 시드 "로뎀나무" vs KLB "싸리나무").
--   즉 translation='modern' 라벨이 데이터와 맞지 않는다. 일치하는 2행은
--   V20260718004903 에서 실제로 원문 대조 교정을 거친 행들이다.
--   그 25행을 어느 번역본으로 정렬할지는 라이선스 결정이 걸린 문제라 여기서
--   임의로 고치지 않는다. scripts/scripture_text_check.py 가 그 상태를 센다.
--
-- ⚠️ 저작권: 현대인의 성경 (Korean Living Bible). MVP 비공개 테스터 ≤10명 fair use.
--   공개 출시 전 라이선스 협의 OR 개역개정(대한성서공회 비영리 약관) 으로 swap.
--   translation='modern' 으로 분리해 두어 추후 'rev' 로 swap 가능하게 한다.
--
-- 기존 시드와 다른 점 하나 더: 본문을 따옴표로 감싸지 않는다.
--   기존 46행은 '''...''' 로 감싸 저장돼 text 값 자체가 작은따옴표로 시작·끝난다.
--   표현(따옴표)은 데이터가 아니므로 새 행은 절 본문만 담는다.
--
-- Rollback:
--   DELETE FROM scripture_passages WHERE translation = 'modern'
--     AND reference IN (아래 INSERT 의 reference 목록);
--   본문만 제거되며 시나리오·인덱스에는 영향이 없다.

-- 다윗 — 시 23(Scene 1 앰비언스) · 삼상 17(Scene 2~5 + outro)
INSERT INTO scripture_passages (reference, translation, book, book_code, chapter, verse_start, verse_end, text, character_tags) VALUES
('ps-23:2',        'modern', 'psalms', 'ps', 23, 2, 2,
 '그가 나를 푸른 풀밭에 쉬게 하시고 잔잔한 물가로 인도하시며', ARRAY['david']),
('ps-23:3',        'modern', 'psalms', 'ps', 23, 3, 3,
 '내 영혼을 소생시키시고 자기 이름을 위하여 나를 의로운 길로 인도하시는구나.', ARRAY['david']),
('ps-23:4',        'modern', 'psalms', 'ps', 23, 4, 4,
 '내가 죽음의 음산한 계곡을 걸어가도 두려워하지 않을 것은 주께서 나와 함께하심이라. 주의 지팡이와 막대기가 나를 지키시니 내가 안심하리라.', ARRAY['david']),
('ps-23:5',        'modern', 'psalms', 'ps', 23, 5, 5,
 '주께서 내 원수들이 보는 가운데 나를 위해 잔치를 베푸시고 나를 귀한 손님으로 맞아 주셨으니 내 잔이 넘치는구나.', ARRAY['david']),
('ps-23:6',        'modern', 'psalms', 'ps', 23, 6, 6,
 '주의 선하심과 한결같은 사랑이 평생에 나를 따를 것이니 내가 여호와의 집에서 영원히 살리라.', ARRAY['david']),
('1sam-17:28',     'modern', '1_samuel', '1sam', 17, 28, 28,
 '그러나 다윗의 맏형 엘리압은 다윗이 그렇게 말하는 것을 듣고 화가 나서 “도대체 너 여기서 무엇하고 있는 거니? 들에 있는 양은 누구에게 맡겼어? 나는 네가 얼마나 교만한 녀석인지 알고 있다. 너는 전쟁을 구경하러 온 놈이구나!” 하였다.', ARRAY['david']),
('1sam-17:38',     'modern', '1_samuel', '1sam', 17, 38, 39,  -- KLB 합본 38-39절
 '사울은 자기가 착용하고 있던 놋투구와 갑옷을 벗어 다윗에게 주었 다. 다윗은 투구를 쓰고 갑옷을 입은 다음 사울의 칼을 차고 시험 삼아 몇 걸음 걸어 보았으나 거추장스러워서 도저히 활동할 수가 없었다. 그래서 그는 사울에게 “이대로는 움직일 수도 없습니다” 하고 그것들을 다 벗어 버렸다.', ARRAY['david']),
('1sam-17:40',     'modern', '1_samuel', '1sam', 17, 40, 40,
 '그러고서 그는 시냇가로 가서 매끄러운 돌 다섯 개를 골라 그의 목양 주머니에 넣고 그가 양을 칠 때 사용하던 지팡이와 물매만 가지고 블레셋 거인 골리앗을 향해 나아갔다.', ARRAY['david']),
('1sam-17:45',     'modern', '1_samuel', '1sam', 17, 45, 45,
 '이때 다윗이 소리치며 그 블레셋 사람에게 말하였다. “너는 칼과 창을 가지고 나왔지만 나는 전능하신 여호와, 곧 네가 모욕하는 이스라엘 군대의 하나님의 이름으로 나왔다.', ARRAY['david']),
('1sam-17:46',     'modern', '1_samuel', '1sam', 17, 46, 46,
 '오늘 여호와께서 너를 내 손에 넘겨 주실 것이며 나는 너를 죽여 네 목을 자르고 또 블레셋군의 시체를 새와 들짐승에게 주어 먹게 하겠다. 그러면 온 세상이 이스라엘에 하나님이 계신 것을 알게 될 것이다.', ARRAY['david']),
('1sam-17:47',     'modern', '1_samuel', '1sam', 17, 47, 47,
 '그리고 이 곳에 있는 모든 사람들도 여호와께서 자기 백성을 구원하는 데 창이나 칼이 필요치 않음을 알게 될 것이다! 전쟁은 여호와께 속한 것이므로 그분이 너희를 우리 손에 넘겨 주실 것이다!”', ARRAY['david'])
ON CONFLICT (reference, translation) DO NOTHING;

-- 엘리야 — 왕상 19:3. 기존 시드는 19:4 부터라 도망하는 앞 한 절이 비어 있었다
INSERT INTO scripture_passages (reference, translation, book, book_code, chapter, verse_start, verse_end, text, character_tags) VALUES
('1kgs-19:3',      'modern', '1_kings', '1kgs', 19, 3, 3,
 '그래서 엘리야는 두려워서 자기 사환을 데리고 유다의 브엘세바로 도망하였다. 그는 사환을 그 곳에 머물러 있게 하고', ARRAY['elijah'])
ON CONFLICT (reference, translation) DO NOTHING;

-- 예수 — 요·마·막·눅. 7개 Scene 참조가 전부 미시드였다
INSERT INTO scripture_passages (reference, translation, book, book_code, chapter, verse_start, verse_end, text, character_tags) VALUES
('jn-1:14',        'modern', 'john', 'jn', 1, 14, 14,
 '말씀 되시는 그리스도께서 사람이 되어 우리 가운데 사셨다. 우리가 그분의 영광을 보니 하나님 아버지의 외아들의 영광이었고 은혜와 진리가 충만하였다.', ARRAY['jesus']),
('matt-5:3',       'modern', 'matthew', 'matt', 5, 3, 3,
 '“마음이 가난한 사람들은 행복하다. 하늘 나라가 그들의 것이다.', ARRAY['jesus']),
('mk-1:41',        'modern', 'mark', 'mk', 1, 41, 41,
 '예수님이 불쌍히 여겨 그에게 손을 대시며 “내가 원한다. 깨끗이 나아라” 하고 말씀하시자', ARRAY['jesus']),
('jn-14:6',        'modern', 'john', 'jn', 14, 6, 6,
 '그래서 예수님이 그에게 말씀하셨다. “나는 길이요 진리요 생명이다. 나를 통하지 않고는 아무도 아버지께로 가지 못한다.', ARRAY['jesus']),
('lk-22:42',       'modern', 'luke', 'lk', 22, 42, 42,
 '“아버지, 아버지께서 원하신다면 이 고난의 잔을 내게서 거두어 주십시오. 그러나 내 뜻대로 마시고 아버지의 뜻대로 하십시오.”', ARRAY['jesus']),
('jn-20:15',       'modern', 'john', 'jn', 20, 15, 15,
 '예수님은 마리아에게 “여자여, 왜 우느냐? 누구를 찾느냐?” 하고 물으셨다. 마리아는 그분이 동산 관리인인 줄 알고 “여보세요, 당신이 그분을 가져갔으면 어디에 두었는지 말씀해 주세요. 내가 모셔 가겠습니다” 하였다.', ARRAY['jesus']),
('jn-7:38',        'modern', 'john', 'jn', 7, 38, 38,
 '나를 믿는 사람은 성경 말씀대로 그 마음속에서 생수의 강이 흘러 나올 것이다.”', ARRAY['jesus'])
ON CONFLICT (reference, translation) DO NOTHING;

-- 욥 — 2:13(침묵의 이레) · 4:7(엘리바스의 인과응보 논변)
INSERT INTO scripture_passages (reference, translation, book, book_code, chapter, verse_start, verse_end, text, character_tags) VALUES
('job-2:13',       'modern', 'job', 'job', 2, 13, 13,
 '그러고서 그들은 밤낮 7일을 꼬박 그와 함께 땅바닥에 앉아 있었으나 욥의 고통이 너무 큰 것을 보았기 때문에 말 한마디 하는 자가 없었다.', ARRAY['job']),
('job-4:7',        'modern', 'job', 'job', 4, 7, 7,
 '한번 생각해 보아라. 죄 없이 벌받는 자가 누구인가? 정직한 자가 망한 적이 어디 있는가?', ARRAY['job'])
ON CONFLICT (reference, translation) DO NOTHING;

-- 요셉 — 창 41:34 · 41:56. 기존 시드는 41:25·33·39·53 만 있었다
INSERT INTO scripture_passages (reference, translation, book, book_code, chapter, verse_start, verse_end, text, character_tags) VALUES
('gen-41:34',      'modern', 'genesis', 'gen', 41, 34, 35,  -- KLB 합본 34-35절
 '행정 구역을 다섯으로 나누고 각 구역 마다 관리를 두어 풍년이 든 7년 동안에 잉여 농산물을 모조리 거두어 왕의 권한으로 각 성의 창고에 비축해 두십시오.', ARRAY['joseph']),
('gen-41:56',      'modern', 'genesis', 'gen', 41, 56, 56,
 '이집트 전국에 기근이 더욱 심각해지자 요셉은 모든 창고를 열고 이집트 사람들에게 곡식을 팔았다.', ARRAY['joseph'])
ON CONFLICT (reference, translation) DO NOTHING;

-- 모세 — 출 3·7·14. 4개 Scene 참조가 전부 미시드였다
INSERT INTO scripture_passages (reference, translation, book, book_code, chapter, verse_start, verse_end, text, character_tags) VALUES
('ex-3:5',         'modern', 'exodus', 'ex', 3, 5, 5,
 '하나님이 그에게 말씀하셨다. “더 이상 가까이 오지 말아라. 네가 선 곳은 거룩한 땅이다. 신을 벗어라.', ARRAY['moses']),
('ex-3:12',        'modern', 'exodus', 'ex', 3, 12, 12,
 '“내가 너와 함께하겠다. 네가 백성을 이집트에서 인도해 낸 후에 너희가 이 산에서 나를 섬길 것이다. 바로 이것이 내가 너를 보내는 증거가 될 것이다.”', ARRAY['moses']),
('ex-7:10',        'modern', 'exodus', 'ex', 7, 10, 10,
 '그래서 모세와 아론은 바로에게 가서 여호와께서 명령하신 대로 했는데 아론이 바로와 그의 신하들 앞에 자기 지팡이를 던지자 그것이 뱀이 되었다.', ARRAY['moses']),
('ex-14:13',       'modern', 'exodus', 'ex', 14, 13, 13,
 '그래서 모세가 백성들에게 대답하였다. “여러분은 두려워하지 말고 가만히 서서 오늘 여호와께서 여러분을 구하기 위해 행하시는 일을 보십시오. 여러분이 오늘 보는 이 이집트 사람들을 다시는 보지 못할 것입니다.', ARRAY['moses'])
ON CONFLICT (reference, translation) DO NOTHING;

-- 룻 — 룻 1~4. 5개 Scene 이 전부 복수 절 묶음 참조였다 (규약대로 절 단위로 편다)
INSERT INTO scripture_passages (reference, translation, book, book_code, chapter, verse_start, verse_end, text, character_tags) VALUES
('ruth-1:8',       'modern', 'ruth', 'ruth', 1, 8, 8,
 '나오미가 두 며느리에게 말하였다. “너희는 모두 친정으로 돌아가거라. 너희는 죽은 너희 남편과 나를 정성껏 섬겼다. 그러므로 여호와께서 너희가 행한 대로 갚아 주시기 원하며', ARRAY['ruth']),
('ruth-1:9',       'modern', 'ruth', 'ruth', 1, 9, 9,
 '또 너희가 재혼하여 행복한 가정을 이룰 수 있도록 축복해 주시기 원한다.” 그러고서 나오미가 그들에게 입을 맞추고 작별하려고 하자 그들은 큰 소리로 울며', ARRAY['ruth']),
('ruth-1:16',      'modern', 'ruth', 'ruth', 1, 16, 16,
 '그러나 룻은 이렇게 대답하였다. “저에게 억지로 어머니 곁을 떠나라고 강요하지 마시고 어머니와 함께 가게 해 주세요. 어머니께서 가시는 곳에 저도 가고 어머니께서 사시는 곳에 저도 살겠습니다. 어머니의 백성이 저의 백성이 되고 어머니의 하나님이 저의 하나님이 되실 것입니다.', ARRAY['ruth']),
('ruth-1:19',      'modern', 'ruth', 'ruth', 1, 19, 19,
 '그래서 그들은 함께 베들레헴으로 떠났다. 그들이 그 곳에 도착했을 때 온 성이 떠들썩하며 그 곳 여자들이 “정말 이 사람이 나오미냐?” 하고 물었다.', ARRAY['ruth']),
('ruth-1:20',      'modern', 'ruth', 'ruth', 1, 20, 20,
 '그러나 나오미는 그들에게 이렇게 대답하였다. “나를 나오미라 부르지 말고 ‘마라’ 라고 불러 주시오. 이것은 전능하신 하나님이 나에게 괴로운 시련을 많이 주셨기 때문입니다.', ARRAY['ruth']),
('ruth-1:21',      'modern', 'ruth', 'ruth', 1, 21, 21,
 '내가 이 곳을 떠날 때는 가진 것이 많았으나 여호와께서는 나를 빈손으로 돌아오게 하셨습니다. 여호와께서 나를 버리시고 나에게 괴로움을 주셨으니 어떻게 당신들이 나를 나오미라고 부를 수 있겠습니까?”', ARRAY['ruth']),
('ruth-2:2',       'modern', 'ruth', 'ruth', 2, 2, 2,
 '하루는 룻이 나오미에게 말하였다. “제가 밭에 가서 이삭을 줍게 해 주세요. 혹시 친절한 사람이라도 만나면 제가 그를 따라다니며 이삭을 줍겠습니다.” 그러자 나오미가 “내 딸아, 가거라” 하고 대답하였다.', ARRAY['ruth']),
('ruth-2:10',      'modern', 'ruth', 'ruth', 2, 10, 10,
 '그러자 룻은 땅에 얼굴을 대고 절하며 말하였다. “어찌 나 같은 이방 여자에게 이런 친절을 베풀고 돌보아 주십니까?”', ARRAY['ruth']),
('ruth-2:11',      'modern', 'ruth', 'ruth', 2, 11, 11,
 '“나는 그대가 남편을 잃은 이후로 시어머니에게 한 일을 모두 들었네. 그리고 그대가 어떻게 부모가 있는 고향을 마다하고 이 곳 낯선 땅에까지 와서 살고 있는지도 다 알고 있네.', ARRAY['ruth']),
('ruth-2:12',      'modern', 'ruth', 'ruth', 2, 12, 12,
 '이스라엘의 하나님 여호와께서 그대가 행한 대로 갚아 주시기 원하며 그분의 보호를 받고자 온 그대에게 풍성한 상을 주시기 원하네.”', ARRAY['ruth']),
('ruth-3:9',       'modern', 'ruth', 'ruth', 3, 9, 9,
 '그래서 그는 “네가 누구냐?” 하고 물었다. 그러자 룻이 “나는 당신의 종 룻입니다. 나와 결혼해 주세요. 당신은 나를 돌볼 책임이 있는 가까운 친척입니다” 하고 대답하였다.', ARRAY['ruth']),
('ruth-3:11',      'modern', 'ruth', 'ruth', 3, 11, 11,
 '이제 그대는 아무것도 두려워하지 말게. 내가 그대의 모든 요구를 들어주겠네. 그대가 현숙한 여인이라는 것은 우리 성 주민들도 다 아는 일이네.', ARRAY['ruth']),
('ruth-4:11',      'modern', 'ruth', 'ruth', 4, 11, 11,
 '그때 거기에 모인 사람들이 “그렇소. 우리가 증인이오” 하자 장로 중 한 사람이 일어나 보아스에게 말하였다. “여호와께서 이제 당신의 아내가 될 이 여인을 이스라엘 각 지파의 조상들을 낳은 라헬과 레아처럼 되게 하시기 바라며 당신은 에브랏에서 유력하고 베들레헴에서 이름을 떨치는 사람이 되기를 빌겠소.', ARRAY['ruth']),
('ruth-4:12',      'modern', 'ruth', 'ruth', 4, 12, 12,
 '그리고 여호와께서 이 여자를 통하여 당신에게 주시는 자녀들은 다말과 유다 사이에서 난 우리 조상, 베레스의 자녀들처럼 되기를 원합니다.”', ARRAY['ruth']),
('ruth-4:18',      'modern', 'ruth', 'ruth', 4, 18, 22,  -- KLB 합본 18-22절
 '베레스부터 다윗까지의 족보는 이렇 다: 베레스는 헤스론을 낳았고 헤스론은 람을, 람은 암미나답을, 암미나답은 나손을, 나손은 살몬을, 살몬은 보아스를, 보아스는 오벳을, 오벳은 이새를, 이새는 다윗을 낳았다.', ARRAY['ruth'])
ON CONFLICT (reference, translation) DO NOTHING;

-- 솔로몬 — 왕상 3 · 전 2:11. 기존 시드는 전 12:13 만 있었다
INSERT INTO scripture_passages (reference, translation, book, book_code, chapter, verse_start, verse_end, text, character_tags) VALUES
('1kgs-3:9',       'modern', '1_kings', '1kgs', 3, 9, 9,
 '그러므로 주의 백성들을 잘 다스리고 선과 악을 분별할 수 있는 지혜로운 마음을 나에게 주소서. 그렇지 않으면 내가 어떻게 이처럼 많은 주의 백성을 다스릴 수 있겠습니까?”', ARRAY['solomon']),
('1kgs-3:12',      'modern', '1_kings', '1kgs', 3, 12, 12,
 '내가 네 요구대로 지혜롭고 총명한 마음을 너에게 주어 역사상에 너와 같은 자가 없도록 하겠다.', ARRAY['solomon']),
('1kgs-3:28',      'modern', '1_kings', '1kgs', 3, 28, 28,
 '이 소문이 온 이스라엘 땅에 퍼지자 백성들은 하나님이 솔로몬에게 놀라운 지혜를 주신 줄 알고 두려운 마음으로 그를 우러러보았다.', ARRAY['solomon']),
('eccl-2:11',      'modern', 'ecclesiastes', 'eccl', 2, 11, 11,
 '그러나 잘 살펴보니 내가 애써 이룩한 그 모든 것이 아무런 의미도 없으며 바람을 잡으려는 것처럼 다 무익한 것이었다.', ARRAY['solomon'])
ON CONFLICT (reference, translation) DO NOTHING;
