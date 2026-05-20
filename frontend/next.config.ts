import type { NextConfig } from "next";

const config: NextConfig = {
  output: "standalone",
  reactStrictMode: true,
  // ⚠️ NEXT_PUBLIC_* 는 빌드 시점 inline.
  // 운영에선 backend API 를 *상대경로* (/api/*) 로 호출해 ingress 가 rewrite.
  // 로컬 dev 만 절대 URL 사용. (sparta-next-js-build-args 학습 반영)
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080"}/api/:path*`,
      },
    ];
  },
};

export default config;
