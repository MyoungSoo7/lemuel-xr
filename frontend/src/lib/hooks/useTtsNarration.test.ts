import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useTtsNarration } from "./useTtsNarration";
import {
  pollTtsJob,
  synthesizeTts,
  type SynthesizeTtsResponse,
} from "@/lib/api/tts";

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
