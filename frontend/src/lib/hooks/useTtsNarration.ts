"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { pollTtsJob, synthesizeTts } from "@/lib/api/tts";

/**
 * TTS 나레이션 재생 훅.
 *
 * 텍스트를 받아 백엔드 /api/tts/synthesize 를 호출하고, 돌려받은 audioUrl 을
 * HTMLAudioElement 로 재생/일시정지한다.
 *
 * ── 왜 폴링인가 ──
 * 캐시에 없는 문장은 백엔드가 202 + jobId 만 주고 실제 합성은 워커에서 돈다
 * (CPU-only XTTS-v2 는 오디오 1초당 CPU 4.1~4.6초). 그래서 첫 재생은
 * `synthesizing` 상태로 수십 초 기다린 뒤 시작될 수 있고, 같은 문장의 두 번째
 * 재생은 캐시 히트라 즉시 시작된다.
 *
 * ── graceful degradation ──
 * 오디오는 *보조 기능* 이다. TTS 사이드카가 없거나(로컬) 다운되면 synthesize 는
 * 502/타임아웃/네트워크 에러를 낸다. 그 경우 에러 UI 를 띄우지 않고 조용히
 * `status = "unavailable"` 로 넘어간다. 앱은 절대 깨지지 않는다.
 *
 * autoplay 금지 — 반드시 사용자 클릭(play) 으로만 재생한다.
 *
 * 상태:
 *  - idle         : 초기 / 정지 상태
 *  - loading      : synthesize 요청 중
 *  - synthesizing : 서버가 합성 중 — jobId 폴링 중 (수십 초 걸릴 수 있음)
 *  - playing      : 재생 중
 *  - unavailable  : TTS 사용 불가(사이드카 없음/오류) — 조용히 비활성
 */
export type TtsStatus =
  "idle" | "loading" | "synthesizing" | "playing" | "unavailable";

/** 폴링 주기. 합성이 수십 초 규모라 더 자주 물어봐야 얻을 게 없다. */
const POLL_INTERVAL_MS = 2_000;

/**
 * 폴링 포기 시각. 백엔드 사이드카 타임아웃이 300초이므로 그보다 넉넉히 잡는다 —
 * 여기서 먼저 포기하면 실제로는 성공한 합성을 놓친다.
 */
const POLL_TIMEOUT_MS = 360_000;

export interface UseTtsNarration {
  status: TtsStatus;
  /** 사용 불가 판정 시 true — 버튼을 disabled/숨김 처리하는 데 쓴다. */
  unavailable: boolean;
  /** 서버가 합성 중 — 버튼에 "준비 중" 을 표시하는 데 쓴다. */
  synthesizing: boolean;
  /** 텍스트를 합성해 재생. 재생 중이면 정지. 실패 시 조용히 unavailable. */
  toggle: (text: string) => Promise<void>;
  /** 재생 정지. */
  stop: () => void;
}

