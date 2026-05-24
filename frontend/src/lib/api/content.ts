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
