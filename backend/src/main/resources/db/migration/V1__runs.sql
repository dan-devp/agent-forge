-- Laeufe und ihre Schritte. Der Trace ist kein Debug-Nebenprodukt, sondern die
-- Eingabe der spaeteren Lernschleife (README, Schicht 4) — deshalb vollstaendig
-- und append-only: ein Schritt wird geschrieben und nie wieder geaendert.

create table agent_run (
    id              uuid primary key,
    auftrag         text        not null,
    soul_id         text        not null,
    soul_version    int         not null,
    provider_id     text        not null,
    modell          text,
    status          text        not null,
    antwort         text,
    fehler          text,
    schritte        int         not null default 0,
    tokens_gesamt   int,
    dauer_ms        bigint,
    gestartet_am    timestamptz not null,
    beendet_am      timestamptz
);

create table agent_run_step (
    id          bigserial   primary key,
    run_id      uuid        not null references agent_run (id) on delete cascade,
    nr          int         not null,
    art         text        not null,
    name        text,
    eingabe     text,
    ausgabe     text,
    tokens      int,
    dauer_ms    bigint,
    fehler      text,
    erstellt_am timestamptz not null,
    constraint agent_run_step_nr_eindeutig unique (run_id, nr)
);

create index agent_run_step_run_idx on agent_run_step (run_id, nr);
create index agent_run_gestartet_idx on agent_run (gestartet_am desc);
