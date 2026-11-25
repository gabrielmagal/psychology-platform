# Backup e guarda dos dados

## Backup automático

O serviço `postgres-backup` do `docker-compose.yml` faz um `pg_dump` por dia dos bancos da
aplicação e do Keycloak, em `docker/backups/`, e apaga as cópias com mais de 30 dias.

| Variável | Padrão | Para quê |
|---|---|---|
| `BACKUP_DATABASES` | `psychology keycloak` | Bancos copiados |
| `BACKUP_KEEP_DAYS` | `30` | Dias que cada cópia fica guardada |
| `BACKUP_INTERVAL_SECONDS` | `86400` | Intervalo entre backups |

Fazer um backup agora:

```bash
docker compose run --rm postgres-backup once
```

Restaurar (em um banco vazio):

```bash
docker compose exec -T postgres pg_restore -U arquitetura -d psychology --clean --if-exists < backups/psychology-AAAAMMDD-HHMMSS.dump
```

**O que este serviço não faz:** levar as cópias para fora da máquina. Um backup no mesmo
disco do banco não protege contra perda do servidor — copie a pasta `backups/` para outro
lugar (outro servidor, armazenamento em nuvem) com a ferramenta de sua preferência.

## Chave de cifragem

O conteúdo clínico (anamnese, evolução, notas, anotações) e as chaves de pagamento são
gravados cifrados. A chave vem da variável `APP_CRYPTO_KEY`.

- Defina uma chave própria em produção. O valor padrão do código é só para desenvolvimento.
- Guarde a chave **fora** do servidor de banco e **fora** dos backups. Quem tiver o backup
  sem a chave não lê o conteúdo; quem perder a chave também não.
- Trocar a chave depois de haver dados gravados torna os dados antigos ilegíveis.

## Guarda do prontuário

O Conselho Federal de Psicologia exige guarda mínima de 5 anos. No sistema:

- o cadastro de um paciente com prontuário não pode ser excluído antes do prazo
  (`CLINIC_RECORD_RETENTION_YEARS`, padrão 5, contado do último atendimento);
- o administrador vê em **Guarda de prontuários** até quando cada registro deve ser mantido
  e quais já podem ser descartados;
- o descarte nunca é automático: é uma decisão de uma pessoa.
