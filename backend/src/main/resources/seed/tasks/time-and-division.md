---
id: time-and-division
version: 1
name: Time and arithmetic
description: Two built-in tools in one round. Checks whether the agent uses tools instead of guessing.
max-tool-calls: 4
evals:
  - type: tool-called
    tool: currentTime
  - type: tool-called
    tool: divide
  - type: regex
    pattern: '\b14\b'
---
What time is it in Berlin right now? And what is 100 divided by 7, with remainder?
Give both in one short sentence.
