FROM ollama/ollama:latest

LABEL authors="Anastasiia.Zarechneva"

RUN ollama serve & \
    until curl --output /dev/null --silent --head --fail http://localhost:11434; do \
        sleep 1; \
    done && \
    ollama pull llama3.2 && \
    pkill ollama

EXPOSE 11434

CMD ["ollama", "serve"]