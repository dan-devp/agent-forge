-- A plan is the saved configuration; an experiment is one execution of it.
--
-- Starting used to be the only way to write a comparison down: the form was the storage.
-- That makes the same comparison unrepeatable — after changing a profile you want to run
-- exactly the same setup again, and typing it a second time is not the same setup, only a
-- similar one. The plan holds what stays equal, every start writes a new experiment against
-- it, and the executions of one plan are a time series over the plan itself.
--
-- Plans live in the database, unlike SOULs, tasks, agents and profiles: they are made in the
-- UI by whoever runs the training camp, not authored and versioned in Git.
create table experiment_plan (
    id               uuid        primary key,
    name             text        not null,
    description      text,
    task_id          text        not null,
    agent_id         text,
    provider_id      text,
    profiles         text[]      not null,
    include_baseline boolean     not null default true,
    repetitions      int         not null,
    created_at       timestamptz not null,
    updated_at       timestamptz not null
);

alter table agent_experiment add column plan_id uuid references experiment_plan (id) on delete set null;

create index agent_experiment_plan_idx on agent_experiment (plan_id);
create index experiment_plan_created_idx on experiment_plan (created_at desc);
