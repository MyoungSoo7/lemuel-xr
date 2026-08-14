"""한국어 발음형 변환(G2P) — XTTS 에 *철자* 가 아니라 *발음* 을 넘기기 위한 전처리.

## 왜 필요한가

Coqui TTS 0.22.0 의 XTTS 토크나이저는 한국어를 **모델에 그대로 넣지 않는다**. `lang == "ko"`
이면 `hangul_romanize` 의 academic 규칙으로 로마자화한 뒤 토큰화한다
(`TTS/tts/layers/xtts/tokenizer.py` 의 `preprocess_text`). 즉 모델이 보는 것은 한글이 아니라
로마자 문자열이다.

그런데 이 로마자화는 **철자 그대로** 옮긴다. 한국어는 철자와 발음이 크게 다른 언어라
(연음·ㅎ탈락·경음화·비음화) 결과가 한국어처럼 들리지 않는다. 프로덕션 파드에서 실측한 값:

    걸음도 -> geol-eumdo     |  발음형 거름도 -> geoleumdo
    않은   -> anh-eun        |  발음형 아는   -> aneun
    것이   -> geos-i         |  발음형 거시   -> geosi
    시작된다 -> sijagdoenda   |  발음형 시작뙨다 -> sijagttoenda

왼쪽이 지금 사용자가 듣고 있는 소리다. "외국인이 한국어를 철자대로 읽는" 느낌의 정체가 이것이다.

## 왜 우리가 로마자를 직접 만들지 않는가

우리가 개정로마자표기(ㄹ을 상황에 따라 r/l 로 쓰는)로 바꿔서 넘기고 싶어지지만, **그러면 안 된다.**
XTTS 가 학습한 한국어 표기는 academic 규칙(ㄹ은 언제나 `l`)이다. 다른 로마자 규칙을 넣으면
학습 분포 밖(out-of-distribution)의 입력이 된다. 그래서 로마자화는 XTTS 에게 그대로 맡기고,
우리는 **발음대로 쓴 한글**만 넘긴다.

## 무엇을 하지 않는가 (정직한 한계)

형태소 분석기를 쓰지 않는다(컨테이너에 mecab/JVM 을 넣지 않기 위해서다). 그래서 형태소 경계를
알아야 풀리는 규칙은 처리하지 못한다:

  - 실질형태소 앞의 대표음 연음: `겉옷`[거돋] → 이 모듈은 `거톧`
  - 사잇소리·ㄴ첨가: `색연필`[생년필], `물난리` 류
  - 한자어 ㄹ 뒤 경음화: `발전`[발쩐]
  - 관형사형 어미 뒤 경음화: `할 것`[할 껏]

또한 **띄어쓰기를 넘어서는 규칙은 적용하지 않는다.** 표준발음법은 구(句) 안에서도 연음을
인정하지만(`옷 안`[오단]), 그렇게 하면 TTS 가 단어 경계를 잃어 운율이 뭉개진다. 낭독체에서는
띄어쓰기를 지키는 편이 낫다.

이 한계들은 "안 고쳐진 부분"이지 "틀리게 고쳐진 부분"이 아니다. 처리하지 못하는 자리는
지금과 똑같이(=철자대로) 남는다.

근거: 국립국어원 표준 발음법(문교부 고시 제88-1호) 제8~12항, 제17~24항.
"""

# 초성 19 / 중성 21 / 종성 28 (종성 0 = 없음). 유니코드 한글 음절 = 0xAC00 + (초*21 + 중)*28 + 종
CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
JONG = ["", *"ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"]

_BASE = 0xAC00
_LAST = 0xD7A3

# 겹받침 → (앞, 뒤). 연음될 때 뒤 자음만 넘어가고 앞 자음은 남는다(표준발음법 14항: 닭이[달기]).
DOUBLE_JONG = {
    "ㄳ": ("ㄱ", "ㅅ"),
    "ㄵ": ("ㄴ", "ㅈ"),
    "ㄶ": ("ㄴ", "ㅎ"),
    "ㄺ": ("ㄹ", "ㄱ"),
    "ㄻ": ("ㄹ", "ㅁ"),
    "ㄼ": ("ㄹ", "ㅂ"),
    "ㄽ": ("ㄹ", "ㅅ"),
    "ㄾ": ("ㄹ", "ㅌ"),
    "ㄿ": ("ㄹ", "ㅍ"),
    "ㅀ": ("ㄹ", "ㅎ"),
    "ㅄ": ("ㅂ", "ㅅ"),
}

