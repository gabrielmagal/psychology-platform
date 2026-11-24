-- Executado apenas na primeira inicialização do Postgres (diretório de dados vazio).
--
-- Antes, 'psychology' (API) e 'keycloak' precisavam ser criados manualmente depois do
-- primeiro `docker compose up`, senão os dois serviços subiam e falhavam ao conectar.

SELECT 'CREATE DATABASE psychology'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'psychology')\gexec

SELECT 'CREATE DATABASE keycloak'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec
