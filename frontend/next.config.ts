import type { NextConfig } from "next";

const config: NextConfig = {
  output: "standalone",
  reactStrictMode: true,
  // /api/* 프록시는 src/app/api/[...path]/route.ts 의 *runtime* route handler 가 담당.
  // next.config 의 rewrites() 는 빌드 시점 평가라 standalone 컨테이너의 런타임 env 못 읽음.
};

export default config;
