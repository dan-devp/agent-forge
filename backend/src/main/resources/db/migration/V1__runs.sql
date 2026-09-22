-- Runs, their steps and their verdicts. The trace is the product, not a debugging
-- by-product — hence complete and append-only: a step or a verdict is written once and
-- never changed.
--
-- The authored entities (models, agents, tasks) live as versioned files under
-- DATA_DIR/files, not here. A run records their id AND version so the recipe behind a
-- result stays readable whatever was saved since.

create table run (
    id              uuid primary key,
    task_id         text,
    task_version    int,
    agent_id        text        not null,
    agent_version   int         not null,
    model_id        text        not null,
    model_version   int         not null,
    -- Model id at the endpoint, as the response reported it (or as the file said).
    model_name      text,
    temperature     numeric(4,3) not null,
    -- What the model saw, written before the first call: if the run crashes, the
    -- parameters are still here.
    system_prompt   text        not null,
    input           text        not null,
    tools           text[]      not null,
    status          text        not null,
    answer          text,
    error           text,
    steps           int         not null default 0,
    total_tokens    int,
    duration_ms     bigint,
    -- null = nothing evaluated (ad-hoc input or task without evals)
    passed          boolean,
    started_at      timestamptz not null,
    finished_at     timestamptz
);

create table run_step (
    id          bigserial   primary key,
    run_id      uuid        not null references run (id) on delete cascade,
    seq         int         not null,
    type        text        not null,
    name        text,
    input       text,
    output      text,
    tokens      int,
    duration_ms bigint,
    error       text,
    created_at  timestamptz not null,
    constraint run_step_seq_unique unique (run_id, seq)
);

-- One row per configured eval. A judge that called a model leaves its cost here too,
-- because a cost axis that omits the judge lies.
create table run_eval (
    id          bigserial   primary key,
    run_id      uuid        not null references run (id) on delete cascade,
    idx         int         not null,
    type        text        not null,
    passed      boolean     not null,
    rationale   text,
    model       text,
    tokens      int,
    duration_ms bigint,
    created_at  timestamptz not null
);

create index run_step_run_idx on run_step (run_id, seq);
create index run_eval_run_idx on run_eval (run_id, idx);
create index run_started_idx on run (started_at desc);
create index run_task_idx on run (task_id, task_version);
create index run_agent_idx on run (agent_id, agent_version);
