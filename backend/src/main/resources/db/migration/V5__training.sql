-- A series becomes a training: the runs are no longer independent repetitions but an
-- ordered sequence in which strategies may carry what they learned from one run into the
-- next. The name follows the concept — a task is the challenge, a training is one strategy
-- profile working on it N times, and trainings of the same task are what gets compared.
alter table agent_series rename to agent_training;
alter table agent_run rename column series_id to training_id;
alter index agent_run_series_idx rename to agent_run_training_idx;
alter index agent_series_created_idx rename to agent_training_created_idx;
alter table agent_training rename constraint agent_series_pkey to agent_training_pkey;

-- What a training has learned. Written by strategies in afterRun, read in beforeRun of the
-- next run — and only within the same training: training A must never see what training B
-- learned, or the comparison between them is worthless. Every entry names the run and the
-- strategy that produced it, so a rising learning curve can be explained, not just observed.
create table training_memory (
    id          bigserial   primary key,
    training_id uuid        not null references agent_training (id) on delete cascade,
    strategy_id text        not null,
    run_id      uuid        references agent_run (id) on delete set null,
    kind        text        not null,
    content     text        not null,
    created_at  timestamptz not null
);

create index training_memory_training_idx on training_memory (training_id, strategy_id, id);
