FROM node:22.19.0-bookworm-slim

ARG DSH_COMMIT=99f6f02fecdb7dff40c3fbc9470f5907c29f74ca
LABEL org.opencontainers.image.title="AgentForge DSH evaluation sidecar" \
      org.opencontainers.image.revision="$DSH_COMMIT"

ENV NODE_ENV=production \
    DSH_CORDIS_CONFIG=/opt/dsh/eval-deploy/cordis.yml \
    DSH_SESSION_ROOT=/tmp/dsh-sessions

WORKDIR /opt/dsh
COPY . .

RUN corepack enable \
    && corepack prepare pnpm@11.7.0 --activate \
    && pnpm install --frozen-lockfile \
    && pnpm run build:lib:host \
    && mkdir -p /tmp/dsh-sessions /tmp/dsh-eval \
    && chown -R node:node /tmp/dsh-sessions /tmp/dsh-eval /opt/dsh/eval-deploy

USER node
ENTRYPOINT ["node", "packages/examples/jsonrpc-demo/lib/packaged-bin.js"]
