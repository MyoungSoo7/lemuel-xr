import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SceneBootState } from "./SceneBootState";
import { resetGuestSession } from "@/lib/api/client";

// 토큰 폐기는 부작용이라 실제로 localStorage 를 건드린다. 여기서 재려는 건
// "만료 상황에서 토큰을 버리는가" 라는 판단이므로 호출 여부만 본다.
vi.mock("@/lib/api/client", () => ({
  resetGuestSession: vi.fn(),
}));

/**
 * 세션 부팅 화면.
 *
 * 원래 7개 인물 페이지가 실패 분기 없이 "세션 시작 중..." 에서 영원히 멈췄고,
 * 재시도 가드 때문에 자동 복구도 없었다(2026-08-06 /joseph 제보). 그래서 이
 * 파일에서 재는 건 문구가 아니라 **사용자가 먹통에서 빠져나갈 수 있는가** 다.
 */
describe("SceneBootState", () => {
  beforeEach(() => {
    vi.mocked(resetGuestSession).mockClear();
  });

  it("에러가 아니면 진행 안내만 보이고 재시도 손잡이는 없다", () => {
    render(<SceneBootState isError={false} error={null} onRetry={vi.fn()} />);
    expect(screen.getByText("세션 시작 중...")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("실패하면 실패했다고 말하고 다시 시도 버튼을 준다", async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    render(
      <SceneBootState isError error={new Error("boom")} onRetry={onRetry} />,
    );

    expect(screen.getByText("세션을 시작하지 못했습니다.")).toBeInTheDocument();
    // 화면이 멈춰 있는 게 아니라는 걸 사용자가 알 수 있어야 한다.
    expect(screen.queryByText("세션 시작 중...")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it("HTTP 상태가 없는 에러면 오류 코드 줄을 띄우지 않는다", () => {
    // 네트워크 끊김 등 status 가 없는 실패. 의미 없는 'null' 이 화면에 뜨면 안 된다.
    render(
      <SceneBootState isError error={new Error("network")} onRetry={vi.fn()} />,
    );
    expect(screen.queryByText(/오류 코드/)).not.toBeInTheDocument();
  });

  it.each([401, 403])(
    "%d 이면 만료 문구를 보이고 재시도 시 게스트 토큰을 버린다",
    async (status) => {
      const user = userEvent.setup();
      const onRetry = vi.fn();
      render(
        <SceneBootState
          isError
          error={{ response: { status } }}
          onRetry={onRetry}
        />,
      );

      expect(
        screen.getByText("세션이 만료됐습니다. 다시 시작해 주세요."),
      ).toBeInTheDocument();
      expect(screen.getByText(`오류 코드 ${status}`)).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "다시 시도" }));
      // 만료 토큰을 그대로 두고 재시도하면 같은 401 이 무한 반복된다 — 앱이 잠긴다.
      expect(resetGuestSession).toHaveBeenCalledTimes(1);
      expect(onRetry).toHaveBeenCalledTimes(1);
    },
  );

  it("500 은 인증 문제가 아니므로 토큰을 버리지 않는다", async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    render(
      <SceneBootState
        isError
        error={{ response: { status: 500 } }}
        onRetry={onRetry}
      />,
    );

    expect(screen.getByText("세션을 시작하지 못했습니다.")).toBeInTheDocument();
    expect(screen.getByText("오류 코드 500")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    // 멀쩡한 세션을 서버 오류 때문에 날리면 사용자는 진행 상태를 잃는다.
    expect(resetGuestSession).not.toHaveBeenCalled();
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it.each([
    ["문자열", "boom"],
    ["null", null],
    ["response 없는 객체", {}],
    ["status 가 숫자가 아닌 응답", { response: { status: "401" } }],
  ])(
    "axios 에러가 아닌 %s 입력에서도 안전하게 상태 없음으로 떨어진다",
    (_이름, error) => {
      // statusOf 는 어떤 값이 와도 던지지 않아야 한다 — 여기서 죽으면
      // "실패했다"는 안내조차 못 띄우고 원래의 먹통으로 돌아간다.
      render(<SceneBootState isError error={error} onRetry={vi.fn()} />);
      expect(
        screen.getByText("세션을 시작하지 못했습니다."),
      ).toBeInTheDocument();
      expect(screen.queryByText(/오류 코드/)).not.toBeInTheDocument();
    },
  );
});
