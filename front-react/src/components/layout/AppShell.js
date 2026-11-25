import React, { useEffect, useRef, useState } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import {
    FiCalendar, FiChevronDown, FiCreditCard, FiDollarSign, FiHome, FiLogOut, FiMenu, FiPackage,
    FiActivity, FiArchive, FiAward, FiBell, FiBriefcase, FiCheckSquare, FiClipboard, FiClock, FiFileText, FiLifeBuoy, FiMessageSquare, FiShield, FiStar, FiSun, FiUser, FiUsers, FiX,
} from "react-icons/fi";
import styles from "./AppShell.module.css";
import { brand, BrandMark } from "../../theme/brand";
import { useRoles } from "../shared/UseRoles";
import { apiFetch } from "../../utils/fetchWithAuth";
import PatientSearch from "./PatientSearch";

const ROLE_LABEL = { admin: "Administrador", professional: brand.professionalLabel, patient: "Paciente" };

function buildMenu({ isAdmin, isProfessional, isPatient }) {
    const staff = isAdmin || isProfessional;
    return [
        {
            label: null,
            items: [
                { to: "/", label: "Início", icon: FiHome, end: true },
                { to: "/agenda", label: "Agenda", icon: FiCalendar },
                { to: "/profissionais", label: "Profissionais", icon: FiAward },
            ],
        },
        staff && {
            label: "Atendimento",
            items: [
                { to: "/usuarios", label: "Pacientes e usuários", icon: FiUsers },
                { to: "/sessions-package", label: "Pacotes de sessões", icon: FiPackage },
                { to: "/lista-de-espera", label: "Lista de espera", icon: FiClock },
                isProfessional && { to: "/instrumentos", label: "Instrumentos", icon: FiActivity },
                isProfessional && { to: "/meu-perfil-publico", label: "Meu perfil público", icon: FiStar },
            ].filter(Boolean),
        },
        staff && {
            label: "Financeiro",
            items: [
                { to: "/financeiro", label: "Resumo financeiro", icon: FiDollarSign },
                { to: "/convenios", label: "Convênios", icon: FiBriefcase },
                { to: "/recebimento-online", label: "Recebimento online", icon: FiCreditCard },
            ],
        },
        isPatient && {
            label: "Meu acompanhamento",
            items: [
                { to: "/sessions-package", label: "Meus pacotes", icon: FiPackage },
                { to: "/diario", label: "Diário de humor e sono", icon: FiSun },
                { to: "/tarefas", label: "Minhas tarefas", icon: FiCheckSquare },
                { to: "/questionarios", label: "Questionários", icon: FiClipboard },
                { to: "/plano-de-crise", label: "Plano de crise", icon: FiLifeBuoy },
                { to: "/termos", label: "Termos", icon: FiFileText },
            ],
        },
        isAdmin && {
            label: "Administração",
            items: [
                { to: "/avaliacoes", label: "Avaliações", icon: FiMessageSquare },
                { to: "/lembretes", label: "Lembretes automáticos", icon: FiBell },
                { to: "/audit-log", label: "Auditoria", icon: FiShield },
                { to: "/guarda", label: "Guarda de prontuários", icon: FiArchive },
            ],
        },
    ].filter(Boolean);
}

