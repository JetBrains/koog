#!/bin/bash
curl -X POST http://localhost:8080/deepResearch \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Find recent papers on large language models and reasoning"}'