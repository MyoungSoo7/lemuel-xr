"""
LLM 사이드카 출력측 안전 필터 테스트.

이 파일이 생기기 전까지 `ai/` 에는 테스트가 한 개도 없었다.
그래서 아래 결함이 오래 눈에 띄지 않았다.

결함: `/ai/generate` 는 `jsonMode=true` 일 때 `_safety_filter` 를 통째로 건너뛴다.
주석은 "JSON 모드 응답은 구조화된 데이터라 skip" 이라고 설명하지만, 구조화됐다는 것은
*문자열 값 안에 자연어가 들어 있다* 는 뜻이지 자연어가 없다는 뜻이 아니다.
JSON 값으로 전달된 자해 묘사는 그대로 사용자에게 도달한다.

현재 이 경로에 호출자는 없다(backend·frontend 어디에서도 jsonMode 를 설정하지 않는다).
즉 지금 터지는 버그가 아니라, 누군가 jsonMode 를 쓰기 시작하는 순간 안전 필터가
조용히 사라지는 *잠재적 함정* 이다.

수정 방향: JSON 모드에서도 검사하되, 평문 fallback 을 그대로 돌려주면 호출자의 JSON
파싱이 깨지므로 *유효한 JSON* 형태의 안전 응답을 돌려준다.
"""

import json

import app


class TestSafetyFilterCore:
    """평문 경로 — 기존 동작 회귀 방지."""

    def test_안전한_문장은_그대로_통과한다(self):
        text, blocked = app._safety_filter("오늘 하루 버텨낸 것으로 충분합니다.")

        assert text == "오늘 하루 버텨낸 것으로 충분합니다."
        assert blocked is None

    def test_자해_묘사는_차단된다(self):
        text, blocked = app._safety_filter("손목을 그었습니다")

        assert text == app._SAFE_FALLBACK
        assert blocked is not None

    def test_자살예방_같은_안전한_합성어는_통과한다(self):
        text, blocked = app._safety_filter("자살예방상담전화 1393 으로 연락하세요.")

        assert blocked is None
        assert "1393" in text


class TestJsonModeSafety:
    """JSON 모드 경로 — 이번에 막는 구멍."""

    def test_json_모드에서도_위험_표현을_탐지한다(self):
        payload = json.dumps({"meditation": "스스로 목숨을 끊는 생각이 들었다"}, ensure_ascii=False)

        text, blocked = app.apply_output_safety(payload, json_mode=True)

        assert blocked is not None, "JSON 값 안의 자해·자살 표현이 필터를 통과했다"

    def test_json_모드_차단시_유효한_json_을_돌려준다(self):
        payload = json.dumps({"meditation": "손목을 그었다"}, ensure_ascii=False)

        text, _ = app.apply_output_safety(payload, json_mode=True)

        # 평문 fallback 을 그대로 주면 호출자의 json.loads 가 깨진다.
        parsed = json.loads(text)
        assert parsed["safetyBlocked"] is True
        assert "1393" in parsed["text"]

    def test_json_모드_안전한_응답은_원본_그대로(self):
        payload = json.dumps({"meditation": "오늘은 쉬어도 괜찮습니다"}, ensure_ascii=False)

        text, blocked = app.apply_output_safety(payload, json_mode=True)

        assert blocked is None
        assert json.loads(text)["meditation"] == "오늘은 쉬어도 괜찮습니다"

    def test_평문_모드는_기존_fallback_을_유지한다(self):
        text, blocked = app.apply_output_safety("손목을 그었다", json_mode=False)

        assert blocked is not None
        assert text == app._SAFE_FALLBACK
