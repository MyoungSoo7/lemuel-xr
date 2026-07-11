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

// --- 기준4: 전도서와 인생 (TRACK-A-1-4-WISDOM-EMOTION §4) ---

/** 인생에 관한 전도서 진리 — 카테고리 분류. 근거는 성경만. */
export interface EcclesiastesCategory {
  key: string;
  title: string;
  chapterRef: string;
  verse: string;
  /** "해 아래 유한함" 을 정직하게 인정하는 문구 (헛됨 축). */
  honestNote: string;
  /** 그 유한함 너머 — 창조주 경외로 향하는 문구 (의미 축). */
  meaningNote: string;
}

/** 전 3:1-8 의 인생 계절 선택지. */
export interface EcclesiastesSeason {
  key: string;
  label: string;
  verse: string;
}

export interface EcclesiastesCategoriesResponse {
  categories: EcclesiastesCategory[];
  seasons: EcclesiastesSeason[];
  aiFooter: string;
}

export interface EcclesiastesCreateRequest {
  chapterRef?: string;
  userSeason?: string;
  futilityNote?: string;
  meaningNote?: string;
  listenedAudio?: boolean;
  conclusionViewed?: boolean;
}

export interface EcclesiastesDto {
  id: number;
  chapterRef: string | null;
  userSeason: string | null;
  futilityNote: string | null;
  meaningNote: string | null;
  listenedAudio: boolean | null;
  conclusionViewed: boolean | null;
  createdAt: string;
}

export interface EcclesiastesResponse {
  view: EcclesiastesDto;
  crisis: CrisisRouting;
  safetyFooter: string;
  conclusionInvite: string;
  aiFooter: string;
}

export interface EcclesiastesListResponse {
  items: EcclesiastesDto[];
  conclusionViewedCount: number;
  conclusionInvite: string;
}

export async function fetchEcclesiastesCategories(): Promise<EcclesiastesCategoriesResponse> {
  const res = await api.get<EcclesiastesCategoriesResponse>("/api/content/ecclesiastes/categories");
  return res.data;
}

export async function recordEcclesiastesView(
  req: EcclesiastesCreateRequest,
): Promise<EcclesiastesResponse> {
  const res = await api.post<EcclesiastesResponse>("/api/content/ecclesiastes", req);
  return res.data;
}

export async function fetchEcclesiastesViews(limit = 20): Promise<EcclesiastesListResponse> {
  const res = await api.get<EcclesiastesListResponse>("/api/content/ecclesiastes", {
    params: { limit },
  });
  return res.data;
}

// --- 기준1: 일기 조언 (TRACK-A-1-4-WISDOM-EMOTION §2 · 성경 기반 규칙형 조언) ---

/** 성경 구절 — ref + 본문. 성경 외 인용 없음. */
export interface GuidanceVerse {
  ref: string;
  text: string;
}

/** 감정별 성경 기반 조언 (구절 + 성찰 질문). */
export interface Guidance {
  emotion: string;
  emotionLabel: string;
  /** "이 감정도 성경 안에 있다" 인증 문구 (R2/R3). */
  validation: string;
  verses: GuidanceVerse[];
  reflectionQuestions: string[];
}

export interface GuidanceResponse {
  /** 단건(감정 지정/텍스트 조언) 시 채워짐. */
  guidance: Guidance | null;
  /** 감정 미지정 GET 시 전체 감정 선택지. */
  catalog: Guidance[];
  crisis: CrisisRouting;
  safetyFooter: string;
  aiFooter: string;
}

/** GET /journal/guidance — 감정으로 조회. 미지정이면 전체 카탈로그. */
export async function fetchJournalGuidance(emotion?: string): Promise<GuidanceResponse> {
  const res = await api.get<GuidanceResponse>("/api/content/journal/guidance", {
    params: emotion ? { emotion } : undefined,
  });
  return res.data;
}

/** POST /journal/guidance — 일기 텍스트 → 조언 (R1 위기 스캔 포함). */
export async function requestJournalGuidance(req: {
  text?: string;
  emotion?: string;
}): Promise<GuidanceResponse> {
  const res = await api.post<GuidanceResponse>("/api/content/journal/guidance", req);
  return res.data;
}

// --- 기준2: 잠언 주제별 조회 (TRACK-A-1-4-WISDOM-EMOTION §3 · 잠언만 근거) ---

export interface ProverbVerse {
  ref: string;
  text: string;
}

/** 잠언 주제 + 실존 잠언 구절 매핑. */
export interface ProverbsTheme {
  key: string;
  title: string;
  summary: string;
  /** R2/R3 — "결과 보장이 아니라 방향" 톤 안내. */
  guidance: string;
  verses: ProverbVerse[];
}

export interface ProverbsThemeListResponse {
  themes: ProverbsTheme[];
  safetyFooter: string;
  aiFooter: string;
}

export interface ProverbsByThemeResponse {
  theme: ProverbsTheme;
  safetyFooter: string;
  aiFooter: string;
}

export interface ProverbsInteractionResponse {
  id: number;
  theme: string;
  chosenProverbRef: string | null;
  safetyFooter: string;
  aiFooter: string;
}

export async function fetchProverbsThemes(): Promise<ProverbsThemeListResponse> {
  const res = await api.get<ProverbsThemeListResponse>("/api/content/proverbs/themes");
  return res.data;
}

export async function fetchProverbsByTheme(theme: string): Promise<ProverbsByThemeResponse> {
  const res = await api.get<ProverbsByThemeResponse>("/api/content/proverbs/by-theme", {
    params: { theme },
  });
  return res.data;
}

export async function recordProverbsInteraction(req: {
  theme: string;
  situation?: string;
  chosenProverbRef?: string;
  dimension?: string;
}): Promise<ProverbsInteractionResponse> {
  const res = await api.post<ProverbsInteractionResponse>(
    "/api/content/proverbs/interactions",
    req,
  );
  return res.data;
}
