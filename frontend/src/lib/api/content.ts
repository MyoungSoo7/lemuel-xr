import { api } from "./client";

export interface Topic {
  id: number;
  key: string;
  title: string;
}

export interface TopicsResponse {
  topics: Topic[];
}

export interface TopicCard {
  id: number;
  topicId: number;
  title: string;
  scriptureRef: string | null;
  body: string;
  anchorCharacter: string | null;
  targetEmotion: string | null;
  difficulty: number | null;
  publishedAt: string | null;
}

export interface CardsResponse {
  topicId: number;
  cards: TopicCard[];
}

export interface ScripturePassage {
  id: number;
  reference: string;
  translation: string;
  book: string;
  bookCode: string | null;
  chapter: number;
  verseStart: number;
  verseEnd: number | null;
  text: string;
  themeTags: string[] | null;
  characterTags: string[] | null;
}

export async function fetchTopics(): Promise<Topic[]> {
  const res = await api.get<TopicsResponse>("/api/content/topics");
  return res.data.topics;
}

export async function fetchTopicCards(topicId: number, emotion?: string, limit = 5): Promise<TopicCard[]> {
  const res = await api.get<CardsResponse>(`/api/content/topics/${topicId}/cards`, {
    params: { emotion, limit },
  });
  return res.data.cards;
}

export async function fetchScripturePassage(reference: string, translation = "modern"): Promise<ScripturePassage> {
  const res = await api.get<ScripturePassage>(`/api/scripture/${encodeURIComponent(reference)}`, {
    params: { translation },
  });
  return res.data;
}

// --- Theme 6·7 실천/성찰 (TRACK-A-5-7-ACTION-GUIDANCE §3·§4) ---

/** 6 = 마음 지킴, 7 = 사람 두려움. */
export type PracticeTopicId = 6 | 7;

/**
 * Theme 6: 'heart_checkin' | 'boundary_sentence'
 * Theme 7: 'courage_act'   | 'thought_record'
 */
export type PracticeKind =
  | "heart_checkin"
  | "boundary_sentence"
  | "courage_act"
  | "thought_record";

export interface PracticeCreateRequest {
  topicId: PracticeTopicId;
  practiceKind: PracticeKind;
  situation?: string;
  reflection?: Record<string, unknown>;
  actionTaken?: boolean;
  scriptureRef?: string;
  dimension?: "spiritual" | "emotional" | "rational";
}

export interface PracticeDto {
  id: number;
  topicId: number;
  practiceKind: string;
  situation: string | null;
  reflection: Record<string, unknown> | null;
  actionTaken: boolean | null;
  scriptureRef: string | null;
  dimension: string | null;
  createdAt: string;
}

export interface CrisisRouting {
  routed: boolean;
  resources: Record<string, unknown>[];
}

export interface PracticeResponse {
  practice: PracticeDto;
  crisis: CrisisRouting;
  safetyFooter: string;
  aiFooter: string;
}

export interface PracticeListResponse {
  topicId: number;
  items: PracticeDto[];
  actionCount: number;
}

export async function recordPractice(req: PracticeCreateRequest): Promise<PracticeResponse> {
  const res = await api.post<PracticeResponse>("/api/content/practice", req);
  return res.data;
}

export async function fetchPractices(topicId: PracticeTopicId, limit = 20): Promise<PracticeListResponse> {
  const res = await api.get<PracticeListResponse>("/api/content/practice", {
    params: { topicId, limit },
  });
  return res.data;
}
