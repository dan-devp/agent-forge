-- What exactly did the model see? Without temperature, the rendered system prompt and
-- the offered tool set two runs cannot be compared: a changed SOUL file, a reconnected
-- MCP server or a different provider setting would alter results unnoticed.
alter table agent_run add column temperature   numeric(4,3);
alter table agent_run add column system_prompt text;
alter table agent_run add column tools         text[];
