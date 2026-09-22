---
id: local
version: 1
name: Lokal (OpenAI-kompatibel)
description: Beispiel fuer einen lokalen Server ohne Schluessel — LM Studio, Ollama, vLLM oder tools/mock-openai-server.py. Aus dem Container heisst der Host host.docker.internal.
provider: local
base-url: http://host.docker.internal:1234/v1
model: mock-local-7b
temperature: 0.7
---
