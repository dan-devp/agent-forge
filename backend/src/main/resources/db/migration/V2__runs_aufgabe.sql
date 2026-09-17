-- Welche Aufgabe in welcher Fassung hat den Lauf ausgeloest?
-- Ohne Version ist ein spaeterer Vergleich wertlos: eine geaenderte Aufgabe erzeugt
-- andere Ergebnisse, und ohne Versionsangabe faellt das nicht auf.
alter table agent_run add column task_id      text;
alter table agent_run add column task_version int;

create index agent_run_task_idx on agent_run (task_id, task_version);
