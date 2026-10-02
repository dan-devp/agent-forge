-- A training session is one execution of a training file: measure the agent, let the coach
-- propose a better system prompt, write it as a new agent version, measure it, decide,
-- repeat. The file is the recipe; the session is the event, so it lives here.
--
-- Unlike run_step, a training_step is updated as it progresses (coach answer, then
-- measurement, then decision): a step is one unit of work that takes minutes, and the UI
-- shows it while it happens. What it measured stays in the runs of its batch.

create table training_session (
    id                uuid        primary key,
    training_id       text        not null,
    training_version  int         not null,
    agent_id          text        not null,
    coach_id          text        not null,
    coach_version     int         not null,
    approval          text        not null,
    repetitions       int         not null,
    max_steps         int         not null,
    max_tokens        int,
    -- The split as it was at the start: a later edit of the file does not rewrite history.
    train_tasks       text[]      not null,
    holdout_tasks     text[]      not null,
    status            text        not null,
    start_version     int         not null,
    best_version      int,
    final_version     int,
    -- Runs + judges + coach. A cost axis without the coach would lie.
    total_tokens      int         not null default 0,
    error             text,
    note              text,
    started_at        timestamptz not null,
    finished_at       timestamptz
);

create table training_step (
    id                bigserial   primary key,
    session_id        uuid        not null references training_session (id) on delete cascade,
    -- 0 = baseline, then one per coach proposal
    seq               int         not null,
    base_version      int,
    agent_version     int,
    batch_id          uuid,
    train_passed      int,
    train_total       int,
    holdout_passed    int,
    holdout_total     int,
    run_tokens        int,
    -- Exactly what the coach saw and said: a proposal is only as good as its evidence.
    coach_input       text,
    coach_output      text,
    rationale         text,
    coach_model       text,
    coach_tokens      int,
    coach_duration_ms bigint,
    gate              text,
    decision          text        not null,
    decided_by        text,
    reason            text,
    created_at        timestamptz not null,
    finished_at       timestamptz,
    constraint training_step_seq_unique unique (session_id, seq)
);

create index training_session_training_idx on training_session (training_id, started_at desc);
create index training_step_session_idx on training_step (session_id, seq);
