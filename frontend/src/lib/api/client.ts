import axios from "axios";

// 운영: same-origin (/api/*) — ingress 가 backend 로 rewrite
// 로컬 dev: next.config.ts 의 rewrites 가 BACKEND_INTERNAL_URL 로 프록시
export const api = axios.create({
  baseURL: "",
  timeout: 30_000,
  headers: { "Content-Type": "application/json" },
});