# 자음군 단순화(10~11항). 연음되지 못하고 남은 겹받침이 실제로 내는 하나의 소리.
CLUSTER_SIMPLIFY = {
    "ㄳ": "ㄱ", "ㄵ": "ㄴ", "ㄶ": "ㄴ", "ㄺ": "ㄱ", "ㄻ": "ㅁ", "ㄼ": "ㄹ",
    "ㄽ": "ㄹ", "ㄾ": "ㄹ", "ㄿ": "ㅂ", "ㅀ": "ㄹ", "ㅄ": "ㅂ",
}

# 음절의 끝소리 규칙 = 평파열음화(8~9항). 받침에서 실제로 나는 소리는 7개뿐(ㄱㄴㄷㄹㅁㅂㅇ).
NEUTRALIZE = {
    "ㄲ": "ㄱ", "ㅋ": "ㄱ",
    "ㅅ": "ㄷ", "ㅆ": "ㄷ", "ㅈ": "ㄷ", "ㅊ": "ㄷ", "ㅌ": "ㄷ", "ㅎ": "ㄷ",
    "ㅍ": "ㅂ",
}

# 격음화(12항). 받침 + 초성 ㅎ. 겹받침이면 *뒤* 자음이 ㅎ 과 합쳐진다(앉히다[안치다], 밝히다[발키다]).
JONG_PLUS_H = {
    "ㄱ": "ㅋ", "ㄲ": "ㅋ", "ㅋ": "ㅋ",
    "ㄷ": "ㅌ", "ㅅ": "ㅌ", "ㅆ": "ㅌ", "ㅌ": "ㅌ",
    "ㅂ": "ㅍ", "ㅍ": "ㅍ",
    "ㅈ": "ㅊ", "ㅊ": "ㅊ",
}
# 받침 ㅎ + 초성 (12항 1). ㅂ 은 목록에 없다 — 표준발음법이 ㄱ·ㄷ·ㅈ·ㅅ 만 규정한다.
H_PLUS_CHO = {"ㄱ": "ㅋ", "ㄷ": "ㅌ", "ㅈ": "ㅊ"}
# 받침 ㅎ 계열이 남기는 잔여 자음(ㅎ 만 사라진다).
H_RESIDUAL = {"ㅎ": "", "ㄶ": "ㄴ", "ㅀ": "ㄹ"}

NASALIZE = {"ㄱ": "ㅇ", "ㄷ": "ㄴ", "ㅂ": "ㅁ"}  # 비음화(18항)
TENSE = {"ㄱ": "ㄲ", "ㄷ": "ㄸ", "ㅂ": "ㅃ", "ㅅ": "ㅆ", "ㅈ": "ㅉ"}  # 경음화(23항)

PLOSIVES = ("ㄱ", "ㄷ", "ㅂ")

# 자음군 단순화의 예외(10항 다만). `밟-` 은 ㄼ 이 ㄹ 이 아니라 ㅂ 으로 난다: 밟다[밥따].
# 형태소 분석 없이 잡을 수 있는 유일한 방식이라 음절 글자 자체로 못 박는다.
CLUSTER_EXCEPTIONS = {"밟": "ㅂ"}


class _Syl:
    """가변 음절. 규칙들이 초성·종성을 제자리에서 바꾼다."""

    __slots__ = ("cho", "jung", "jong")

    def __init__(self, cho: str, jung: str, jong: str):
        self.cho = cho
        self.jung = jung
        self.jong = jong

    def compose(self) -> str:
        return chr(
            _BASE + (CHO.index(self.cho) * 21 + JUNG.index(self.jung)) * 28 + JONG.index(self.jong)
        )


