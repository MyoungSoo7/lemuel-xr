###########################
# Stage 1: deps
###########################
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json ./
RUN npm install --no-audit --no-fund

###########################
# Stage 2: build
###########################
FROM node:22-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

###########################
# Stage 3: runtime (standalone)
###########################
FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup -S nodejs && adduser -S next -G nodejs
COPY --from=builder /app/public ./public
COPY --from=builder --chown=next:nodejs /app/.next/standalone ./
COPY --from=builder --chown=next:nodejs /app/.next/static ./.next/static
USER next
EXPOSE 3000
ENV PORT=3000
CMD ["node", "server.js"]
