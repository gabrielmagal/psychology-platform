import React, { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import { FiArrowLeft, FiCheck, FiMessageSquare, FiStar, FiX } from "react-icons/fi";
import { Alert, EmptyState, ErrorState, PageHeader, Skeleton } from "../../ui/Feedback";
import Avatar from "../../ui/Avatar";
import { RatingInline, RatingSummary } from "../../ui/Rating";
import ReviewList from "../../ui/Reviews";
import { errorText } from "../../../utils/errors";
import { useApi } from "../clinic/clinicApi";
import { brand } from "../../../theme/brand";
import styles from "../clinic/Clinic.module.css";

const COMMENT_STATUS = {
    PRIVATE: ["Paciente não autorizou exibir", "ui-badge"],
    PENDING: ["Aguardando aprovação", "ui-badge ui-badge--warning"],
    PUBLISHED: ["Publicado no perfil", "ui-badge ui-badge--success"],
    REJECTED: ["Não publicado", "ui-badge ui-badge--danger"],
};

/**
 * Avaliações na visão do administrador: a fila de comentários a aprovar e, por profissional,
 * tudo o que ele recebeu — com o nome de quem avaliou, que o profissional não vê.
 */
export default function ReviewModerationPage(props) {
    const request = useApi(props);
    const [tab, setTab] = useState("fila");
    const [queue, setQueue] = useState(null);
    const [people, setPeople] = useState(null);
    const [selected, setSelected] = useState(null);
    const [received, setReceived] = useState(null);
    const [error, setError] = useState(null);
    const [busy, setBusy] = useState(null);

    const load = useCallback(() => {
        Promise.all([request("/directory/reviews/moderation"), request("/directory/professionals")])
            .then(([pending, professionals]) => {
                setQueue(pending || []);
                setPeople(professionals || []);
                setError(null);
            })
            .catch((err) => { if (err?.name !== "AbortError") setError(err); });
    }, [request]);

    useEffect(() => { load(); }, [load]);

    const open = useCallback((person) => {
        setSelected(person);
        setReceived(null);
        request(`/directory/reviews?professionalId=${person.id}`)
            .then(setReceived)
            .catch((err) => { if (err?.name !== "AbortError") setError(err); });
    }, [request]);

    const decide = async (item, action) => {
        setBusy(item.id);
        try {
            await request(`/directory/reviews/${item.id}/${action}`, { method: "PUT" });
            toast.success(action === "publish" ? "Comentário publicado no perfil." : "Comentário não será exibido.");
            load();
            if (selected) open(selected);
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setBusy(null);
        }
    };

    /** Aprovar só o que espera decisão; tirar do ar o que já está publicado; rever o que foi recusado. */
    const actions = (item) => (
        <>
            {(item.commentStatus === "PENDING" || item.commentStatus === "REJECTED") && (
                <button className="ui-btn ui-btn--primary ui-btn--sm" disabled={busy === item.id}
                        onClick={() => decide(item, "publish")}><FiCheck aria-hidden="true" /> Publicar</button>
            )}
            {(item.commentStatus === "PENDING" || item.commentStatus === "PUBLISHED") && (
                <button className="ui-btn ui-btn--danger-soft ui-btn--sm" disabled={busy === item.id}
                        onClick={() => decide(item, "reject")}>
                    <FiX aria-hidden="true" /> {item.commentStatus === "PUBLISHED" ? "Tirar do perfil" : "Não publicar"}
                </button>
            )}
        </>
    );

    const status = (item) => {
        if (!item.comment) return null;
        const [label, className] = COMMENT_STATUS[item.commentStatus] || COMMENT_STATUS.PRIVATE;
        return <span className={className}>{label}</span>;
    };

    return (
        <div className="ui-page">
            <PageHeader title="Avaliações"
                        subtitle="O retorno que os pacientes dão depois das sessões, e os comentários que podem ir para o perfil de cada profissional." />

            <div className={styles.tabs} role="tablist">
                <button role="tab" aria-selected={tab === "fila"} className={tab === "fila" ? styles.tabActive : ""}
                        onClick={() => setTab("fila")}>
                    Para aprovar{queue?.length ? <span className={styles.tabCount}>{queue.length}</span> : null}
                </button>
                <button role="tab" aria-selected={tab === "profissionais"} className={tab === "profissionais" ? styles.tabActive : ""}
                        onClick={() => setTab("profissionais")}>Por profissional</button>
            </div>

            {error && <ErrorState error={error} onRetry={load} />}
            {!error && (queue === null || people === null) && <div className="ui-card"><Skeleton rows={5} /></div>}

            {!error && queue && tab === "fila" && (
                <div className="ui-stack">
                    <Alert tone="info" title="O que observar antes de publicar">
                        Não publique o que identifique o paciente ou o caso, prometa resultado ou compare profissionais.
                        Recusar o comentário não altera a nota: ela continua contando na média.
                    </Alert>
                    <div className="ui-card">
                        {queue.length === 0 ? (
                            <EmptyState icon={FiMessageSquare} title="Nenhum comentário aguardando">
                                Quando um paciente autorizar a exibição de um comentário, ele aparece aqui.
                            </EmptyState>
                        ) : (
                            <ReviewList
                                items={queue}
                                renderMeta={(item) => <span>para <strong>{item.subjectName}</strong> · de {item.authorName}</span>}
                                renderActions={actions}
                            />
                        )}
                    </div>
                </div>
            )}

            {!error && people && tab === "profissionais" && !selected && (
                <div className="ui-card">
                    {people.length === 0 ? (
                        <EmptyState icon={FiStar} title="Nenhum profissional cadastrado" />
                    ) : (
                        <div className="ui-table-wrap">
                            <table className="ui-table">
                                <thead>
                                    <tr><th>Profissional</th><th>Nota</th><th>Recomendam</th><th>Perfil</th><th aria-label="Ações" /></tr>
                                </thead>
                                <tbody>
                                    {people.map((person) => (
                                        <tr key={person.id}>
                                            <td>
                                                <span style={{ display: "inline-flex", alignItems: "center", gap: 10 }}>
                                                    <Avatar name={person.name} photo={person.photo} size={34} />
                                                    <span>
                                                        <strong>{person.name}</strong>
                                                        {person.registration && <span className="ui-muted"> · CRP {person.registration}</span>}
                                                    </span>
                                                </span>
                                            </td>
                                            <td>{person.reviews?.count ? <RatingInline summary={person.reviews} /> : <span className="ui-muted">Sem avaliações</span>}</td>
                                            <td>{person.reviews?.recommendPercent != null ? `${person.reviews.recommendPercent}%` : "—"}</td>
                                            <td>
                                                <span className={`ui-badge ${person.published ? "ui-badge--success" : "ui-badge--warning"}`}>
                                                    {person.published ? "Publicado" : "Rascunho"}
                                                </span>
                                            </td>
                                            <td style={{ textAlign: "right" }}>
                                                <button className="ui-btn ui-btn--sm" onClick={() => open(person)}>Ver avaliações</button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            )}

            {!error && tab === "profissionais" && selected && (
                <div className="ui-stack">
                    <div className="ui-toolbar">
                        <button className="ui-btn ui-btn--ghost ui-btn--sm" onClick={() => setSelected(null)}>
                            <FiArrowLeft aria-hidden="true" /> Todos os profissionais
                        </button>
                        <Link className="ui-btn ui-btn--sm" to={`/profissionais/${selected.id}`}>Ver perfil público</Link>
                    </div>
                    <section className="ui-card">
                        <div className="ui-person-head" style={{ marginBottom: 18 }}>
                            <Avatar name={selected.name} photo={selected.photo} size={52} />
                            <div>
                                <div className="ui-person-name">{selected.name}</div>
                                <div className="ui-person-sub">
                                    {brand.professionalLabel}{selected.registration ? ` · CRP ${selected.registration}` : ""}
                                </div>
                            </div>
                        </div>
                        {received ? <RatingSummary summary={received.summary} emptyText="Este profissional ainda não recebeu avaliações." />
                            : <Skeleton rows={3} />}
                    </section>
                    {received?.items?.length > 0 && (
                        <section className="ui-card">
                            <ReviewList
                                items={received.items}
                                renderMeta={(item) => <><span>de <strong>{item.authorName}</strong></span>{status(item)}</>}
                                renderActions={(item) => item.comment && item.commentStatus !== "PRIVATE" ? actions(item) : null}
                            />
                        </section>
                    )}
                </div>
            )}
        </div>
    );
}
