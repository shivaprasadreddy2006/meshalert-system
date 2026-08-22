# Multi-stage Dockerfile for Mesh Alert System
# Stage 1: Build React Frontend
FROM node:20-alpine AS client-builder
WORKDIR /app/client
COPY client/package*.json ./
RUN npm install
COPY client/ ./
RUN npm run build

# Stage 2: Production Server
FROM node:20-alpine AS runner
WORKDIR /app/server
ENV NODE_ENV=production
ENV PORT=8080

# Copy server package & install production deps
COPY server/package*.json ./
RUN npm install --omit=dev
COPY server/src ./src

# Copy built client into place
COPY --from=client-builder /app/client/dist /app/client/dist

# Expose standard Railway port
EXPOSE 8080 5000 7000

CMD ["node", "src/server.js"]
