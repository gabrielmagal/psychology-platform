import React, { Suspense, lazy, useState, useEffect, useCallback } from "react";
import { BrowserRouter, Routes, Route, Navigate, useLocation } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { ToastContainer } from "react-toastify";
import AppShell from "./components/layout/AppShell";
import AlertDialog from "./components/shared/dialog/AlertDialog";
import ErrorBoundary from "./components/ui/ErrorBoundary";
import { Skeleton } from "./components/ui/Feedback";
import { useRoles } from "./components/shared/UseRoles";
import ConsentGate from "./components/pages/clinic/ConsentGate";
import muiTheme from "./theme/muiTheme";

// Cada tela vira um chunk próprio, carregado só quando o usuário navega até ela.
const Home = lazy(() => import("./components/Home"));
const RetornoPagamentoPage = lazy(() => import("./components/pages/retorno-pagamento/RetornoPagamentoPage"));
const UserCrud = lazy(() => import("./components/pages/user/UserCrud"));
const SessionPackageCrud = lazy(() => import("./components/pages/session-package/SessionPackageCrud"));
const AuditLogCrud = lazy(() => import("./components/pages/audit/AuditLogCrud"));
const UserProfilePage = lazy(() => import("./components/pages/user/UserProfilePage"));
const AgendaPage = lazy(() => import("./components/pages/clinic/AgendaPage"));
const PatientRecordPage = lazy(() => import("./components/pages/clinic/PatientRecordPage"));
const FinancePage = lazy(() => import("./components/pages/clinic/FinancePage"));
const DocumentPage = lazy(() => import("./components/pages/clinic/DocumentPage"));
const MoodDiaryPage = lazy(() => import("./components/pages/clinic/MoodDiaryPage"));
const RetentionPage = lazy(() => import("./components/pages/clinic/RetentionPage"));
const PaymentSettingsPage = lazy(() => import("./components/pages/payment/PaymentSettingsPage"));
const ProfessionalsPage = lazy(() => import("./components/pages/directory/ProfessionalsPage"));
const ProfessionalProfilePage = lazy(() => import("./components/pages/directory/ProfessionalProfilePage"));
const MyPublicProfilePage = lazy(() => import("./components/pages/directory/MyPublicProfilePage"));
const carePage = (name) => lazy(() => import("./components/pages/clinic/MyCarePages").then((m) => ({ default: m[name] })));
const MyTasksPage = carePage("MyTasksPage");
const MySafetyPlanPage = carePage("MySafetyPlanPage");
const MyTermsPage = carePage("MyTermsPage");
const instrumentPage = (name) => lazy(() => import("./components/pages/clinic/InstrumentPages").then((m) => ({ default: m[name] })));
const MyFormsPage = instrumentPage("MyFormsPage");
const InstrumentTemplatesPage = instrumentPage("InstrumentTemplatesPage");
const RemindersPage = instrumentPage("RemindersPage");
const InsurancePage = lazy(() => import("./components/pages/clinic/InsurancePage"));
const WaitlistPage = lazy(() => import("./components/pages/clinic/WaitlistPage"));
const ReviewModerationPage = lazy(() => import("./components/pages/directory/ReviewModerationPage"));

function PageFallback() {
    return <div className="ui-page"><Skeleton rows={6} height={22} /></div>;
}

/** A barreira de erro precisa da rota atual para se limpar quando o usuário troca de tela. */
function Screens({ children }) {
    const location = useLocation();
    return <ErrorBoundary resetKey={location.pathname}>{children}</ErrorBoundary>;
}

