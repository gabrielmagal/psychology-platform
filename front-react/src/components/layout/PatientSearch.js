import React, { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { FiSearch } from "react-icons/fi";
import styles from "./AppShell.module.css";
import { apiFetch } from "../../utils/fetchWithAuth";
import { useDebouncedCallback } from "../../utils/useDebouncedCallback";

/** Busca de paciente na barra superior: digitar o nome e ir direto ao prontuário. */
export default function PatientSearch({ keycloak, realm }) {
    const navigate = useNavigate();
    const box = useRef(null);
    const [text, setText] = useState("");
    const [results, setResults] = useState(null);
    const [active, setActive] = useState(0);

    const search = useDebouncedCallback(async (value) => {
        if (value.trim().length < 2) {
            setResults(null);
            return;
        }
        try {
            const data = await apiFetch(
                `/user?page=0&size=8&userType=PACIENTE&search=${encodeURIComponent(value.trim())}`,
                { keycloak, realm }
            );
            setResults(data?.content || []);
            setActive(0);
        } catch {
            setResults([]);
        }
    }, 300);

    useEffect(() => {
        const close = (e) => { if (box.current && !box.current.contains(e.target)) setResults(null); };
        document.addEventListener("mousedown", close);
        return () => document.removeEventListener("mousedown", close);
    }, []);

    const go = (patient) => {
        setText("");
        setResults(null);
        navigate(`/prontuario/${patient.id}`);
    };

    const onKeyDown = (e) => {
        if (!results?.length) return;
        if (e.key === "ArrowDown") { e.preventDefault(); setActive((i) => (i + 1) % results.length); }
        if (e.key === "ArrowUp") { e.preventDefault(); setActive((i) => (i - 1 + results.length) % results.length); }
        if (e.key === "Enter") { e.preventDefault(); go(results[active]); }
        if (e.key === "Escape") setResults(null);
    };

    return (
        <div className={styles.search} ref={box}>
            <FiSearch aria-hidden="true" className={styles.searchIcon} />
            <input
                type="search"
                className={styles.searchInput}
                placeholder="Buscar paciente…"
                aria-label="Buscar paciente e abrir o prontuário"
                value={text}
                onChange={(e) => { setText(e.target.value); search(e.target.value); }}
                onKeyDown={onKeyDown}
            />
            {results && (
                <div className={styles.searchResults} role="listbox">
                    {results.length === 0 && <div className={styles.searchEmpty}>Nenhum paciente encontrado.</div>}
                    {results.map((patient, index) => (
                        <button key={patient.id} role="option" aria-selected={index === active}
                                className={`${styles.searchItem} ${index === active ? styles.searchItemActive : ""}`}
                                onMouseEnter={() => setActive(index)} onClick={() => go(patient)}>
                            <strong>{[patient.firstName, patient.lastName].filter(Boolean).join(" ")}</strong>
                            <small>Abrir prontuário</small>
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}
