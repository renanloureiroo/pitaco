# syntax=docker/dockerfile:1
FROM node:24-alpine AS deps
WORKDIR /app
COPY painel/package.json painel/package-lock.json ./painel/
COPY sdk ./sdk/
WORKDIR /app/painel
RUN --mount=type=cache,target=/root/.npm npm install

FROM node:24-alpine AS build
WORKDIR /app/painel
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/sdk /app/sdk
COPY --from=deps /app/painel/node_modules ./node_modules
COPY painel .
# O build não fala com a API: toda página é dinâmica (lê searchParams ou params),
# então PITACO_API_URL só é exigida em tempo de execução.
RUN npm run build

FROM node:24-alpine AS runtime
WORKDIR /app/painel
# 0.0.0.0 porque o padrão do server.js standalone é localhost, inalcançável de fora do contêiner.
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3001 \
    HOSTNAME=0.0.0.0
RUN addgroup -S app && adduser -S app -G app
# O standalone já traz o node_modules mínimo que o server.js precisa; static e public
# ficam de fora dele e são copiados à parte.
COPY --from=build /app/painel/.next/standalone/painel ./
COPY --from=build /app/painel/.next/static ./.next/static
COPY --from=build /app/painel/public ./public
USER app
EXPOSE 3001
CMD ["node", "server.js"]
