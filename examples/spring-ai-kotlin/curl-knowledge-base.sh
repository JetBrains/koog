#!/bin/bash
curl -X POST http://localhost:8080/knowledgeBase \
  -H "Content-Type: application/json" \
  -d '{"prompt": "What information do you have about our products?"}'