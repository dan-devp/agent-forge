-- A batch groups the runs one start produced: agent version x tasks x repetitions.
-- Runs are queued first and executed one after another, so a run can now wait
-- (QUEUED) or be withdrawn before it starts (CANCELLED). A running model call is never
-- interrupted: it is paid for, so its answer is still recorded.
alter table run add column batch_id uuid;
alter table run add column queued_at timestamptz;
-- A queued run has not started yet.
alter table run alter column started_at drop not null;
update run set queued_at = started_at where queued_at is null;

create index run_batch_idx on run (batch_id);
create index run_matrix_idx on run (agent_id, agent_version, task_id);