/** Estrutura das telas: menu lateral, barra superior e a área de conteúdo. */
export default function AppShell({ keycloak, realm, children }) {
    const roles = useRoles(keycloak);
    const location = useLocation();
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [menuOpen, setMenuOpen] = useState(false);
    const [profileImage, setProfileImage] = useState(null);
    const menuRef = useRef(null);

    const token = keycloak?.tokenParsed || {};
    const fullName = [token.given_name, token.family_name].filter(Boolean).join(" ") || token.preferred_username || "";
    const initials = fullName.split(" ").filter(Boolean).slice(0, 2).map((p) => p[0]).join("").toUpperCase() || "?";
    const roleLabel = roles.isAdmin ? ROLE_LABEL.admin : roles.isProfessional ? ROLE_LABEL.professional : ROLE_LABEL.patient;

    // Trocar de página fecha o menu do celular e o menu do usuário.
    useEffect(() => {
        setDrawerOpen(false);
        setMenuOpen(false);
    }, [location.pathname]);

    useEffect(() => {
        const closeOnOutsideClick = (e) => {
            if (menuRef.current && !menuRef.current.contains(e.target)) setMenuOpen(false);
        };
        const closeOnEscape = (e) => {
            if (e.key === "Escape") {
                setMenuOpen(false);
                setDrawerOpen(false);
            }
        };
        document.addEventListener("mousedown", closeOnOutsideClick);
        document.addEventListener("keydown", closeOnEscape);
        return () => {
            document.removeEventListener("mousedown", closeOnOutsideClick);
            document.removeEventListener("keydown", closeOnEscape);
        };
    }, []);

    useEffect(() => {
        let cancelled = false;
        apiFetch(`/user/keycloak`, { keycloak, realm })
            .then((data) => { if (!cancelled && data?.profileImage) setProfileImage(data.profileImage); })
            // A foto é enfeite: sem ela ficam as iniciais.
            .catch(() => {});
        return () => { cancelled = true; };
        // eslint-disable-next-line
    }, []);

    const menu = buildMenu(roles);

    return (
        <div className={styles.shell}>
            {drawerOpen && <div className={styles.scrim} onClick={() => setDrawerOpen(false)} />}

            <aside className={`${styles.sidebar} ${drawerOpen ? styles.sidebarOpen : ""} ui-no-print`}>
                <div className={styles.brand}>
                    <Link to="/" className={styles.brandLink}>
                        <BrandMark size={30} />
                        <span>
                            <strong>{brand.name}</strong>
                            <small>{brand.tagline}</small>
                        </span>
                    </Link>
                    <button className={styles.closeDrawer} onClick={() => setDrawerOpen(false)} aria-label="Fechar menu">
                        <FiX />
                    </button>
                </div>

                <nav className={styles.nav} aria-label="Menu principal">
                    {menu.map((group, index) => (
                        <div key={group.label || index} className={styles.group}>
                            {group.label && <div className={styles.groupLabel}>{group.label}</div>}
                            {group.items.map((item) => (
                                <NavLink
                                    key={item.to}
                                    to={item.to}
                                    end={item.end}
                                    className={({ isActive }) => `${styles.item} ${isActive ? styles.itemActive : ""}`}
                                >
                                    <item.icon aria-hidden="true" />
                                    <span>{item.label}</span>
                                </NavLink>
                            ))}
                        </div>
                    ))}
                </nav>

                <div className={styles.sidebarFooter}>Clínica <strong>{realm}</strong></div>
            </aside>

            <div className={styles.main}>
                <header className={`${styles.topbar} ui-no-print`}>
                    <button className={styles.hamburger} onClick={() => setDrawerOpen(true)} aria-label="Abrir menu">
                        <FiMenu />
                    </button>
                    {(roles.isAdmin || roles.isProfessional) && <PatientSearch keycloak={keycloak} realm={realm} />}
                    <div className={styles.topbarSpacer} />

                    <div className={styles.user} ref={menuRef}>
                        <button
                            className={styles.userButton}
                            onClick={() => setMenuOpen((open) => !open)}
                            aria-haspopup="menu"
                            aria-expanded={menuOpen}
                        >
                            {profileImage ? (
                                <img src={`data:image/jpeg;base64,${profileImage}`} alt="" className={styles.avatar} />
                            ) : (
                                <span className={styles.avatar}>{initials}</span>
                            )}
                            <span className={styles.userText}>
                                <strong>{fullName}</strong>
                                <small>{roleLabel}</small>
                            </span>
                            <FiChevronDown aria-hidden="true" />
                        </button>
                        {menuOpen && (
                            <div className={styles.userMenu} role="menu">
                                <Link to="/perfil" role="menuitem" className={styles.userMenuItem}>
                                    <FiUser aria-hidden="true" /> Meu perfil
                                </Link>
                                <button role="menuitem" className={`${styles.userMenuItem} ${styles.logout}`}
                                        onClick={() => keycloak.logout()}>
                                    <FiLogOut aria-hidden="true" /> Sair
                                </button>
                            </div>
                        )}
                    </div>
                </header>

                <main className={styles.content}>{children}</main>
            </div>
        </div>
    );
}
