# Testes de ponta a ponta

Roteiros que exercitam o sistema **de verdade**: API rodando, Postgres e Keycloak reais.
Complementam os testes automatizados da API (`api/`, que usam banco em memória).

| Roteiro | O que faz |
|---|---|
| `api_flow.py` | Cria três usuários de teste (administrador, profissional, paciente) e percorre os fluxos pela API: agenda, prontuário, documentos, financeiro, pagamento online, LGPD — conferindo também o que cada perfil **não** pode fazer. |
| `ui_smoke.py` | Entra no front como cada perfil, abre cada tela, confere que carregou sem erro e salva uma captura em `e2e/screenshots/`. |

## Como rodar

1. Suba Postgres e Keycloak (`docker/docker-compose.yml`), a API e o front.
2. Copie `.env.example` para `.env`, ajuste, e carregue as variáveis no terminal.
3. Rode na ordem — o segundo usa os usuários criados pelo primeiro:

```bash
set -a; . e2e/.env; set +a
python e2e/api_flow.py
python e2e/ui_smoke.py            # ou: python e2e/ui_smoke.py profissional
```

Dependências: Python 3.11+ e, para as telas, `pip install playwright && playwright install chromium`.

## Cuidados

- **Só em ambiente local ou de testes.** Os roteiros criam usuários no Keycloak, gravam e
  apagam dados no banco do tenant informado.
- Os dados de teste usam CPF `9000000000x` e e-mail `@exemplo.com`, para serem fáceis de
  achar e remover.
- O perfil do profissional no Keycloak é `PROFISSIONAL_ROLE`. Em realms criados antes dessa
  mudança o perfil se chamava `PSICOLOGO_ROLE` e precisa ser renomeado.