def _decompose(ch: str):
    code = ord(ch)
    if not (_BASE <= code <= _LAST):
        return None
    rest = code - _BASE
    return _Syl(CHO[rest // 588], JUNG[(rest % 588) // 28], JONG[rest % 28])


def _pairs(tokens):
    """규칙이 적용될 수 있는 인접 음절 쌍만 내놓는다.

    공백·문장부호·숫자가 사이에 있으면 쌍이 아니다 — 그래서 띄어쓰기를 넘는 연음이
    저절로 막힌다(모듈 docstring 의 '무엇을 하지 않는가' 참조).
    """
    for i in range(len(tokens) - 1):
        left, right = tokens[i], tokens[i + 1]
        if isinstance(left, _Syl) and isinstance(right, _Syl):
            yield left, right


def _split_jong(jong: str):
    """받침을 (남는 것, 넘어가는 것) 으로 나눈다. 홑받침이면 통째로 넘어간다."""
    return DOUBLE_JONG.get(jong, ("", jong))


def to_spoken(text: str) -> str:
    """철자대로 쓴 한글을 발음대로 쓴 한글로 바꾼다. 한글이 아닌 문자는 그대로 둔다.

    >>> to_spoken("걸음도 멈추지 않은 것이")
    '거름도 멈추지 아는 거시'
    """
    tokens = [_decompose(ch) or ch for ch in text]

    # 1. ㅎ 관련(12항). 연음보다 먼저다 — `않은` 은 ㅎ 이 먼저 빠져야 ㄴ 이 넘어간다(아는).
    for p, n in _pairs(tokens):
        if p.jong in H_RESIDUAL:
            residual = H_RESIDUAL[p.jong]
            if n.cho in H_PLUS_CHO:
                n.cho = H_PLUS_CHO[n.cho]
                p.jong = residual
            elif n.cho == "ㅅ":
                n.cho = "ㅆ"
                p.jong = residual
            elif n.cho == "ㄴ":
                # 놓는[논는]·않는[안는]·뚫는[뚤는→뚤른]. ㅎ 은 사라지고 남는 소리가 받침이 된다.
                p.jong = "ㄴ" if p.jong in ("ㅎ", "ㄶ") else "ㄹ"
            elif n.cho == "ㅇ":
                p.jong = residual  # ㅎ 탈락: 좋은[조은], 않은[안은], 싫어[실어]
        elif n.cho == "ㅎ" and p.jong:
            first, second = _split_jong(p.jong)
            if second in JONG_PLUS_H:
                n.cho = JONG_PLUS_H[second]
                p.jong = first

    # 2. 연음(13~14항). 뒤 음절이 모음으로 시작할 때만.
    for p, n in _pairs(tokens):
        if p.jong and p.jong != "ㅇ" and n.cho == "ㅇ":
            p.jong, n.cho = _split_jong(p.jong)

    # 3. 자음군 단순화(10~11항) — 연음으로 넘어가지 못하고 남은 겹받침.
    for tok in tokens:
        if isinstance(tok, _Syl) and tok.jong in CLUSTER_SIMPLIFY:
            tok.jong = CLUSTER_EXCEPTIONS.get(tok.compose(), CLUSTER_SIMPLIFY[tok.jong])

    # 4. 음절의 끝소리 규칙(8~9항).
    for tok in tokens:
        if isinstance(tok, _Syl) and tok.jong in NEUTRALIZE:
            tok.jong = NEUTRALIZE[tok.jong]

    # 5. 비음화(18~19항)·유음화(20항). 왼쪽부터 훑는다 — 앞 쌍의 결과가 뒤 쌍의 입력이 된다.
    for p, n in _pairs(tokens):
        if not p.jong or not n.cho:
            continue
        if n.cho == "ㄹ":
            if p.jong in ("ㅁ", "ㅇ"):
                n.cho = "ㄴ"  # 종로[종노], 침략[침냑]
            elif p.jong == "ㄴ":
                p.jong = "ㄹ"  # 유음화: 신라[실라]
            elif p.jong in PLOSIVES:
                p.jong = NASALIZE[p.jong]  # 독립[동닙], 협력[혐녁]
                n.cho = "ㄴ"
        elif n.cho in ("ㄴ", "ㅁ"):
            if p.jong in PLOSIVES:
                p.jong = NASALIZE[p.jong]  # 국물[궁물], 믿는[민는], 없는[엄는]
            elif p.jong == "ㄹ" and n.cho == "ㄴ":
                n.cho = "ㄹ"  # 유음화: 칼날[칼랄]

    # 6. 경음화(23항). 평파열음 받침 뒤. 비음화를 먼저 돌렸으므로 `국물` 의 ㄱ 은 여기 안 온다.
    for p, n in _pairs(tokens):
        if p.jong in PLOSIVES and n.cho in TENSE:
            n.cho = TENSE[n.cho]

    return "".join(t.compose() if isinstance(t, _Syl) else t for t in tokens)
