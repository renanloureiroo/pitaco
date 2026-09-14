# syntax=docker/dockerfile:1
FROM node:24-alpine AS build
WORKDIR /app

COPY sdk ./sdk/
WORKDIR /app/sdk
RUN npm install && npm run build

WORKDIR /app/painel
COPY painel/package.json painel/package-lock.json ./
RUN npm install

COPY painel ./
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:24-alpine AS runtime
WORKDIR /app/painel
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3001 \
    HOSTNAME=0.0.0.0
RUN addgroup -S app && adduser -S app -G app

COPY --from=build /app/painel/.next/standalone/painel ./
COPY --from=build /app/painel/.next/static ./.next/static
COPY --from=build /app/painel/public ./public
USER app
EXPOSE 3001
CMD ["node", "server.js"]
