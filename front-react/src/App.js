import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import Home from "./components/Home";
import UserCrud from "./components/usuario/UserCrud";
import SessionCrud from "./components/session/SessionCrud";
import SessionPackageCrud from "./components/session-package/SessionPackageCrud";
import PaymentCrud from "./components/payment/PaymentCrud";
import AnnotationCrud from "./components/annotation/AnnotationCrud";
import Navigation from "./components/Navigation";

function App({ keycloak, realm }) {
  return (
    <BrowserRouter basename={`/${realm}`}>
      <Navigation keycloak={keycloak} />
      <Routes>
        <Route path="/" element={<Home keycloak={keycloak} realm={realm} />} />
        <Route path="/usuarios" element={<UserCrud keycloak={keycloak} realm={realm} />} />
        <Route path="/sessions" element={<SessionCrud keycloak={keycloak} realm={realm} />} />
        <Route path="/sessions-package" element={<SessionPackageCrud keycloak={keycloak} realm={realm} />} />
        <Route path="/payments" element={<PaymentCrud keycloak={keycloak} realm={realm} />} />
        <Route path="/annotations" element={<AnnotationCrud keycloak={keycloak} realm={realm} />} />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
