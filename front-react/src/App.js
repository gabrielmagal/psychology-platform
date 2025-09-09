import React, { useState, useEffect } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import Home from "./components/Home";
import RetornoPagamentoPage from "./components/pages/retorno-pagamento/RetornoPagamentoPage";
import UserCrud from "./components/pages/user/UserCrud";
import SessionPackageCrud from "./components/pages/session-package/SessionPackageCrud";
import AuditLogCrud from "./components/pages/audit/AuditLogCrud";
import Navigation from "./components/Navigation";
import AlertDialog from "./components/shared/dialog/AlertDialog";
import {useRoles} from "./components/shared/UseRoles";
import UserProfilePage from "./components/pages/user/UserProfilePage";
import MercadoPagoInfoCrud from "./components/pages/mercadopagoinfo/MercadoPagoInfoCrud";

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

    const { isAdmin, isPsychologist, isPatient } = useRoles(keycloak);

    useEffect(() => {
        if (doLogin && !alert.open) {
            keycloak.login();
            setDoLogin(false);
        }
    }, [doLogin, alert.open, keycloak]);

    return (
        <BrowserRouter basename={`/${realm}`}>
          <Navigation keycloak={keycloak} realm={realm} onUnauthorized={handleUnauthorized} onForbidden={handleForbidden} />
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
          <AlertDialog
            open={alert.open}
            message={alert.message}
            onClose={() => setAlert({ open: false, message: "" })}
          />
        </BrowserRouter>
    );
}

export default App;
