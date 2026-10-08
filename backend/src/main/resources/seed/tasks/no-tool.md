---
id: no-tool
version: 1
name: Without a tool
description: A plain text task. Checks that the agent touches no tool when none is needed.
evals:
  - type: tool-not-called
    tool: '*'
  - type: contains
    text: capital
---
What is the capital of France? Answer in one sentence that contains the word "capital".
