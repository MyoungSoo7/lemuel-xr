import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useTtsNarration } from "./useTtsNarration";
import {
  pollTtsJob,
  synthesizeTts,
  type SynthesizeTtsResponse,
} from "@/lib/api/tts";
import { TTS_MAX_CHARS } from "@/lib/tts/splitForTts";

/**
 * 상한을 넘되 문단 경계가 하나 있는 본문 — 정확히 두 조각으로 갈린다.
 *
 * 길이를 [TTS_MAX_CHARS] 에서 끌어온다. 상수를 조정할 때마다 조각 수가 달라져
 * 이 파일의 호출 횟수 기대값이 우수수 깨지는 걸 막는다 (2026-08-15, 300 → 280
 * 으로 내리다 실제로 겪었다).
 */
const 두조각본문 = `${"가".repeat(TTS_MAX_CHARS)}\n\n${"나".repeat(TTS_MAX_CHARS)}`;

vi.mock("@/lib/api/tts", () => ({
  synthesizeTts: vi.fn(),
  pollTtsJob: vi.fn(),
}));

const mockSynthesize = vi.mocked(synthesizeTts);
const mockPoll = vi.mocked(pollTtsJob);

/**
 * TTS 나레이션 훅.
 *
 * 이 훅의 계약은 "잘 들린다" 가 아니라 **"안 들려도 앱이 안 깨진다"** 다.
 * 사이드카가 없는 로컬/장애 상황이 정상 경로에 포함돼 있으므로, 실패가 조용히
 * unavailable 로 떨어지는지를 중심으로 잰다. autoplay 금지(사용자 클릭 없이는
 * 절대 재생 안 함)도 여기서 지킨다.
 */

/** playUrl 안에서 만들어지는 Audio 를 붙잡아 ended/error 이벤트를 흘려 넣는다. */
const 만들어진오디오: HTMLAudioElement[] = [];
const OriginalAudio = window.Audio;

function 재생스텁() {
  return window.HTMLMediaElement.prototype.play as unknown as ReturnType<
    typeof vi.fn
  >;
}