function App({ keycloak, realm }) {
    const [alert, setAlert] = useState({ open: false, title: "", message: "" });
    const [doLogin, setDoLogin] = useState(false);

    // Identidade estável: estas funções entram nas dependências dos efeitos das telas, e
    // recriá-las a cada render do App faria cada tela recarregar seus dados em laço.
    const handleUnauthorized = useCallback(() => {
        setAlert({ open: true, title: "Sua sessão expirou", message: "Por segurança, faça login novamente para continuar." });
        setDoLogin(true);
    }, []);

    const handleForbidden = useCallback(() => {
        setAlert({
            open: true,
            title: "Você não tem permissão para isso",
            message: "Seu perfil não pode executar esta ação. Se precisar deste acesso, fale com o responsável pela clínica.",
        });
    }, []);

    const { isAdmin, isProfessional, isPatient } = useRoles(keycloak);
    const isStaff = isAdmin || isProfessional;
    const pageProps = { keycloak, realm, onUnauthorized: handleUnauthorized, onForbidden: handleForbidden };

    useEffect(() => {
        if (doLogin && !alert.open) {
            keycloak.login();
            setDoLogin(false);
        }
    }, [doLogin, alert.open, keycloak]);

    return (
        <ThemeProvider theme={muiTheme}>
            <BrowserRouter basename={`/${realm}`}>
                <AppShell keycloak={keycloak} realm={realm}>
                    <Screens>
                        <Suspense fallback={<PageFallback />}>
                            <Routes>
                                <Route path="/" element={<Home keycloak={keycloak} realm={realm} />} />
                                <Route path="/agenda" element={<AgendaPage {...pageProps} />} />
                                <Route path="/sessions-package" element={<SessionPackageCrud {...pageProps} />} />
                                <Route path="/retorno-pagamento" element={<RetornoPagamentoPage {...pageProps} />} />
                                <Route path="/documento/:kind/:id" element={<DocumentPage {...pageProps} />} />
                                <Route path="/perfil" element={<UserProfilePage {...pageProps} />} />
                                <Route path="/profissionais" element={<ProfessionalsPage {...pageProps} />} />
                                <Route path="/profissionais/:id" element={<ProfessionalProfilePage {...pageProps} />} />
                                {isProfessional && <Route path="/meu-perfil-publico" element={<MyPublicProfilePage {...pageProps} />} />}
                                {isAdmin && <Route path="/avaliacoes" element={<ReviewModerationPage {...pageProps} />} />}
                                {isStaff && <Route path="/usuarios" element={<UserCrud {...pageProps} />} />}
                                {isStaff && <Route path="/prontuario/:patientId" element={<PatientRecordPage {...pageProps} />} />}
                                {isStaff && <Route path="/financeiro" element={<FinancePage {...pageProps} />} />}
                                {isStaff && <Route path="/convenios" element={<InsurancePage {...pageProps} />} />}
                                {isStaff && <Route path="/lista-de-espera" element={<WaitlistPage {...pageProps} />} />}
                                {isStaff && <Route path="/recebimento-online" element={<PaymentSettingsPage {...pageProps} />} />}
                                {isPatient && <Route path="/diario" element={<MoodDiaryPage {...pageProps} />} />}
                                {isPatient && <Route path="/tarefas" element={<MyTasksPage {...pageProps} />} />}
                                {isPatient && <Route path="/plano-de-crise" element={<MySafetyPlanPage {...pageProps} />} />}
                                {isPatient && <Route path="/termos" element={<MyTermsPage {...pageProps} />} />}
                                {isPatient && <Route path="/questionarios" element={<MyFormsPage {...pageProps} />} />}
                                {isProfessional && <Route path="/instrumentos" element={<InstrumentTemplatesPage {...pageProps} />} />}
                                {isAdmin && <Route path="/lembretes" element={<RemindersPage {...pageProps} />} />}
                                {isAdmin && <Route path="/audit-log" element={<AuditLogCrud {...pageProps} />} />}
                                {isAdmin && <Route path="/guarda" element={<RetentionPage {...pageProps} />} />}
                                <Route path="*" element={<Navigate to="/" />} />
                            </Routes>
                        </Suspense>
                    </Screens>
                </AppShell>
                <ConsentGate {...pageProps} />
                <ToastContainer position="bottom-right" autoClose={4500} hideProgressBar newestOnTop />
                <AlertDialog
                    open={alert.open}
                    title={alert.title}
                    message={alert.message}
                    onClose={() => setAlert({ open: false, title: "", message: "" })}
                />
            </BrowserRouter>
        </ThemeProvider>
    );
}

export default App;
