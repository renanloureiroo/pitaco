# syntax=docker/dockerfile:1
#
# Debian slim (glibc) e não Alpine: o npm do node:24-alpine morre com segfault (exit 139) no
# install do painel no homelab, e o build e o runtime precisam da mesma libc para os binários
# nativos que o standalone carrega.
FROM node:24-bookworm-slim AS build
WORKDIR /app
ENV NPM_CONFIG_AUDIT=false \
    NPM_CONFIG_FUND=false \
    NPM_CONFIG_UPDATE_NOTIFIER=false

COPY sdk ./sdk/
WORKDIR /app/sdk
RUN --mount=type=cache,target=/root/.npm npm ci && npm run build

WORKDIR /app/painel
COPY painel/package.json painel/package-lock.json ./
# --install-links copia o SDK para node_modules em vez de criar symlink: o Turbopack bloqueia
# módulo fora da raiz do projeto. `ci` instala exatamente o lockfile, sem resolver de novo.
RUN --mount=type=cache,target=/root/.npm npm ci --install-links

COPY painel ./
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:24-bookworm-slim AS runtime
WORKDIR /app/painel
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3001 \
    HOSTNAME=0.0.0.0
RUN groupadd --system app && useradd --system --gid app --no-create-home app

# Build feito direto em /app/painel, sem workspace na raiz: o standalone sai plano.
COPY --from=build --chown=app:app /app/painel/.next/standalone ./
COPY --from=build --chown=app:app /app/painel/.next/static ./.next/static
COPY --from=build --chown=app:app /app/painel/public ./public
USER app
EXPOSE 3001
CMD ["node", "server.js"]
