import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { NarrationAudioButton } from "./NarrationAudioButton";
import {
  pollTtsJob,
  synthesizeTts,
  type SynthesizeTtsResponse,
} from "@/lib/api/tts";

// 훅은 모킹하지 않는다 — 버튼의 상태 표시는 훅의 상태 전이와 한 벌이라
// 둘을 붙여 놔야 "실패했는데 버튼이 계속 활성" 같은 어긋남을 잡을 수 있다.
vi.mock("@/lib/api/tts", () => ({
  synthesizeTts: vi.fn(),
  pollTtsJob: vi.fn(),
}));

const mockSynthesize = vi.mocked(synthesizeTts);
const mockPoll = vi.mocked(pollTtsJob);

/** 캐시 히트 — 누르자마자 재생되는 응답. */
function 응답(audioUrl = "https://cdn/x.mp3"): SynthesizeTtsResponse {
  return {
    status: "ready",
    audioUrl,
    durationMs: 1000,
    cached: false,
    jobId: null,
  };
}

/** 202 — 서버가 합성 중이라 버튼이 "준비 중…" 을 띄워야 하는 응답. */
function 대기중(jobId = "job-1"): SynthesizeTtsResponse {
  return {
    status: "pending",
    audioUrl: null,
    durationMs: null,
    cached: false,
    jobId,
  };
}

/**
 * 나레이션 버튼.
 *
 * 대상 사용자가 고령자라 두 가지가 계약이다 —
 *   ① 상태(재생/로딩/사용불가)가 aria-label 과 글자로 *동시에* 드러난다.
 *   ② TTS 가 죽어도 에러 UI 를 절대 띄우지 않는다. 조용히 비활성 또는 숨김.
 */
