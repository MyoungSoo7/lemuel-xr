"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { synthesizeTts } from "@/lib/api/tts";

/**
 * TTS 나레이션 재생 훅.
 *
 * 텍스트를 받아 백엔드 /api/tts/synthesize 를 호출하고, 돌려받은 audioUrl 을
 * HTMLAudioElement 로 재생/일시정지한다.
 *
 * ── graceful degradation ──
 * 오디오는 *보조 기능* 이다. TTS 사이드카가 없거나(로컬) 다운되면 synthesize 는
 * 502/타임아웃/네트워크 에러를 낸다. 그 경우 에러 UI 를 띄우지 않고 조용히
 * `status = "unavailable"` 로 넘어간다. 앱은 절대 깨지지 않는다.
 *
 * autoplay 금지 — 반드시 사용자 클릭(play) 으로만 재생한다.
 *
 * 상태:
 *  - idle        : 초기 / 정지 상태
 *  - loading     : synthesize 요청 중
 *  - playing     : 재생 중
 *  - unavailable : TTS 사용 불가(사이드카 없음/오류) — 조용히 비활성
 */
export type TtsStatus = "idle" | "loading" | "playing" | "unavailable";

export interface UseTtsNarration {
  status: TtsStatus;
  /** 사용 불가 판정 시 true — 버튼을 disabled/숨김 처리하는 데 쓴다. */
  unavailable: boolean;
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

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      const a = audioRef.current;
      if (a) {
        a.pause();
        a.src = "";
      }
    };
  }, []);

  const stop = useCallback(() => {
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

  const toggle = useCallback(
    async (text: string) => {
      const trimmed = text?.trim();
      if (!trimmed) return;

      // 재생 중이면 정지 (토글).
      if (status === "playing") {
        stop();
        return;
      }
      if (status === "loading") return;

      // 동일 텍스트를 이미 합성해 뒀으면 재합성 없이 바로 재생.
      const cached = cacheRef.current;
      if (cached && cached.text === trimmed) {
        playUrl(cached.url);
        return;
      }

      setStatus("loading");
      try {
        const res = await synthesizeTts(trimmed, voiceId);
        if (!mountedRef.current) return;
        if (!res?.audioUrl) {
          // 응답은 왔지만 URL 이 없음 — 사용 불가로 취급.
          setStatus("unavailable");
          return;
        }
        cacheRef.current = { text: trimmed, url: res.audioUrl };
        playUrl(res.audioUrl);
      } catch {
        // 사이드카 없음/다운/네트워크 오류 → 조용히 비활성. 에러 UI 없음.
        if (mountedRef.current) setStatus("unavailable");
      }
    },
    [status, stop, playUrl, voiceId],
  );

  return {
    status,
    unavailable: status === "unavailable",
    toggle,
    stop,
  };
}
