-- Two additions that belong together: who worked, and what the work was compared against.
--
-- The agent is now an entity of its own — model, SOUL and tool allowlist in one versioned
-- file instead of three values assembled per call. The run records which agent in which
-- revision produced it; without the version a later comparison could not tell an edited
-- agent from an unchanged one.
alter table agent_run add column agent_id text;
alter table agent_run add column agent_version int;

alter table agent_training add column agent_id text;
alter table agent_training add column agent_version int;

-- The experiment is the bracket above the trainings: one agent, one task, several strategy
-- profiles, each trained N times. A single training says how one profile did; only the
-- experiment says whether it did better than the baseline, which is why the baseline is
-- part of it rather than something to remember to start by hand.
create table agent_experiment (
    id            uuid primary key,
    name          text,
    task_id       text        not null,
    task_version  int         not null,
    agent_id      text,
    agent_version int,
    provider_id   text,
    repetitions   int         not null,
    status        text        not null,
    error         text,
    created_at    timestamptz not null,
    finished_at   timestamptz
);

alter table agent_training add column experiment_id uuid references agent_experiment (id) on delete set null;

create index agent_training_experiment_idx on agent_training (experiment_id);
create index agent_experiment_created_idx on agent_experiment (created_at desc);
create index agent_experiment_task_idx on agent_experiment (task_id);