export function useTtsNarration(voiceId?: string): UseTtsNarration {
  const [status, setStatus] = useState<TtsStatus>("idle");
  const audioRef = useRef<HTMLAudioElement | null>(null);
  // synthesize 성공 → 재생까지 이어진 audioUrl 캐시 (동일 텍스트 재재생 시 재합성 회피).
  const cacheRef = useRef<{ text: string; url: string } | null>(null);
  const mountedRef = useRef(true);
  // 진행 중인 폴링을 취소하는 수단. 값이 바뀌면 그 전에 돌던 루프는 결과를 버린다.
  // (unmount·stop·새 요청 시 증가 — 옛 job 이 뒤늦게 끝나 엉뚱한 오디오를 트는 것을 막는다.)
  const pollGenerationRef = useRef(0);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      pollGenerationRef.current += 1;
      const a = audioRef.current;
      if (a) {
        a.pause();
        a.src = "";
      }
    };
  }, []);

  const stop = useCallback(() => {
    pollGenerationRef.current += 1; // 기다리던 합성이 있으면 그것도 포기한다.
    const a = audioRef.current;
    if (a) {
      a.pause();
      a.currentTime = 0;
    }
    if (mountedRef.current) {
      setStatus((s) => (s === "unavailable" ? s : "idle"));
    }
  }, []);

  const playUrl = useCallback((url: string) => {
    let a = audioRef.current;
    if (!a) {
      a = new Audio();
      a.preload = "auto";
      a.onended = () => {
        if (mountedRef.current) setStatus("idle");
      };
      // 재생 중 오류(디코드 실패 등) — 조용히 idle 로.
      a.onerror = () => {
        if (mountedRef.current) setStatus("idle");
      };
      audioRef.current = a;
    }
    /*
      react-hooks/immutability (플러그인 v7 의 React Compiler 규칙) 는 여기를
      막는다 — ref 에 담긴 객체의 *속성 대입* 을 금지한다. (`a.pause()` 같은
      메서드 호출은 안 걸리고, `a.src = ...` 만 걸린다.)

      규칙이 틀린 건 아니지만 지금 고치지 않는다. 제대로 고치려면 요소를
      재사용하지 말고 매 재생마다 `new Audio(url)` 로 만들어 — 속성 설정을 ref 에
      넣기 *전에* 끝내고 — 저장하는 구조로 바꿔야 한다. 그건 동작이 바뀌는
      변경인데, TTS 사이드카가 없으면 재생 경로를 실행해 볼 수가 없다.
      돌려보지 못한 채 오디오 코드를 바꾸면 조용히 깨진다.

      그래서 이 한 줄만 예외로 두고, 나머지 코드베이스에서는 이 규칙이 그대로
      살아 있게 한다. 사이드카를 띄울 수 있게 되면 위 구조로 바꾸고 이 주석을
      지울 것.
    */
    // eslint-disable-next-line react-hooks/immutability
    a.src = url;
    // play() 는 Promise — autoplay 차단 등으로 reject 될 수 있으므로 조용히 처리.
    a.play()
      .then(() => {
        if (mountedRef.current) setStatus("playing");
      })
      .catch(() => {
        if (mountedRef.current) setStatus("idle");
      });
  }, []);

  /**
   * jobId 가 ready 가 될 때까지 폴링해 audioUrl 을 돌려준다.
   * 취소(stop/unmount/새 요청) 되거나 실패·타임아웃이면 null.
   */
  const waitForJob = useCallback(
    async (jobId: string, generation: number): Promise<string | null> => {
      const deadline = Date.now() + POLL_TIMEOUT_MS;
      while (Date.now() < deadline) {
        await new Promise((r) => setTimeout(r, POLL_INTERVAL_MS));
        // 기다리는 사이 사용자가 정지했거나 화면을 떠났으면 조용히 접는다.
        if (!mountedRef.current || pollGenerationRef.current !== generation) {
          return null;
        }
        try {
          const job = await pollTtsJob(jobId);
          if (job.status === "ready" && job.audioUrl) return job.audioUrl;
          if (job.status === "failed") return null;
          // pending — 계속 기다린다.
        } catch {
          // 폴링 한 번 실패는 치명적이지 않다 (네트워크 순간 오류). 다음 주기에 다시 묻는다.
        }
      }
      return null;
    },
    [],
  );

  const toggle = useCallback(
    async (text: string) => {
      const trimmed = text?.trim();
      if (!trimmed) return;

      // 재생 중이면 정지 (토글).
      if (status === "playing") {
        stop();
        return;
      }
      // 이미 합성을 기다리는 중이면 중복 요청하지 않는다.
      if (status === "loading" || status === "synthesizing") return;

      // 동일 텍스트를 이미 합성해 뒀으면 재합성 없이 바로 재생.
      const cached = cacheRef.current;
      if (cached && cached.text === trimmed) {
        playUrl(cached.url);
        return;
      }

      const generation = ++pollGenerationRef.current;
      setStatus("loading");
      try {
        const res = await synthesizeTts(trimmed, voiceId);
        if (!mountedRef.current || pollGenerationRef.current !== generation) {
          return;
        }

        // 큐 포화 — 고장이 아니라 "지금은 바쁨" 이다. 버튼을 영구히 죽이지 않고
        // idle 로 되돌려 사용자가 잠시 뒤 다시 누를 수 있게 둔다.
        if (res.status === "busy") {
          setStatus("idle");
          return;
        }

        if (res.status === "ready" && res.audioUrl) {
          cacheRef.current = { text: trimmed, url: res.audioUrl };
          playUrl(res.audioUrl);
          return;
        }

        if (res.status === "pending" && res.jobId) {
          setStatus("synthesizing");
          const url = await waitForJob(res.jobId, generation);
          if (!mountedRef.current || pollGenerationRef.current !== generation) {
            return;
          }
          if (!url) {
            setStatus("unavailable");
            return;
          }
          cacheRef.current = { text: trimmed, url };
          playUrl(url);
          return;
        }

        // failed — 또는 status 는 왔는데 필요한 필드가 빈 모순 응답.
        setStatus("unavailable");
      } catch {
        // 사이드카 없음/다운/네트워크 오류 → 조용히 비활성. 에러 UI 없음.
        if (mountedRef.current && pollGenerationRef.current === generation) {
          setStatus("unavailable");
        }
      }
    },
    [status, stop, playUrl, voiceId, waitForJob],
  );

  return {
    status,
    unavailable: status === "unavailable",
    synthesizing: status === "synthesizing",
    toggle,
    stop,
  };
}
