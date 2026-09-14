# syntax=docker/dockerfile:1
FROM node:24-alpine AS build
WORKDIR /app

COPY sdk ./sdk/
WORKDIR /app/sdk
RUN npm install && npm run build

WORKDIR /app/painel
COPY painel/package.json painel/package-lock.json ./
# Run npm install with --install-links to copy the SDK into node_modules instead of symlinking,
# so Next.js/Turbopack doesn't block it for being outside the project root!
RUN npm install --install-links

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

# Since we built directly inside /app/painel without a root workspace, standalone output is flat.
COPY --from=build /app/painel/.next/standalone ./
COPY --from=build /app/painel/.next/static ./.next/static
COPY --from=build /app/painel/public ./public
USER app
EXPOSE 3001
CMD ["node", "server.js"]
