import React, { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { FiArrowLeft, FiEdit3 } from "react-icons/fi";
import { Alert, ErrorState, Skeleton } from "../../ui/Feedback";
import PublicProfile from "../../ui/PublicProfile";
import { useApi } from "../clinic/clinicApi";
import { brand } from "../../../theme/brand";

/** Perfil público de um profissional, como os pacientes da clínica o veem. */
export default function ProfessionalProfilePage(props) {
    const { id } = useParams();
    const request = useApi(props);
    const [profile, setProfile] = useState(null);
    const [error, setError] = useState(null);
    const [attempt, setAttempt] = useState(0);

    useEffect(() => {
        let cancelled = false;
        setProfile(null);
        request(`/directory/professionals/${id}`)
            .then((data) => { if (!cancelled) { setProfile(data); setError(null); } })
            .catch((err) => { if (!cancelled && err?.name !== "AbortError") setError(err); });
        return () => { cancelled = true; };
    }, [request, id, attempt]);

    return (
        <div className="ui-page">
            <div style={{ marginBottom: 14 }}>
                <Link className="ui-btn ui-btn--ghost ui-btn--sm" to="/profissionais"><FiArrowLeft aria-hidden="true" /> Todos os profissionais</Link>
            </div>
            {error && <ErrorState error={error} onRetry={() => setAttempt((n) => n + 1)} />}
            {!error && !profile && <div className="ui-card"><Skeleton rows={6} /></div>}
            {profile && (
                <div className="ui-stack">
                    {!profile.published && (
                        <Alert tone="warning" title="Este perfil ainda é um rascunho">
                            Só o próprio profissional e a administração conseguem vê-lo. Os pacientes passam a ver quando ele for publicado.
                        </Alert>
                    )}
                    <PublicProfile
                        profile={profile}
                        roleLabel={brand.professionalLabel}
                        registrationLabel="CRP"
                        actions={profile.mine && (
                            <Link className="ui-btn" to="/meu-perfil-publico"><FiEdit3 aria-hidden="true" /> Editar perfil</Link>
                        )}
                    />
                </div>
            )}
        </div>
    );
}