beforeEach(() => {
  만들어진오디오.length = 0;
  mockSynthesize.mockReset();
  mockPoll.mockReset();
  vi.stubGlobal("Audio", function AudioSpy(src?: string) {
    const a = new OriginalAudio(src);
    만들어진오디오.push(a);
    return a;
  } as unknown as typeof Audio);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

/** 캐시 히트 — 폴링 없이 바로 재생 가능한 응답. */
function 응답(audioUrl: string): SynthesizeTtsResponse {
  return {
    status: "ready",
    audioUrl,
    durationMs: 1200,
    cached: false,
    jobId: null,
  };
}

/** 202 — 서버가 워커에 넘겼고 jobId 로 폴링해야 하는 응답. */
function 대기중(jobId: string): SynthesizeTtsResponse {
  return {
    status: "pending",
    audioUrl: null,
    durationMs: null,
    cached: false,
    jobId,
  };
}

function 상태만(
  status: SynthesizeTtsResponse["status"],
): SynthesizeTtsResponse {
  return {
    status,
    audioUrl: null,
    durationMs: null,
    cached: false,
    jobId: null,
  };
}

describe("useTtsNarration — 정상 경로", () => {
  it("초기 상태는 idle 이고 사용 가능하다", () => {
    const { result } = renderHook(() => useTtsNarration());
    expect(result.current.status).toBe("idle");
    expect(result.current.unavailable).toBe(false);
    // 마운트만으로 합성이 일어나면 그게 곧 autoplay 다.
    expect(mockSynthesize).not.toHaveBeenCalled();
  });

  it("toggle 하면 합성 후 재생 상태가 된다", async () => {
    mockSynthesize.mockResolvedValue(응답("https://cdn/x.mp3"));
    const { result } = renderHook(() => useTtsNarration("voice-ko-1"));

    await act(async () => {
      await result.current.toggle("  여호와는 나의 목자시니  ");
    });

    // 앞뒤 공백은 잘라서 보낸다 — 같은 문장이 공백 차이로 캐시를 비켜 가면
    // 사이드카를 불필요하게 두 번 때린다.
    expect(mockSynthesize).toHaveBeenCalledWith(
      "여호와는 나의 목자시니",
      "voice-ko-1",
    );
    await waitFor(() => expect(result.current.status).toBe("playing"));
    expect(만들어진오디오[0].getAttribute("src")).toBe("https://cdn/x.mp3");
    expect(재생스텁()).toHaveBeenCalled();
  });

  it("재생 중 다시 toggle 하면 정지한다", async () => {
    mockSynthesize.mockResolvedValue(응답("https://cdn/x.mp3"));
    const { result } = renderHook(() => useTtsNarration());

    await act(async () => {
      await result.current.toggle("본문");
    });
    await waitFor(() => expect(result.current.status).toBe("playing"));

    await act(async () => {
      await result.current.toggle("본문");
    });

    expect(result.current.status).toBe("idle");
    expect(만들어진오디오[0].pause).toHaveBeenCalled();
    // 정지는 재합성이 아니다.
    expect(mockSynthesize).toHaveBeenCalledTimes(1);
  });

  it("같은 텍스트를 다시 들으면 재합성하지 않고 캐시된 URL 로 재생한다", async () => {
    mockSynthesize.mockResolvedValue(응답("https://cdn/x.mp3"));
    const { result } = renderHook(() => useTtsNarration());

    await act(async () => {
      await result.current.toggle("본문");
    });
    await waitFor(() => expect(result.current.status).toBe("playing"));
    act(() => result.current.stop());

    await act(async () => {
      await result.current.toggle("본문");
    });

    expect(mockSynthesize).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(result.current.status).toBe("playing"));
    // Audio 요소는 재사용된다 — 매번 새로 만들면 이전 재생이 안 멈춘다.
    expect(만들어진오디오).toHaveLength(1);
  });

  it("텍스트가 바뀌면 다시 합성한다", async () => {
    mockSynthesize
      .mockResolvedValueOnce(응답("https://cdn/a.mp3"))
      .mockResolvedValueOnce(응답("https://cdn/b.mp3"));
    const { result } = renderHook(() => useTtsNarration());

    await act(async () => {
      await result.current.toggle("첫 문장");
    });
    act(() => result.current.stop());
    await act(async () => {
      await result.current.toggle("다른 문장");
    });

    expect(mockSynthesize).toHaveBeenCalledTimes(2);
    expect(만들어진오디오[0].getAttribute("src")).toBe("https://cdn/b.mp3");
  });

  it("재생이 끝나면 스스로 idle 로 돌아온다", async () => {
    mockSynthesize.mockResolvedValue(응답("https://cdn/x.mp3"));
    const { result } = renderHook(() => useTtsNarration());
    await act(async () => {
      await result.current.toggle("본문");
    });
    await waitFor(() => expect(result.current.status).toBe("playing"));

    // 끝났는데 '정지' 버튼으로 남아 있으면 다음 클릭이 재생이 아니라 정지가 된다.
    act(() => {
      만들어진오디오[0].dispatchEvent(new Event("ended"));
    });
    expect(result.current.status).toBe("idle");
  });

  it("재생 중 디코드 오류가 나도 에러가 아니라 idle 로 조용히 떨어진다", async () => {
    mockSynthesize.mockResolvedValue(응답("https://cdn/x.mp3"));
    const { result } = renderHook(() => useTtsNarration());
    await act(async () => {
      await result.current.toggle("본문");
    });
    await waitFor(() => expect(result.current.status).toBe("playing"));

    act(() => {
      만들어진오디오[0].dispatchEvent(new Event("error"));
    });
    expect(result.current.status).toBe("idle");
  });
});

describe("useTtsNarration — 빈 입력·중복 호출", () => {
  it.each([
    ["", "빈 문자열"],
    ["   ", "공백뿐"],
  ])("%s(%s) 은 아무것도 하지 않는다", async (text) => {
    const { result } = renderHook(() => useTtsNarration());
    await act(async () => {
      await result.current.toggle(text);
    });
    expect(mockSynthesize).not.toHaveBeenCalled();
    expect(result.current.status).toBe("idle");
  });

  it("합성 대기 중 다시 눌러도 요청이 겹치지 않는다", async () => {
    let 해결: (v: SynthesizeTtsResponse) => void = () => {};
    mockSynthesize.mockReturnValue(
      new Promise((res) => {
        해결 = res;
      }),
    );
    const { result } = renderHook(() => useTtsNarration());

    let 첫요청: Promise<void>;
    act(() => {
      첫요청 = result.current.toggle("본문");
    });
    await waitFor(() => expect(result.current.status).toBe("loading"));

    await act(async () => {
      await result.current.toggle("본문");
    });
    // 연타로 사이드카를 여러 번 때리지 않는다.
    expect(mockSynthesize).toHaveBeenCalledTimes(1);

    await act(async () => {
      해결(응답("https://cdn/x.mp3"));
      await 첫요청!;
    });
    await waitFor(() => expect(result.current.status).toBe("playing"));
  });
});

/**
 * 캐시에 없는 문장은 202 + jobId 로 오고, 훅이 2초 주기로 폴링한다.
 * 실제로 기다릴 수는 없으니 가짜 타이머로 주기를 밀어 준다 —
 * `advanceTimersByTimeAsync` 는 타이머와 함께 마이크로태스크(폴링 응답)까지 흘려 준다.
 */
describe("useTtsNarration — 비동기 합성 폴링", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  /**
   * 가짜 시계를 ms 만큼 밀면서 그 사이 떨어진 응답까지 흘려 준다.
   * 0 을 주면 "타이머는 안 건드리고 지금 밀린 것만 정리" 가 된다.
   *
   * 주의: 진행 중인 toggle() 프라미스를 이 안에서 await 하면 안 된다. 그 프라미스는
   * 폴링이 끝나야 풀리는데, 폴링은 여기서 시계를 밀어야 진행된다 — 서로 기다리다
   * 테스트가 5초 타임아웃으로 죽는다. toggle 프라미스는 *마지막에만* await 한다.
   */
  async function 흘리기(ms: number) {
    await act(async () => {
      await vi.advanceTimersByTimeAsync(ms);
    });
  }

  /** 폴링 한 주기. */
  const 한주기 = () => 흘리기(2_000);

  it("202 를 받으면 synthesizing 이 되고 ready 가 될 때까지 폴링한다", async () => {
    mockSynthesize.mockResolvedValue(대기중("job-1"));
    mockPoll
      .mockResolvedValueOnce(상태만("pending"))
      .mockResolvedValueOnce(응답("https://cdn/x.mp3"));
    const { result } = renderHook(() => useTtsNarration());

    let 요청: Promise<void>;
    act(() => {
      요청 = result.current.toggle("본문");
    });
    await 흘리기(0); // submit 응답(202)까지만 흘린다

    // 합성을 기다리는 동안은 loading 이 아니라 synthesizing — 버튼이 "준비 중" 을 띄운다.
    expect(result.current.status).toBe("synthesizing");
    expect(result.current.synthesizing).toBe(true);
    expect(만들어진오디오).toHaveLength(0);

    await 한주기(); // 1회차 — 아직 pending
    expect(result.current.status).toBe("synthesizing");

    await 한주기(); // 2회차 — ready
    await act(async () => {
      await 요청!;
    });
    expect(result.current.status).toBe("playing");
    expect(만들어진오디오[0].getAttribute("src")).toBe("https://cdn/x.mp3");
  });

  it("job 이 failed 로 끝나면 조용히 unavailable", async () => {
    mockSynthesize.mockResolvedValue(대기중("job-1"));
    mockPoll.mockResolvedValue(상태만("failed"));
    const { result } = renderHook(() => useTtsNarration());

    let 요청: Promise<void>;
    act(() => {
      요청 = result.current.toggle("본문");
    });
    await 흘리기(0); // submit 응답(202)까지만 흘린다
    await 한주기();
    await act(async () => {
      await 요청!;
    });

    expect(result.current.status).toBe("unavailable");
    expect(만들어진오디오).toHaveLength(0);
  });

  it("폴링이 한 번 실패해도 포기하지 않고 다음 주기에 다시 묻는다", async () => {
    // 순간적인 네트워크 오류로 다 된 합성을 버리면 사용자는 처음부터 다시 기다려야 한다.
    mockSynthesize.mockResolvedValue(대기중("job-1"));
    mockPoll
      .mockRejectedValueOnce(new Error("네트워크 순간 오류"))
      .mockResolvedValueOnce(응답("https://cdn/x.mp3"));
    const { result } = renderHook(() => useTtsNarration());

    let 요청: Promise<void>;
    act(() => {
      요청 = result.current.toggle("본문");
    });
    await 흘리기(0); // submit 응답(202)까지만 흘린다
    await 한주기();
    expect(result.current.status).toBe("synthesizing");

    await 한주기();
    await act(async () => {
      await 요청!;
    });
    expect(result.current.status).toBe("playing");
  });

  it("기다리는 중 stop() 하면 폴링을 접고 늦게 끝난 오디오도 틀지 않는다", async () => {
    mockSynthesize.mockResolvedValue(대기중("job-1"));
    mockPoll.mockResolvedValue(응답("https://cdn/x.mp3"));
    const { result } = renderHook(() => useTtsNarration());

    let 요청: Promise<void>;
    act(() => {
      요청 = result.current.toggle("본문");
    });
    await 흘리기(0); // submit 응답(202)까지만 흘린다
    expect(result.current.status).toBe("synthesizing");

    act(() => result.current.stop());
    expect(result.current.status).toBe("idle");

    await 한주기();
    await act(async () => {
      await 요청!;
    });

    // 취소했는데 뒤늦게 소리가 나오면 사용자는 원인을 알 수 없다.
    expect(result.current.status).toBe("idle");
    expect(만들어진오디오).toHaveLength(0);
  });

  it("합성을 기다리는 동안 다시 눌러도 요청이 겹치지 않는다", async () => {
    mockSynthesize.mockResolvedValue(대기중("job-1"));
    mockPoll.mockResolvedValue(상태만("pending"));
    const { result } = renderHook(() => useTtsNarration());

    act(() => {
      void result.current.toggle("본문");
    });
    await 흘리기(0);
    expect(result.current.status).toBe("synthesizing");

    await act(async () => {
      await result.current.toggle("본문");
    });

    expect(mockSynthesize).toHaveBeenCalledTimes(1);
  });

  it("큐가 가득 차면(busy) 버튼을 죽이지 않고 idle 로 되돌린다", async () => {
    // 429 는 고장이 아니라 "지금은 바쁨" 이다. unavailable 로 두면 다시 시도할 방법이 없다.
    mockSynthesize.mockResolvedValue({
      ...상태만("busy"),
      retryAfterSeconds: 40,
    });
    const { result } = renderHook(() => useTtsNarration());

    // busy 는 폴링으로 안 넘어가므로 toggle 이 바로 끝난다 — 그냥 await 해도 안전하다.
    await act(async () => {
      await result.current.toggle("본문");
    });

    expect(result.current.status).toBe("idle");
    expect(result.current.unavailable).toBe(false);
    expect(mockPoll).not.toHaveBeenCalled();
  });
});

