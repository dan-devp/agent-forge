-- A series: the same task N times under the same SOUL, profile and provider. One run is
-- an anecdote; only repetition shows whether a result is capability or luck. The series
-- row is the anchor the runs hang from; the statistics are computed from the runs on read,
-- never stored — a stored number can silently disagree with its runs.
create table agent_series (
    id              uuid primary key,
    task_id         text        not null,
    task_version    int         not null,
    soul_id         text,
    profile_id      text,
    provider_id     text,
    repetitions     int         not null,
    status          text        not null,
    completed       int         not null default 0,
    error           text,
    created_at      timestamptz not null,
    finished_at     timestamptz
);

alter table agent_run add column series_id uuid references agent_series (id) on delete set null;

create index agent_run_series_idx on agent_run (series_id);
create index agent_series_created_idx on agent_series (created_at desc);
