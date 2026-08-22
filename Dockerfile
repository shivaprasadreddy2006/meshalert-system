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
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=8080

# Copy root & server packages
COPY package*.json ./
COPY server/package*.json ./server/
RUN npm install --omit=dev && npm --prefix server install --omit=dev

COPY index.js ./
COPY server/src ./server/src

# Copy built client into place
COPY --from=client-builder /app/client/dist ./client/dist

# Expose standard Railway port
EXPOSE 8080 5000 7000

CMD ["node", "index.js"]