describe("useTtsNarration — graceful degradation", () => {
  it("합성이 실패하면 에러를 던지지 않고 unavailable 로 넘어간다", async () => {
    mockSynthesize.mockRejectedValue(new Error("502 Bad Gateway"));
    const { result } = renderHook(() => useTtsNarration());

    // 던지면 호출한 컴포넌트가 통째로 죽는다. 오디오는 보조 기능이다.
    await act(async () => {
      await expect(result.current.toggle("본문")).resolves.toBeUndefined();
    });

    expect(result.current.status).toBe("unavailable");
    expect(result.current.unavailable).toBe(true);
    expect(만들어진오디오).toHaveLength(0);
  });

  it("ready 라면서 audioUrl 이 없으면 사용 불가로 본다", async () => {
    // 모순 응답. 여기서 걸러 두지 않으면 빈 src 로 Audio 를 만들어 디코드 오류로 샌다.
    mockSynthesize.mockResolvedValue({ ...상태만("ready"), audioUrl: "" });
    const { result } = renderHook(() => useTtsNarration());
    await act(async () => {
      await result.current.toggle("본문");
    });
    expect(result.current.status).toBe("unavailable");
  });

  it("pending 인데 jobId 가 없으면 폴링하지 않고 사용 불가로 본다", async () => {
    mockSynthesize.mockResolvedValue(상태만("pending"));
    const { result } = renderHook(() => useTtsNarration());
    await act(async () => {
      await result.current.toggle("본문");
    });
    expect(result.current.status).toBe("unavailable");
    expect(mockPoll).not.toHaveBeenCalled();
  });

  it("사용 불가로 판정된 뒤에는 stop() 이 상태를 되돌리지 않는다", async () => {
    // 되돌리면 버튼이 다시 활성화되고, 사용자는 눌러도 아무 일 없는 버튼을 반복해 누른다.
    mockSynthesize.mockRejectedValue(new Error("down"));
    const { result } = renderHook(() => useTtsNarration());
    await act(async () => {
      await result.current.toggle("본문");
    });

    act(() => result.current.stop());
    expect(result.current.status).toBe("unavailable");
  });

  it("브라우저가 재생을 막으면(autoplay 정책) 조용히 idle 로 돌아온다", async () => {
    재생스텁().mockImplementationOnce(() =>
      Promise.reject(new DOMException("NotAllowedError")),
    );
    mockSynthesize.mockResolvedValue(응답("https://cdn/x.mp3"));
    const { result } = renderHook(() => useTtsNarration());

    await act(async () => {
      await result.current.toggle("본문");
    });

    await waitFor(() => expect(result.current.status).toBe("idle"));
    expect(result.current.unavailable).toBe(false);
  });

  it("언마운트하면 재생을 멈추고 소스를 비운다", async () => {
    mockSynthesize.mockResolvedValue(응답("https://cdn/x.mp3"));
    const { result, unmount } = renderHook(() => useTtsNarration());
    await act(async () => {
      await result.current.toggle("본문");
    });
    await waitFor(() => expect(result.current.status).toBe("playing"));

    const audio = 만들어진오디오[0];
    unmount();

    // 화면을 떠났는데 나레이션이 계속 흐르면 사용자는 끌 방법이 없다.
    expect(audio.pause).toHaveBeenCalled();
    expect(audio.getAttribute("src")).toBe("");
  });

  it("언마운트 뒤 늦게 도착한 응답은 상태를 건드리지 않는다", async () => {
    let 해결: (v: SynthesizeTtsResponse) => void = () => {};
    mockSynthesize.mockReturnValue(
      new Promise((res) => {
        해결 = res;
      }),
    );
    const { result, unmount } = renderHook(() => useTtsNarration());

    let 요청: Promise<void>;
    act(() => {
      요청 = result.current.toggle("본문");
    });
    unmount();

    await act(async () => {
      해결(응답("https://cdn/x.mp3"));
      await 요청!;
    });

    // 언마운트된 훅에 setState 하면 React 경고가 나고, 재생도 유령처럼 시작된다.
    expect(만들어진오디오).toHaveLength(0);
  });
});

