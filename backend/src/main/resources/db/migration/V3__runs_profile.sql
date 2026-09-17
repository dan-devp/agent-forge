-- Which strategy profile in which revision governed the run? A strategy is
-- configuration, not a code path — only when the profile is recorded on the run can an
-- ablation later say which strategies were active. Baseline is the empty profile.
alter table agent_run add column profile_id      text;
alter table agent_run add column profile_version int;

create index agent_run_profile_idx on agent_run (profile_id, profile_version);