describe("NarrationAudioButton", () => {
  beforeEach(() => {
    mockSynthesize.mockReset();
    mockPoll.mockReset();
  });

  it.each([
    ["빈 문자열", ""],
    ["공백뿐", "   "],
  ])("읽을 것이 없으면(%s) 버튼 자체를 렌더하지 않는다", (_이름, text) => {
    render(<NarrationAudioButton text={text} />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("기본 상태에서 무엇을 하는 버튼인지 라벨로 알 수 있다", () => {
    render(<NarrationAudioButton text="여호와는 나의 목자시니" />);
    const 버튼 = screen.getByRole("button", { name: "듣기 — 음성으로 듣기" });
    expect(버튼).toBeEnabled();
    expect(버튼).toHaveTextContent("듣기");
    // 상태 변화를 스크린 리더가 따라 읽을 수 있어야 한다.
    expect(버튼).toHaveAttribute("aria-live", "polite");
  });

  it("라벨을 바꾸면 보이는 글자와 aria-label 이 함께 바뀐다", () => {
    render(<NarrationAudioButton text="본문" label="낭독" />);
    expect(
      screen.getByRole("button", { name: "낭독 — 음성으로 듣기" }),
    ).toHaveTextContent("낭독");
  });

  it("누르면 그 텍스트를 지정한 목소리로 합성해 재생 상태가 된다", async () => {
    const user = userEvent.setup();
    mockSynthesize.mockResolvedValue(응답());
    render(
      <NarrationAudioButton text="여호와는 나의 목자시니" voiceId="ko-1" />,
    );

    await user.click(screen.getByRole("button"));

    expect(mockSynthesize).toHaveBeenCalledWith(
      "여호와는 나의 목자시니",
      "ko-1",
    );
    const 정지 = await screen.findByRole("button", { name: "듣기 정지" });
    expect(정지).toHaveTextContent("정지");
    expect(정지).toBeEnabled();
  });

  it("재생 중 다시 누르면 정지하고 원래 라벨로 돌아온다", async () => {
    const user = userEvent.setup();
    mockSynthesize.mockResolvedValue(응답());
    render(<NarrationAudioButton text="본문" />);

    await user.click(screen.getByRole("button"));
    await screen.findByRole("button", { name: "듣기 정지" });

    await user.click(screen.getByRole("button", { name: "듣기 정지" }));
    expect(
      await screen.findByRole("button", { name: "듣기 — 음성으로 듣기" }),
    ).toBeInTheDocument();
  });

  it("합성 대기 중에는 '불러오는 중' 으로 잠긴다 — 연타를 막는다", async () => {
    const user = userEvent.setup();
    let 해결: (v: ReturnType<typeof 응답>) => void = () => {};
    mockSynthesize.mockReturnValue(
      new Promise((res) => {
        해결 = res;
      }),
    );
    render(<NarrationAudioButton text="본문" />);

    await user.click(screen.getByRole("button"));

    const 로딩 = await screen.findByRole("button", {
      name: "음성 불러오는 중",
    });
    expect(로딩).toBeDisabled();
    expect(로딩).toHaveTextContent("불러오는 중");

    await user.click(로딩);
    expect(mockSynthesize).toHaveBeenCalledTimes(1);

    해결(응답());
    await screen.findByRole("button", { name: "듣기 정지" });
  });

  it("서버가 합성 중이면 '준비 중…' 을 띄우되 버튼을 잠그지 않는다", async () => {
    // 첫 재생은 수십 초가 걸릴 수 있다. 그동안 잠가 버리면 기다리기 싫은 사용자가
    // 빠져나올 방법이 없다 — 눌러서 취소할 수 있어야 한다.
    const user = userEvent.setup();
    mockSynthesize.mockResolvedValue(대기중());
    mockPoll.mockResolvedValue({ ...대기중(), status: "pending" });
    render(<NarrationAudioButton text="본문" />);

    await user.click(screen.getByRole("button"));

    const 준비중 = await screen.findByRole("button", {
      name: "음성 준비 중 — 눌러서 취소",
    });
    expect(준비중).toBeEnabled();
    expect(준비중).toHaveTextContent("준비 중…");
  });

  it("합성 대기 중 누르면 취소되고 원래 라벨로 돌아온다", async () => {
    const user = userEvent.setup();
    mockSynthesize.mockResolvedValue(대기중());
    mockPoll.mockResolvedValue({ ...대기중(), status: "pending" });
    render(<NarrationAudioButton text="본문" />);

    await user.click(screen.getByRole("button"));
    const 준비중 = await screen.findByRole("button", {
      name: "음성 준비 중 — 눌러서 취소",
    });

    await user.click(준비중);

    expect(
      await screen.findByRole("button", { name: "듣기 — 음성으로 듣기" }),
    ).toBeEnabled();
    // 취소는 재요청이 아니다.
    expect(mockSynthesize).toHaveBeenCalledTimes(1);
  });

  it("큐가 가득 차도(busy) 버튼은 살아 있어 다시 시도할 수 있다", async () => {
    const user = userEvent.setup();
    mockSynthesize.mockResolvedValue({
      status: "busy",
      audioUrl: null,
      durationMs: null,
      cached: false,
      jobId: null,
      retryAfterSeconds: 40,
    });
    render(<NarrationAudioButton text="본문" />);

    await user.click(screen.getByRole("button"));

    expect(
      await screen.findByRole("button", { name: "듣기 — 음성으로 듣기" }),
    ).toBeEnabled();
  });

  it("TTS 가 죽어 있으면 조용히 비활성되고 에러 문구는 한 글자도 안 뜬다", async () => {
    const user = userEvent.setup();
    mockSynthesize.mockRejectedValue(new Error("502 Bad Gateway"));
    const { container } = render(<NarrationAudioButton text="본문" />);

    await user.click(screen.getByRole("button"));

    const 버튼 = await screen.findByRole("button", {
      name: "음성 안내 사용 불가",
    });
    expect(버튼).toBeDisabled();
    // 오디오는 보조 기능이다. 실패를 사용자에게 사고처럼 보여 주지 않는다.
    expect(container.textContent).not.toMatch(/오류|에러|실패|502/);
    // 라벨 글자는 남아 자리와 맥락이 유지된다.
    expect(버튼).toHaveTextContent("듣기");
  });

  it("응답에 audioUrl 이 없어도 같은 방식으로 조용히 비활성된다", async () => {
    const user = userEvent.setup();
    mockSynthesize.mockResolvedValue({ ...응답(), audioUrl: "" });
    render(<NarrationAudioButton text="본문" />);

    await user.click(screen.getByRole("button"));
    expect(
      await screen.findByRole("button", { name: "음성 안내 사용 불가" }),
    ).toBeDisabled();
  });

  it("onUnavailable='hide' 면 사용 불가 판정 후 버튼이 사라진다", async () => {
    const user = userEvent.setup();
    mockSynthesize.mockRejectedValue(new Error("down"));
    render(<NarrationAudioButton text="본문" onUnavailable="hide" />);

    // 처음부터 숨기지는 않는다 — 눌러 보기 전엔 죽었는지 알 수 없다.
    await user.click(screen.getByRole("button"));

    await waitFor(() =>
      expect(screen.queryByRole("button")).not.toBeInTheDocument(),
    );
  });

  it("className 을 넘기면 기존 스타일을 지우지 않고 덧붙인다", () => {
    render(<NarrationAudioButton text="본문" className="mt-4" />);
    const 버튼 = screen.getByRole("button");
    expect(버튼.className).toContain("mt-4");
    // 터치 타깃 44px 은 고령자 접근성 요구라 커스텀 클래스에 밀려나면 안 된다.
    expect(버튼.className).toContain("min-h-[44px]");
  });
});
