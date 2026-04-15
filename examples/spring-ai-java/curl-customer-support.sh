#!/bin/bash
curl -X POST http://localhost:8080/customerSupport \
  -H "Content-Type: application/json" \
  -d '{"prompt": "I have not received my order #12345, can you help?"}'
