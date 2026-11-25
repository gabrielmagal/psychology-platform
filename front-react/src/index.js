import React from 'react';
import ReactDOM from 'react-dom/client';
import Keycloak from 'keycloak-js';
import App from './App';
import MissingRealmPage from './MissingRealmPage';
import dayjs from 'dayjs'
import 'dayjs/locale/pt-br'
import './theme/theme.css'

const pathSegments = window.location.pathname.split('/').filter(Boolean);
const realmName = pathSegments.length > 0 ? pathSegments[0] : '';

const root = ReactDOM.createRoot(document.getElementById('root'));

dayjs.locale('pt-br')

// De quanto em quanto tempo verificar a validade do token, e com que antecedência renová-lo.
const TOKEN_CHECK_INTERVAL_MS = 30_000;
const TOKEN_MIN_VALIDITY_SECONDS = 70;

if (!realmName) {
  root.render(<MissingRealmPage />);
} else {
  const keycloak = new Keycloak({
    url: process.env.REACT_APP_KEYCLOAK_URL,
    realm: realmName,
    clientId: process.env.REACT_APP_KEYCLOAK_CLIENT_ID
  });

  keycloak.init({
    onLoad: 'login-required',
    checkLoginIframe: false
  }).then(authenticated => {
    if (!authenticated) {
      keycloak.login();
      return;
    }

    scheduleTokenRefresh(keycloak);
    root.render(<App keycloak={keycloak} realm={realmName} />);
  }).catch(error => {
    console.error("Erro ao inicializar Keycloak", error);
  });
}

/**
 * Renova o access token antes de ele expirar.
 *
 * Sem isso o token obtido no login ia vencendo em silêncio: como toda requisição envia
 * `keycloak.token` como está, o usuário simplesmente começava a levar 401 no meio do
 * trabalho e era mandado de volta para o login — normalmente perdendo o formulário que
 * estava preenchendo. A renovação usa o refresh token e é um no-op enquanto o access token
 * ainda tem validade sobrando.
 */
function scheduleTokenRefresh(keycloak) {
  const refresh = () =>
    keycloak
      .updateToken(TOKEN_MIN_VALIDITY_SECONDS)
      .catch(() => {
        console.warn("Não foi possível renovar a sessão; novo login necessário.");
        keycloak.login();
      });

  // O keycloak-js dispara este evento quando o token vence entre duas verificações.
  keycloak.onTokenExpired = refresh;

  setInterval(refresh, TOKEN_CHECK_INTERVAL_MS);
}
