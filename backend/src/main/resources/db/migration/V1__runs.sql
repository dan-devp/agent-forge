-- Runs, their steps and their scores. The trace is not a debugging by-product but the
-- input of the later learning loop (README, layer 4) — hence complete and append-only:
-- a step or a score is written once and never changed.

create table agent_run (
    id              uuid primary key,
    goal            text        not null,
    -- Which task in which revision triggered the run? Without the version a later
    -- comparison is worthless: a changed task produces different results unnoticed.
    task_id         text,
    task_version    int,
    soul_id         text        not null,
    soul_version    int         not null,
    provider_id     text        not null,
    model           text,
    status          text        not null,
    answer          text,
    error           text,
    steps           int         not null default 0,
    total_tokens    int,
    duration_ms     bigint,
    started_at      timestamptz not null,
    finished_at     timestamptz
);

create table agent_run_step (
    id          bigserial   primary key,
    run_id      uuid        not null references agent_run (id) on delete cascade,
    seq         int         not null,
    type        text        not null,
    name        text,
    input       text,
    output      text,
    tokens      int,
    duration_ms bigint,
    error       text,
    created_at  timestamptz not null,
    constraint agent_run_step_seq_unique unique (run_id, seq)
);

-- Several graders per run (deterministic, judge, human), each verdict its own row, never
-- overwritten — a later grader must not hide an earlier one, or the graders themselves
-- can no longer be compared.
create table agent_run_score (
    id          bigserial    primary key,
    run_id      uuid         not null references agent_run (id) on delete cascade,
    grader      text         not null,
    score       numeric(5,4) not null,
    passed      boolean      not null,
    rationale   text,
    created_at  timestamptz  not null
);

create index agent_run_step_run_idx on agent_run_step (run_id, seq);
create index agent_run_started_idx on agent_run (started_at desc);
create index agent_run_task_idx on agent_run (task_id, task_version);
create index agent_run_score_run_idx on agent_run_score (run_id);
