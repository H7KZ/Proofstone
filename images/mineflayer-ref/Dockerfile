# Reference client driver sidecar (ADR-0004, client-driver spec section 4). Built from this directory by the
# drivers:mineflayer-ref integration test; no bind mounts (ADR-0005). Pins: the base image by digest and the npm
# packages exactly (package.json and package-lock.json), as ADR-0006 requires.
FROM node:22-bookworm-slim@sha256:5ec23b7c622b00547753bc6ffd78085dd868a68aeb41c8e0a1418f033e13d96d

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY server.js ./

USER node
EXPOSE 8765
CMD ["node", "server.js"]
