import React from 'react';
import ReactDOM from 'react-dom/client';
import Keycloak from 'keycloak-js';
import App from './App';
import MissingRealmPage from './MissingRealmPage';
import dayjs from 'dayjs'
import 'dayjs/locale/pt-br'

const pathSegments = window.location.pathname.split('/').filter(Boolean);
const realmName = pathSegments.length > 0 ? pathSegments[0] : '';

const root = ReactDOM.createRoot(document.getElementById('root'));

dayjs.locale('pt-br')

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
    if (authenticated) {
      root.render(<App keycloak={keycloak} realm={realmName} />);
    } else {
      keycloak.login();
    }
  }).catch(error => {
    console.error("Erro ao inicializar Keycloak", error);
  });
}
