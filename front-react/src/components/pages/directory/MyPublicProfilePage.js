import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import { Alert, ErrorState, PageHeader, Skeleton } from "../../ui/Feedback";
import PublicProfile from "../../ui/PublicProfile";
import { TagInput } from "../../ui/Tags";
import { AutoTextarea } from "../../ui/SectionedNote";
import { RatingSummary } from "../../ui/Rating";
import ReviewList from "../../ui/Reviews";
import ErrorDialog from "../../shared/dialog/ErrorDialog";
import { useApi } from "../clinic/clinicApi";
import { brand } from "../../../theme/brand";
import styles from "../clinic/Clinic.module.css";

const TABS = [["editar", "Editar"], ["previa", "Pré-visualizar"], ["avaliacoes", "Avaliações recebidas"]];

const SUGGESTIONS = {
    specialties: ["Ansiedade", "Depressão", "Relacionamentos", "Luto", "Autoestima", "Estresse e burnout", "Sono",
        "TDAH", "Orientação profissional", "Parentalidade"],
    approaches: ["Terapia Cognitivo-Comportamental", "Psicanálise", "Gestalt-terapia", "Terapia Sistêmica",
        "Análise do Comportamento", "Humanista", "Terapia do Esquema", "ACT"],
    audiences: ["Adultos", "Adolescentes", "Crianças", "Casais", "Famílias", "Idosos"],
    languages: ["Português", "Inglês", "Espanhol", "Libras"],
};

const COMMENT_STATUS = {
    PRIVATE: ["Só você lê", "ui-badge"],
    PENDING: ["Aguardando aprovação da clínica", "ui-badge ui-badge--warning"],
    PUBLISHED: ["Publicado no seu perfil", "ui-badge ui-badge--success"],
    REJECTED: ["Não publicado", "ui-badge ui-badge--danger"],
};

