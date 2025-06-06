
# Gerenciador de Cargos (React + Keycloak)

## Requisitos

- Node.js 16+
- Backend em http://localhost:8080/api
- Keycloak rodando em http://localhost:8083 com Realm e client configurado

## Variáveis de Ambiente

Configure o arquivo `.env` com:

```
REACT_APP_KEYCLOAK_URL=http://localhost:8083/auth
REACT_APP_KEYCLOAK_CLIENT_ID=myapp-react
REACT_APP_API_URL=http://localhost:8080/api
```

## Executar

```bash
npm install
npm start
```

## Acesso

Abra no navegador: `http://localhost:3000/campinas`

Onde `campinas` é o nome do Realm no Keycloak.
