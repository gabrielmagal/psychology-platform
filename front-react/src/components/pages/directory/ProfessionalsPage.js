import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { FiEdit3, FiSearch, FiUsers } from "react-icons/fi";
import { EmptyState, ErrorState, PageHeader, Skeleton } from "../../ui/Feedback";
import PersonCard from "../../ui/PersonCard";
import { RatingInline } from "../../ui/Rating";
import { useRoles } from "../../shared/UseRoles";
import { useDebouncedCallback } from "../../../utils/useDebouncedCallback";
import { useApi } from "../clinic/clinicApi";
import { brand } from "../../../theme/brand";

/** Guia de profissionais da clínica. Ordem alfabética: aqui não existe ranking. */
export default function ProfessionalsPage(props) {
    const request = useApi(props);
    const { isProfessional } = useRoles(props.keycloak);
    const [items, setItems] = useState(null);
    const [error, setError] = useState(null);
    const [search, setSearch] = useState("");
    const [query, setQuery] = useState("");
    const [attempt, setAttempt] = useState(0);
    const applySearch = useDebouncedCallback((value) => setQuery(value.trim()), 300);

    useEffect(() => {
        let cancelled = false;
        request(`/directory/professionals${query ? `?search=${encodeURIComponent(query)}` : ""}`)
            .then((data) => { if (!cancelled) { setItems(data || []); setError(null); } })
            .catch((err) => { if (!cancelled && err?.name !== "AbortError") setError(err); });
        return () => { cancelled = true; };
    }, [request, query, attempt]);

    return (
        <div className="ui-page">
            <PageHeader title="Profissionais" subtitle="Conheça quem atende na clínica: formação, abordagem e áreas de atuação.">
                <div style={{ position: "relative" }}>
                    <FiSearch aria-hidden="true" style={{ position: "absolute", left: 11, top: 12, color: "var(--ink-400)" }} />
                    <input className="ui-input" style={{ paddingLeft: 34, width: 260 }} type="search"
                           placeholder="Nome, área ou abordagem…" aria-label="Buscar profissional" value={search}
                           onChange={(e) => { setSearch(e.target.value); applySearch(e.target.value); }} />
                </div>
                {isProfessional && (
                    <Link className="ui-btn ui-btn--primary" to="/meu-perfil-publico"><FiEdit3 aria-hidden="true" /> Meu perfil público</Link>
                )}
            </PageHeader>

            {error && <ErrorState error={error} onRetry={() => setAttempt((n) => n + 1)} />}
            {!error && items === null && <div className="ui-card"><Skeleton rows={5} /></div>}

            {!error && items && items.length === 0 && (
                <div className="ui-card">
                    <EmptyState icon={FiUsers} title={query ? `Nada encontrado para "${query}"` : "Nenhum perfil publicado ainda"}>
                        {query
                            ? "Tente outro termo, como o nome ou uma área de atuação."
                            : isProfessional
                                ? "Preencha e publique o seu perfil para aparecer aqui."
                                : "Assim que os profissionais publicarem seus perfis, eles aparecem aqui."}
                    </EmptyState>
                </div>
            )}

            {!error && items && items.length > 0 && (
                <div className="ui-people-grid">
                    {items.map((item) => (
                        <PersonCard
                            key={item.id}
                            to={`/profissionais/${item.id}`}
                            name={item.name}
                            photo={item.photo}
                            subtitle={`${brand.professionalLabel}${item.registration ? ` · CRP ${item.registration}` : ""}`}
                            text={item.headline}
                            tags={[...(item.specialties || []), ...(item.approaches || [])]}
                            badge={!item.published && <span className="ui-badge ui-badge--warning">Rascunho: só você e a administração veem</span>}
                            footer={(
                                <>
                                    <span>{[item.attendsOnline && "Online", item.attendsInPerson && "Presencial"].filter(Boolean).join(" · ")
                                        || (item.city || "Ver perfil")}</span>
                                    <RatingInline summary={item.reviews} />
                                </>
                            )}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}