/** O profissional monta a própria página de apresentação e acompanha as avaliações que recebe. */
export default function MyPublicProfilePage(props) {
    const request = useApi(props);
    const [tab, setTab] = useState("editar");
    const [profile, setProfile] = useState(null);
    const [reviews, setReviews] = useState(null);
    const [loadError, setLoadError] = useState(null);
    const [saveError, setSaveError] = useState(null);
    const [saving, setSaving] = useState(false);
    const [attempt, setAttempt] = useState(0);

    useEffect(() => {
        let cancelled = false;
        Promise.all([request("/directory/profile"), request("/directory/reviews")])
            .then(([mine, received]) => {
                if (cancelled) return;
                setProfile(mine);
                setReviews(received);
                setLoadError(null);
            })
            .catch((err) => { if (!cancelled && err?.name !== "AbortError") setLoadError(err); });
        return () => { cancelled = true; };
    }, [request, attempt]);

    const set = (key) => (value) => setProfile((current) => ({ ...current, [key]: value }));
    const field = (key) => ({ value: profile[key] ?? "", onChange: (e) => set(key)(e.target.value) });
    const check = (key) => ({ checked: !!profile[key], onChange: (e) => set(key)(e.target.checked) });

    const save = async (published) => {
        setSaving(true);
        try {
            const saved = await request("/directory/profile", { method: "PUT", body: { ...profile, published } });
            setProfile(saved);
            toast.success(published ? "Perfil publicado." : "Rascunho salvo. O perfil não está visível para os pacientes.");
        } catch (err) {
            if (err?.name !== "AbortError") setSaveError(err);
        } finally {
            setSaving(false);
        }
    };

    if (loadError) return <div className="ui-page"><ErrorState error={loadError} onRetry={() => setAttempt((n) => n + 1)} /></div>;
    if (!profile) return <div className="ui-page"><div className="ui-card"><Skeleton rows={8} /></div></div>;

    const bioLength = (profile.bio || "").trim().length;
    const preview = { ...profile, reviews: profile.showReviews ? reviews?.summary : undefined, comments: [] };

    return (
        <div className="ui-page">
            <PageHeader title="Meu perfil público"
                        subtitle="É assim que os pacientes da clínica conhecem você antes da primeira sessão.">
                <span className={`ui-badge ${profile.published ? "ui-badge--success" : "ui-badge--warning"}`}>
                    {profile.published ? "Publicado" : "Rascunho"}
                </span>
                {profile.published && <Link className="ui-btn" to={`/profissionais/${profile.id}`}>Ver como paciente</Link>}
            </PageHeader>

            <div className={styles.tabs} role="tablist">
                {TABS.map(([id, label]) => (
                    <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? styles.tabActive : ""}
                            onClick={() => setTab(id)}>
                        {label}
                        {id === "avaliacoes" && reviews?.summary?.count ? <span className={styles.tabCount}>{reviews.summary.count}</span> : null}
                    </button>
                ))}
            </div>

            {tab === "editar" && (
                <div className="ui-stack">
                    <Alert tone="info" title="Nome, foto e CRP vêm do seu cadastro">
                        Para trocar a foto, use <Link to="/perfil">Meus dados</Link>. O Código de Ética pede que a
                        divulgação traga nome e CRP, informe só títulos que você possui e não prometa resultados nem
                        use o preço como chamariz.
                    </Alert>

                    <section className="ui-card">
                        <h2 className="ui-card-title">Apresentação</h2>
                        <div className="ui-form">
                            <label className="ui-field">
                                Em uma linha
                                <input maxLength={160} placeholder="Ex.: Psicóloga clínica · atendimento de adultos e casais" {...field("headline")} />
                            </label>
                            <label className="ui-field">
                                Sobre você
                                <AutoTextarea minRows={5} maxLength={4000} value={profile.bio} onChange={set("bio")}
                                              placeholder="Como você trabalha, com quem costuma atender e o que a pessoa pode esperar das sessões." />
                                <span className="ui-field-hint">
                                    {bioLength < 40 ? `Faltam ${40 - bioLength} caracteres para poder publicar.` : `${bioLength} de 4000 caracteres.`}
                                </span>
                            </label>
                            <label className="ui-field">
                                Formação
                                <AutoTextarea minRows={3} maxLength={2000} value={profile.education} onChange={set("education")}
                                              placeholder={"Graduação em Psicologia — Universidade, ano\nEspecialização em …"} />
                            </label>
                        </div>
                    </section>

                    <section className="ui-card">
                        <h2 className="ui-card-title">Como você atua</h2>
                        <div className="ui-form">
                            {[["specialties", "Áreas de atuação", "Ex.: Ansiedade"], ["approaches", "Abordagem", "Ex.: Psicanálise"],
                                ["audiences", "Quem você atende", "Ex.: Adultos"], ["languages", "Idiomas", "Ex.: Português"]]
                                .map(([key, label, placeholder]) => (
                                    <div key={key} className="ui-field">
                                        <label htmlFor={`tags-${key}`}>{label}</label>
                                        <TagInput id={`tags-${key}`} value={profile[key]} onChange={set(key)}
                                                  placeholder={placeholder} suggestions={SUGGESTIONS[key]} />
                                    </div>
                                ))}
                        </div>
                    </section>

                    <section className="ui-card">
                        <h2 className="ui-card-title">Atendimento</h2>
                        <div className="ui-form">
                            <div className="ui-row">
                                <label className="ui-check"><input type="checkbox" {...check("attendsOnline")} /><span>Atendo online</span></label>
                                <label className="ui-check"><input type="checkbox" {...check("attendsInPerson")} /><span>Atendo presencialmente</span></label>
                            </div>
                            <div className="ui-row">
                                <label className="ui-field">Cidade<input maxLength={120} placeholder="Ex.: Barueri, SP" {...field("city")} /></label>
                                <label className="ui-field">
                                    Atua desde (ano)
                                    <input type="number" min="1950" max={new Date().getFullYear()} placeholder="Ex.: 2015" {...field("practicingSince")} />
                                </label>
                            </div>
                            <div className="ui-row">
                                <label className="ui-field">
                                    Duração da sessão (minutos)
                                    <input type="number" min="10" max="240" placeholder="50" {...field("sessionMinutes")} />
                                </label>
                                <label className="ui-field">
                                    Valor da sessão (opcional)
                                    <input type="number" min="0" step="0.01" placeholder="Deixe vazio para não exibir" {...field("sessionPrice")} />
                                </label>
                            </div>
                            <label className="ui-check">
                                <input type="checkbox" checked={profile.acceptingClients !== false}
                                       onChange={(e) => set("acceptingClients")(e.target.checked)} />
                                <span>Estou recebendo novos atendimentos</span>
                            </label>
                        </div>
                    </section>

                    <section className="ui-card">
                        <h2 className="ui-card-title">Avaliações</h2>
                        <label className="ui-check">
                            <input type="checkbox" {...check("showReviews")} />
                            <span>
                                Mostrar minha nota e os comentários aprovados no perfil
                                <small>
                                    Desligado, as avaliações continuam chegando só para você, como retorno do seu trabalho.
                                    O Conselho Federal de Psicologia não recomenda o uso de depoimentos de pessoas atendidas
                                    (Nota Técnica 1/2022); a decisão e a responsabilidade pelo que fica exposto são suas.
                                </small>
                            </span>
                        </label>
                    </section>

                    <div className="ui-toolbar">
                        <button className="ui-btn ui-btn--primary" disabled={saving || bioLength < 40} onClick={() => save(true)}>
                            {saving ? "Salvando…" : profile.published ? "Salvar alterações" : "Publicar perfil"}
                        </button>
                        <button className="ui-btn" disabled={saving} onClick={() => save(false)}>
                            {profile.published ? "Tirar do ar e salvar" : "Salvar rascunho"}
                        </button>
                    </div>
                </div>
            )}

            {tab === "previa" && (
                <PublicProfile profile={preview} roleLabel={brand.professionalLabel} registrationLabel="CRP" />
            )}

            {tab === "avaliacoes" && (
                <div className="ui-stack">
                    <Alert tone="info" title="As avaliações são anônimas para você">
                        Quem avalia é o paciente, depois de uma sessão realizada. Você vê a nota e o comentário, não o nome.
                    </Alert>
                    <section className="ui-card"><RatingSummary summary={reviews?.summary} emptyText="Você ainda não recebeu avaliações." /></section>
                    {reviews?.items?.length > 0 && (
                        <section className="ui-card">
                            <ReviewList items={reviews.items} renderMeta={(item) => {
                                const [label, className] = COMMENT_STATUS[item.commentStatus] || COMMENT_STATUS.PRIVATE;
                                return item.comment ? <span className={className}>{label}</span> : null;
                            }} />
                        </section>
                    )}
                </div>
            )}

            <ErrorDialog open={!!saveError} message={saveError} onClose={() => setSaveError(null)} />
        </div>
    );
}