/**
 * 긴 본문(상한 초과) 은 조각으로 나뉘어 *차례로* 합성되고 이어서 재생된다.
 *
 * 실물은 모세 3씬 echo 다 — 카드를 내려놓을수록 응답이 붙어 최대 716자가 되고,
 * 그대로 보내면 백엔드 상한(@Size 500)에 걸려 400 이 났다. 버튼은 onUnavailable="hide"
 * 라 조용히 사라졌으므로, 카드를 많이 내려놓은 사람만 소리를 못 들었다.
 */
describe("useTtsNarration — 상한을 넘는 본문", () => {

  it("조각마다 합성하고 이어 붙여 한 번의 낭독으로 들려준다", async () => {
    mockSynthesize
      .mockResolvedValueOnce(응답("https://cdn/1.mp3"))
      .mockResolvedValueOnce(응답("https://cdn/2.mp3"));
    const { result } = renderHook(() => useTtsNarration());

    await act(async () => {
      await result.current.toggle(두조각본문);
    });
    await waitFor(() => expect(result.current.status).toBe("playing"));

    // 보낸 조각은 둘 다 상한 이하여야 한다 — 이게 400 을 막는 지점이다.
    expect(mockSynthesize).toHaveBeenCalledTimes(2);
    for (const [보낸글] of mockSynthesize.mock.calls) {
      expect(보낸글.length).toBeLessThanOrEqual(TTS_MAX_CHARS);
    }
    expect(만들어진오디오[0].getAttribute("src")).toBe("https://cdn/1.mp3");

    // 첫 조각이 끝나면 idle 이 아니라 다음 조각으로 이어져야 한다.
    act(() => {
      만들어진오디오[0].dispatchEvent(new Event("ended"));
    });
    await waitFor(() =>
      expect(만들어진오디오[0].getAttribute("src")).toBe("https://cdn/2.mp3"),
    );
    expect(result.current.status).toBe("playing");

    // 마지막 조각까지 끝나야 idle 이다.
    act(() => {
      만들어진오디오[0].dispatchEvent(new Event("ended"));
    });
    expect(result.current.status).toBe("idle");
  });

  it("상한 이하인 본문은 쪼개지 않고 원문 그대로 보낸다", async () => {
    // 캐시 키가 sha256(본문) 이라, 짧은 글까지 쪼개면 이미 데워 둔 캐시를 전부
    // 놓쳐 첫 재생이 다시 수십 초가 된다.
    const 짧은본문 = "여호와는 나의 목자시니\n\n내게 부족함이 없으리로다.";
    mockSynthesize.mockResolvedValue(응답("https://cdn/x.mp3"));
    const { result } = renderHook(() => useTtsNarration());

    await act(async () => {
      await result.current.toggle(짧은본문);
    });

    expect(mockSynthesize).toHaveBeenCalledTimes(1);
    expect(mockSynthesize).toHaveBeenCalledWith(짧은본문, undefined);
  });

  it("첫 조각부터 실패하면 아무 소리도 내지 않고 조용히 unavailable", async () => {
    mockSynthesize.mockResolvedValueOnce(상태만("failed"));
    const { result } = renderHook(() => useTtsNarration());

    await act(async () => {
      await result.current.toggle(두조각본문);
    });

    expect(result.current.status).toBe("unavailable");
    expect(만들어진오디오).toHaveLength(0);
    // 첫 조각이 죽었으면 뒷조각을 물어볼 이유가 없다.
    expect(mockSynthesize).toHaveBeenCalledTimes(1);
  });

  it("뒤 조각이 실패해도 들리던 조각은 끝까지 들려주고 버튼은 살려 둔다", async () => {
    /*
      먼저 틀고 뒤를 만드는 구조라, 뒷조각이 죽는 순간엔 이미 소리가 나고 있다.
      그때 unavailable 로 내리면 *들리는 중에* 버튼이 죽고 다시 들을 길도 사라진다.
      남은 조각만 포기하고 들리던 데까지는 끝맺는다.

      대가는 분명하다 — 낭독이 조각 경계(문단 경계)에서 잘린 채로 끝난다.
      "전부 아니면 전무" 를 첫 소리까지의 시간과 맞바꾼 것이다.
    */
    mockSynthesize
      .mockResolvedValueOnce(응답("https://cdn/1.mp3"))
      .mockResolvedValueOnce(상태만("failed"));
    const { result } = renderHook(() => useTtsNarration());

    await act(async () => {
      await result.current.toggle(두조각본문);
    });

    expect(result.current.status).toBe("playing");
    expect(만들어진오디오[0].getAttribute("src")).toBe("https://cdn/1.mp3");
    expect(result.current.unavailable).toBe(false);

    // 들리던 조각이 끝나면 조용히 마무리된다 — 오지 않을 조각을 기다리지 않는다.
    act(() => {
      만들어진오디오[0].dispatchEvent(new Event("ended"));
    });
    expect(result.current.status).toBe("idle");
  });

  it("반쪽만 만들어진 낭독은 캐시하지 않는다", async () => {
    // 캐시해 버리면 다시 들을 때 뒷부분이 영영 안 나온다 — 재합성 기회를 잃는다.
    mockSynthesize
      .mockResolvedValueOnce(응답("https://cdn/1.mp3"))
      .mockResolvedValueOnce(상태만("failed"));
    const { result } = renderHook(() => useTtsNarration());

    await act(async () => {
      await result.current.toggle(두조각본문);
    });
    act(() => {
      만들어진오디오[0].dispatchEvent(new Event("ended"));
    });

    mockSynthesize
      .mockResolvedValueOnce(응답("https://cdn/1.mp3"))
      .mockResolvedValueOnce(응답("https://cdn/2.mp3"));
    await act(async () => {
      await result.current.toggle(두조각본문);
    });

    // 두 번째 시도에서 두 조각을 다시 물어봤다 (2 + 2).
    expect(mockSynthesize).toHaveBeenCalledTimes(4);
    expect(result.current.status).toBe("playing");
  });

  it("조각을 기다리는 중 stop() 하면 남은 조각을 더 합성하지 않는다", async () => {
    mockSynthesize.mockResolvedValueOnce(대기중("job-1"));
    mockPoll.mockResolvedValue({
      status: "pending",
      audioUrl: null,
      durationMs: null,
      cached: false,
      jobId: "job-1",
    });
    const { result } = renderHook(() => useTtsNarration());

    let 요청: Promise<void>;
    act(() => {
      요청 = result.current.toggle(두조각본문);
    });
    await waitFor(() => expect(result.current.status).toBe("synthesizing"));

    act(() => {
      result.current.stop();
    });
    await act(async () => {
      await 요청!;
    });

    // 첫 조각 한 번만 요청됐고, 둘째 조각으로 넘어가지 않았다.
    expect(mockSynthesize).toHaveBeenCalledTimes(1);
    expect(만들어진오디오).toHaveLength(0);
  });

  it("정지하면 남은 조각도 버린다 — 다음 조각이 유령처럼 울리지 않는다", async () => {
    mockSynthesize
      .mockResolvedValueOnce(응답("https://cdn/1.mp3"))
      .mockResolvedValueOnce(응답("https://cdn/2.mp3"));
    const { result } = renderHook(() => useTtsNarration());

    await act(async () => {
      await result.current.toggle(두조각본문);
    });
    await waitFor(() => expect(result.current.status).toBe("playing"));

    act(() => {
      result.current.stop();
    });
    expect(result.current.status).toBe("idle");

    // 정지 뒤 늦게 도착한 ended 가 다음 조각을 틀어서는 안 된다.
    act(() => {
      만들어진오디오[0].dispatchEvent(new Event("ended"));
    });
    expect(만들어진오디오[0].getAttribute("src")).toBe("https://cdn/1.mp3");
    expect(result.current.status).toBe("idle");
  });

  it("같은 긴 본문을 다시 들으면 재합성 없이 조각들을 그대로 다시 재생한다", async () => {
    mockSynthesize
      .mockResolvedValueOnce(응답("https://cdn/1.mp3"))
      .mockResolvedValueOnce(응답("https://cdn/2.mp3"));
    const { result } = renderHook(() => useTtsNarration());

    await act(async () => {
      await result.current.toggle(두조각본문);
    });
    await waitFor(() => expect(result.current.status).toBe("playing"));
    act(() => {
      result.current.stop();
    });

    await act(async () => {
      await result.current.toggle(두조각본문);
    });

    expect(mockSynthesize).toHaveBeenCalledTimes(2); // 재합성 없음
    expect(만들어진오디오[0].getAttribute("src")).toBe("https://cdn/1.mp3");
  });
});

