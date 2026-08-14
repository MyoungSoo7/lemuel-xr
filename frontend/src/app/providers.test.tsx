import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Providers } from "./providers";

/**
 * 10줄짜리 파일이지만 여기가 틀어지면 앱의 *모든* 데이터 훅이 죽는다.
 * 재는 건 세 가지다:
 *
 *  1) QueryClient 가 실제로 컨텍스트에 실려 자식 훅이 돈다
 *  2) 리렌더에도 QueryClient 인스턴스가 바뀌지 않는다
 *     — useState 의 lazy initializer 를 `new QueryClient(...)` 로 잘못 바꾸면
 *       매 렌더마다 새 클라이언트가 생겨 캐시가 통째로 날아간다. 화면은 돈다.
 *       그냥 매번 네트워크를 다시 친다. 조용한 종류의 고장이라 여기서 못 박는다.
 *  3) 기본 옵션(staleTime 30s / retry 1)이 실제로 적용된다
 */
describe("Providers", () => {
  it("자식의 useQuery 가 실제로 동작한다", async () => {
    function Child() {
      const { data } = useQuery({
        queryKey: ["probe"],
        queryFn: async () => "값 도착",
      });
      return <p>{data ?? "대기"}</p>;
    }

    render(
      <Providers>
        <Child />
      </Providers>,
    );

    expect(await screen.findByText("값 도착")).toBeInTheDocument();
  });

  it("리렌더해도 같은 QueryClient 를 유지한다 — 캐시가 날아가지 않는다", async () => {
    const seen: unknown[] = [];

    function Child() {
      seen.push(useQueryClient());
      const [, setN] = useState(0);
      return (
        <button type="button" onClick={() => setN((n) => n + 1)}>
          리렌더
        </button>
      );
    }

    const { rerender } = render(
      <Providers>
        <Child />
      </Providers>,
    );
    // 부모 리렌더 + 자식 state 변경, 두 경로 모두에서 동일해야 한다.
    rerender(
      <Providers>
        <Child />
      </Providers>,
    );
    screen.getByRole("button", { name: "리렌더" }).click();

    expect(seen.length).toBeGreaterThan(1);
    expect(new Set(seen).size).toBe(1);
  });

  it("기본 쿼리 옵션 — staleTime 30초, retry 1", () => {
    let opts: unknown;

    function Child() {
      opts = useQueryClient().getDefaultOptions().queries;
      return null;
    }

    render(
      <Providers>
        <Child />
      </Providers>,
    );

    // staleTime 0 으로 돌아가면 화면 전환마다 같은 데이터를 다시 받는다.
    // retry 를 크게 올리면 백엔드 장애 시 실패가 사용자에게 늦게 보인다.
    expect(opts).toMatchObject({ staleTime: 30_000, retry: 1 });
  });
});
