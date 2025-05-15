FROM ollama/ollama:latest

RUN apt-get update && apt-get install -y curl

RUN nohup ollama serve &

RUN sleep 5

RUN ollama pull meta/llama3:latest

EXPOSE 11434

CMD ["ollama", "serve"]