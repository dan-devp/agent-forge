---
id: rookie-learns
version: 1
name: Rookie learns
description: The rookie starts almost without a prompt. The coach sees time-and-division and no-tool, division stays holdout.
agent: rookie
coach: coach
train:
  - time-and-division
  - no-tool
holdout:
  - division
repetitions: 3
max-steps: 4
max-tokens: 50000
approval: gate
---
The agent should use tools where they make the answer safe, and leave them out where they add
nothing. Answers are brief and pick up the terms of the question.