/**
 * 첫 조각이 나오면 나머지를 기다리지 않고 바로 튼다.
 *
 * 조각을 전부 만든 뒤에 재생하면 캐시가 빈 716자는 첫 소리까지 9분이 걸린다
 * (조각1 372초 + 조각2 181초, CPU-only XTTS-v2 실측). 먼저 틀고 뒤에서 이어
 * 만들면 6분으로 줄어든다.
 *
 * 대신 재생이 합성을 앞지르는 구간이 생긴다 — 조각1 오디오 85초 < 조각2 합성
 * 181초라 캐시가 비면 반드시 벌어진다. 그 구간이 "낭독 끝" 으로 보이면 안 된다.
 */
describe("useTtsNarration — 먼저 틀고 뒤를 만든다", () => {

  /** 아직 안 끝난 조각2 합성 — 손으로 풀어 준다. */
  function 매달린조각() {
    let 해결: (v: SynthesizeTtsResponse) => void = () => {};
    const p = new Promise<SynthesizeTtsResponse>((res) => {
      해결 = res;
    });
    return { p, 해결: (v: SynthesizeTtsResponse) => 해결(v) };
  }

  it("조각2 가 아직 안 끝났어도 조각1 은 이미 흐른다", async () => {
    const 조각2 = 매달린조각();
    mockSynthesize
      .mockResolvedValueOnce(응답("https://cdn/1.mp3"))
      .mockReturnValueOnce(조각2.p);
    const { result } = renderHook(() => useTtsNarration());

    let 요청: Promise<void>;
    act(() => {
      요청 = result.current.toggle(두조각본문);
    });

    // 여기가 이 변경의 전부다 — 조각2 는 아직 만들어지지도 않았는데 소리가 난다.
    await waitFor(() => expect(result.current.status).toBe("playing"));
    expect(만들어진오디오[0].getAttribute("src")).toBe("https://cdn/1.mp3");

    await act(async () => {
      조각2.해결(응답("https://cdn/2.mp3"));
      await 요청!;
    });
  });

  it("재생이 조각을 앞지르면 idle 이 아니라 기다렸다가 이어 붙인다", async () => {
    const 조각2 = 매달린조각();
    mockSynthesize
      .mockResolvedValueOnce(응답("https://cdn/1.mp3"))
      .mockReturnValueOnce(조각2.p);
    const { result } = renderHook(() => useTtsNarration());

    let 요청: Promise<void>;
    act(() => {
      요청 = result.current.toggle(두조각본문);
    });
    await waitFor(() => expect(result.current.status).toBe("playing"));

    // 조각1 이 끝났는데 조각2 는 아직이다.
    act(() => {
      만들어진오디오[0].dispatchEvent(new Event("ended"));
    });
    // idle 로 떨어지면 사용자는 낭독이 중간에 잘린 줄 안다.
    expect(result.current.status).toBe("synthesizing");

    await act(async () => {
      조각2.해결(응답("https://cdn/2.mp3"));
      await 요청!;
    });

    // 큐를 거치지 않고 곧장 이어진다.
    await waitFor(() =>
      expect(만들어진오디오[0].getAttribute("src")).toBe("https://cdn/2.mp3"),
    );
    expect(result.current.status).toBe("playing");

    act(() => {
      만들어진오디오[0].dispatchEvent(new Event("ended"));
    });
    expect(result.current.status).toBe("idle");
  });

  it("기다리던 조각이 끝내 오지 않으면 조용히 idle 로 풀린다", async () => {
    // 뒷조각이 실패했는데 재생은 그 조각을 기다리며 서 있는 경우.
    // 풀어 주지 않으면 버튼이 "준비 중…" 인 채로 영원히 잠긴다.
    const 조각2 = 매달린조각();
    mockSynthesize
      .mockResolvedValueOnce(응답("https://cdn/1.mp3"))
      .mockReturnValueOnce(조각2.p);
    const { result } = renderHook(() => useTtsNarration());

    let 요청: Promise<void>;
    act(() => {
      요청 = result.current.toggle(두조각본문);
    });
    await waitFor(() => expect(result.current.status).toBe("playing"));
    act(() => {
      만들어진오디오[0].dispatchEvent(new Event("ended"));
    });
    expect(result.current.status).toBe("synthesizing");

    await act(async () => {
      조각2.해결(상태만("failed"));
      await 요청!;
    });

    expect(result.current.status).toBe("idle");
    expect(result.current.unavailable).toBe(false);
    expect(만들어진오디오[0].getAttribute("src")).toBe("https://cdn/1.mp3");
  });

  it("정지하면 뒤에서 만들던 조각까지 함께 접는다", async () => {
    const 조각2 = 매달린조각();
    mockSynthesize
      .mockResolvedValueOnce(응답("https://cdn/1.mp3"))
      .mockReturnValueOnce(조각2.p);
    const { result } = renderHook(() => useTtsNarration());

    let 요청: Promise<void>;
    act(() => {
      요청 = result.current.toggle(두조각본문);
    });
    await waitFor(() => expect(result.current.status).toBe("playing"));

    act(() => {
      result.current.stop();
    });
    expect(result.current.status).toBe("idle");

    // 정지 뒤 늦게 도착한 조각2 가 혼자 울리면 안 된다.
    await act(async () => {
      조각2.해결(응답("https://cdn/2.mp3"));
      await 요청!;
    });
    expect(만들어진오디오[0].getAttribute("src")).toBe("https://cdn/1.mp3");
    expect(result.current.status).toBe("idle");
  });

  it("뒤 조각에서 네트워크가 끊겨도 재생 중이면 버튼을 죽이지 않는다", async () => {
    // 소리가 나고 있다는 건 오디오 경로가 멀쩡하다는 뜻이다. 그때 unavailable 은
    // 거짓말이고, 버튼이 사라지면(onUnavailable="hide") 다시 들을 수도 없다.
    mockSynthesize
      .mockResolvedValueOnce(응답("https://cdn/1.mp3"))
      .mockRejectedValueOnce(new Error("network"));
    const { result } = renderHook(() => useTtsNarration());

    await act(async () => {
      await result.current.toggle(두조각본문);
    });

    expect(result.current.unavailable).toBe(false);
    expect(result.current.status).toBe("playing");
    act(() => {
      만들어진오디오[0].dispatchEvent(new Event("ended"));
    });
    expect(result.current.status).toBe("idle");
  });

  it("뒤 조각을 합성하는 동안 재생 상태를 '준비 중' 으로 덮지 않는다", async () => {
    /*
      뒷조각이 202 로 떨어져 폴링에 들어가도, 앞조각은 멀쩡히 들리는 중이다.
      여기서 synthesizing 을 켜면 소리는 나는데 버튼만 잠겨(disabled) 정지도
      못 하게 된다.
    */
    vi.useFakeTimers();
    try {
      mockSynthesize
        .mockResolvedValueOnce(응답("https://cdn/1.mp3"))
        .mockResolvedValueOnce(대기중("job-2"));
      mockPoll
        .mockResolvedValueOnce(상태만("pending"))
        .mockResolvedValueOnce(응답("https://cdn/2.mp3"));
      const { result } = renderHook(() => useTtsNarration());

      let 요청: Promise<void>;
      act(() => {
        요청 = result.current.toggle(두조각본문);
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });

      // 조각2 는 폴링 중, 조각1 은 재생 중.
      expect(result.current.status).toBe("playing");
      expect(result.current.synthesizing).toBe(false);

      await act(async () => {
        await vi.advanceTimersByTimeAsync(4_000);
      });
      await act(async () => {
        await 요청!;
      });
      expect(result.current.status).toBe("playing");
    } finally {
      vi.useRealTimers();
    }
  });
});
