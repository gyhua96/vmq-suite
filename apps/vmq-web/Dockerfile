FROM node:22-alpine AS base
ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
RUN corepack enable

FROM base AS deps
RUN apk add --no-cache libc6-compat
WORKDIR /app
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY apps/vmq-web/package.json ./apps/vmq-web/
RUN pnpm install --frozen-lockfile

FROM base AS builder
WORKDIR /app
ARG NEXT_PUBLIC_VMQ_API_BASE=/vmq-api
ARG NEXT_PUBLIC_VMQ_BASE_PATH=/vmq
ARG VMQ_API_PROXY_URL=http://vmq-api:50126
ENV NEXT_PUBLIC_VMQ_API_BASE=$NEXT_PUBLIC_VMQ_API_BASE
ENV NEXT_PUBLIC_VMQ_BASE_PATH=$NEXT_PUBLIC_VMQ_BASE_PATH
ENV VMQ_API_PROXY_URL=$VMQ_API_PROXY_URL
COPY --from=deps /app/node_modules ./node_modules
COPY --from=deps /app/apps/vmq-web/node_modules ./apps/vmq-web/node_modules
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY apps/vmq-web ./apps/vmq-web
RUN pnpm --filter vmq-web build

FROM base AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs
# COPY --from=builder /app/apps/vmq-web/public ./apps/vmq-web/public
COPY --from=builder --chown=nextjs:nodejs /app/apps/vmq-web/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/apps/vmq-web/.next/static ./apps/vmq-web/.next/static
EXPOSE 50125
USER nextjs
ENV PORT=50125
ENV HOSTNAME="0.0.0.0"
CMD ["node", "apps/vmq-web/server.js"]
