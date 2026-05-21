import { api } from "./client";

export interface QueueItem {
  contentVersionId: string;
  contentKind: string;
  contentRef: string;
  version: string;
  createdAt: string;
  generatedBy: string | null;
  theologyReviewed: boolean;
  clinicalReviewed: boolean;
}

export interface TheologyReviewRequest {
  contentVersionId: string;
  reviewerProfileId?: string | null;
  verdict: "approve" | "request_changes" | "reject";
  scriptureAccuracy?: number;     // 1-5
  doctrinalBalance?: number;
  therapeuticSafety?: number;
  notes?: string;
  suggestedChanges?: Record<string, unknown>;
  referencedPmids?: string[];
}

export interface ClinicalReviewRequest {
  contentVersionId: string;
  reviewerProfileId?: string | null;
  verdict: "approve" | "request_changes" | "reject";
  traumaSafety?: number;
  crisisResourceCompliance?: number;
  /** moral_injury_risk — 낮을수록 위험 (Jones 2022 PMID 35609469 매핑). */
  moralInjuryRisk?: number;
  evidenceQuality?: number;
  vetoUsed?: boolean;
  vetoReason?: string;
  notes?: string;
  suggestedChanges?: Record<string, unknown>;
  referencedPmids?: string[];
}

export interface ReviewSubmitted {
  reviewId: number;
  contentVersionId: string;
  reviewedAt: string;
}

export async function fetchReviewQueue(): Promise<QueueItem[]> {
  const res = await api.get<QueueItem[]>("/api/reviews/queue");
  return res.data;
}

export async function submitTheologyReview(req: TheologyReviewRequest): Promise<ReviewSubmitted> {
  const res = await api.post<ReviewSubmitted>("/api/theology/reviews", req);
  return res.data;
}

export async function submitClinicalReview(req: ClinicalReviewRequest): Promise<ReviewSubmitted> {
  const res = await api.post<ReviewSubmitted>("/api/clinical/reviews", req);
  return res.data;
}
