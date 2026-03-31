FROM node:20-slim

RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*
RUN npm install -g @openai/codex @anthropic-ai/claude-code && npm cache clean --force

# Set a non-root user (optional but recommended)
USER node

WORKDIR /workspace
