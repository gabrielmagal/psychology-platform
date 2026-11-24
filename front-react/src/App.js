import React, { Suspense, lazy, useState, useEffect } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import Navigation from "./components/Navigation";
import AlertDialog from "./components/shared/dialog/AlertDialog";
import {useRoles} from "./components/shared/UseRoles";

// Cada tela vira um chunk próprio, carregado só quando o usuário navega até ela.
// Antes tudo — inclusive as telas de auditoria e de Mercado Pago, que a maioria dos
// perfis nunca abre — ia no bundle inicial e era baixado já no primeiro acesso.
const Home = lazy(() => import("./components/Home"));
const RetornoPagamentoPage = lazy(() => import("./components/pages/retorno-pagamento/RetornoPagamentoPage"));
const UserCrud = lazy(() => import("./components/pages/user/UserCrud"));
const SessionPackageCrud = lazy(() => import("./components/pages/session-package/SessionPackageCrud"));
const AuditLogCrud = lazy(() => import("./components/pages/audit/AuditLogCrud"));
const UserProfilePage = lazy(() => import("./components/pages/user/UserProfilePage"));
const MercadoPagoInfoCrud = lazy(() => import("./components/pages/mercadopagoinfo/MercadoPagoInfoCrud"));

function App({ keycloak, realm }) {
    const [alert, setAlert] = useState({ open: false, message: "" });
    const [doLogin, setDoLogin] = useState(false);

    const handleUnauthorized = () => {
        setAlert({ open: true, message: "Sessão expirada. Faça login novamente." });
        setDoLogin(true);
    };

    const handleForbidden = () => {
        setAlert({ open: true, message: "Você não tem permissão para essa ação." });
    };

    const { isAdmin, isPsychologist } = useRoles(keycloak);

    useEffect(() => {
        if (doLogin && !alert.open) {
            keycloak.login();
            setDoLogin(false);
        }
    }, [doLogin, alert.open, keycloak]);

    return (
        <BrowserRouter basename={`/${realm}`}>
          <Navigation keycloak={keycloak} realm={realm} onUnauthorized={handleUnauthorized} onForbidden={handleForbidden} />
          <Suspense fallback={<div style={{ padding: "2rem", textAlign: "center" }}>Carregando…</div>}>
            <Routes>
              <Route path="/retorno-pagamento" element={<RetornoPagamentoPage keycloak={keycloak} realm={realm} />} />
              <Route path="/" element={<Home keycloak={keycloak} realm={realm} />} />
              { (isAdmin || isPsychologist) &&
                  (
                    <Route
                        path="/usuarios"
                        element={
                            <UserCrud
                                keycloak={keycloak}
                                realm={realm}
                                onUnauthorized={handleUnauthorized}
                                onForbidden={handleForbidden}
                            />
                        }
                    />
                  )
              }
              <Route path="/sessions-package" element={<SessionPackageCrud keycloak={keycloak} realm={realm} onUnauthorized={handleUnauthorized} onForbidden={handleForbidden} />} />
              { (isAdmin) &&
                (
                    <Route
                        path="/audit-log"
                        element={
                            <AuditLogCrud
                                keycloak={keycloak}
                                realm={realm}
                                onUnauthorized={handleUnauthorized}
                                onForbidden={handleForbidden}
                            />
                        }
                    />
                )
              }

              <Route path="/mercado-pago-info" element={
                      <MercadoPagoInfoCrud
                        keycloak={keycloak}
                        realm={realm}
                        onUnauthorized={handleUnauthorized}
                        onForbidden={handleForbidden}
                      />
                  }
              />
              <Route path="/perfil" element={
                        <UserProfilePage
                            keycloak={keycloak}
                            realm={realm}
                            onUnauthorized={handleUnauthorized}
                            onForbidden={handleForbidden}
                        />
                    }
              />
              <Route path="*" element={<Navigate to="/" />} />
            </Routes>
          </Suspense>
          <AlertDialog
            open={alert.open}
            message={alert.message}
            onClose={() => setAlert({ open: false, message: "" })}
          />
        </BrowserRouter>
    );
}

export default App;
