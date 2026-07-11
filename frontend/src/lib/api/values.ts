import { api } from "./client";

export interface ValueDef {
  title: string;
  anchor_character?: "joseph" | "moses" | "david" | "jesus";
  anchor_scripture?: string;
  note?: string;
}

export type ValuesMap = Record<string, ValueDef>;

export interface ValuesStats {
  totalPractices7d: number;
  countByValue: Record<string, number>;
  cdrIndex: number;
  tier: string;
}

export interface ProfileResponse {
  values: ValuesMap;
  stats: ValuesStats;
}

export async function getValueProfile(): Promise<ProfileResponse> {
  const res = await api.get<ProfileResponse>("/api/values/me");
  return res.data;
}

export async function updateValueProfile(patch: Partial<ValuesMap>): Promise<ProfileResponse> {
  const res = await api.post<ProfileResponse>("/api/values/profile", patch);
  return res.data;
}

export interface PracticeRequest {
  valueId: number;
  durationSec?: number;
  note?: string;
  linkedCharacter?: string;
  linkedGameSession?: string;
}

export interface PracticeDto {
  id: number;
  valueId: number;
  practicedAt: string;
  durationSec?: number;
  note?: string;
  linkedCharacter?: string;
  linkedGameSession?: string;
}

export async function recordPractice(req: PracticeRequest): Promise<PracticeDto> {
  const res = await api.post<PracticeDto>("/api/values/practice", req);
  return res.data;
}
