---
id: division
version: 1
name: Division with remainder
description: A calculation that is safe with the tool and easy to get wrong in your head. Serves as holdout for trainings on time-and-division.
max-tool-calls: 3
evals:
  - type: tool-called
    tool: divide
  - type: regex
    pattern: '\b30\b'
---
What is 365 divided by 12, with remainder? Answer in one sentence.
