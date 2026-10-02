-- An eval may give several verdicts: a judge asked a list of yes/no questions writes one
-- row per question, all with the eval's idx. The subject names what the row is about,
-- here the question; null for evals with a single verdict.
alter table run_eval add column subject text;
