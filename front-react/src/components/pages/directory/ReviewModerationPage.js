import React, { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import { FiCheck, FiMessageSquare, FiX } from "react-icons/fi";
import { Alert, EmptyState, ErrorState, PageHeader, Skeleton } from "../../ui/Feedback";
import ReviewList from "../../ui/Reviews";
import { errorText } from "../../../utils/errors";
import { useApi } from "../clinic/clinicApi";

/** Fila de comentários que os pacientes autorizaram a publicar e a clínica ainda não avaliou. */
export default function ReviewModerationPage(props) {
    const request = useApi(props);
    const [items, setItems] = useState(null);
    const [error, setError] = useState(null);
    const [busy, setBusy] = useState(null);

    const load = useCallback(() => {
        request("/directory/reviews/moderation")
            .then((data) => { setItems(data || []); setError(null); })
            .catch((err) => { if (err?.name !== "AbortError") setError(err); });
    }, [request]);

    useEffect(() => { load(); }, [load]);

    const decide = async (item, action) => {
        setBusy(item.id);
        try {
            await request(`/directory/reviews/${item.id}/${action}`, { method: "PUT" });
            toast.success(action === "publish" ? "Comentário publicado no perfil." : "Comentário não será publicado.");
            setItems((current) => current.filter((other) => other.id !== item.id));
        } catch (err) {
            toast.error(errorText(err));
        } finally {
            setBusy(null);
        }
    };

    return (
        <div className="ui-page">
            <PageHeader title="Comentários para aprovar"
                        subtitle="Comentários que o paciente autorizou a exibir, sem o nome dele, no perfil do profissional." />

            <div className="ui-stack">
                <Alert tone="info" title="O que observar antes de publicar">
                    Não publique o que identifique o paciente ou o caso, prometa resultado ou compare profissionais.
                    Recusar o comentário não altera a nota: ela continua contando na média.
                </Alert>

                {error && <ErrorState error={error} onRetry={load} />}
                {!error && items === null && <div className="ui-card"><Skeleton rows={4} /></div>}
                {!error && items && items.length === 0 && (
                    <div className="ui-card">
                        <EmptyState icon={FiMessageSquare} title="Nenhum comentário aguardando">
                            Quando um paciente autorizar a exibição de um comentário, ele aparece aqui.
                        </EmptyState>
                    </div>
                )}
                {!error && items && items.length > 0 && (
                    <div className="ui-card">
                        <ReviewList
                            items={items}
                            renderMeta={(item) => <span>para <strong>{item.subjectName}</strong> · de {item.authorName}</span>}
                            renderActions={(item) => (
                                <>
                                    <button className="ui-btn ui-btn--primary ui-btn--sm" disabled={busy === item.id}
                                            onClick={() => decide(item, "publish")}><FiCheck aria-hidden="true" /> Publicar</button>
                                    <button className="ui-btn ui-btn--danger-soft ui-btn--sm" disabled={busy === item.id}
                                            onClick={() => decide(item, "reject")}><FiX aria-hidden="true" /> Não publicar</button>
                                </>
                            )}
                        />
                    </div>
                )}
            </div>
        </div>
    );
}
