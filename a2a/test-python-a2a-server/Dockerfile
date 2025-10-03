FROM ghcr.io/astral-sh/uv:python3.12-alpine
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen
COPY src/ ./src/
EXPOSE 9999
CMD ["uv", "run", "--no-sync", "src/main.py"]
