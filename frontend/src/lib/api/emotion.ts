import { api } from "./client";

export type Emotion =
  | "ANXIOUS"
  | "SAD"
  | "ANGRY"
  | "CONFUSED"
  | "LONELY"
  | "EXHAUSTED"
  | "GRATEFUL";

export interface ClassifyResponse {
  emotionLogId: number | null;
  primary: { emotion: Emotion; confidence: number };
  recommendations: {
    trackA: Array<{ topicId: number; title: string; rationale?: string }>;
    trackB: Array<{ character: string; rationale?: string }>;
  };
}

export async function classifyEmotion(
  text: string,
  preferredMode?: "vr" | "ar" | "mr",
): Promise<ClassifyResponse> {
  const res = await api.post<ClassifyResponse>("/api/emotion/classify", {
    text,
    context: preferredMode ? { preferredMode } : undefined,
  });
  return res.data;
}
