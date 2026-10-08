---
id: local
version: 1
name: Local (OpenAI-compatible)
description: Example for a local server without a key — LM Studio, Ollama, vLLM or tools/mock-openai-server.py. From inside the container the host is called host.docker.internal.
provider: local
base-url: http://host.docker.internal:1234/v1
model: mock-local-7b
temperature: 0.7
---
