import React from "react";
import { FiClock, FiDollarSign, FiGlobe, FiMapPin, FiMonitor, FiUsers } from "react-icons/fi";
import Avatar from "./Avatar";
import { TagList } from "./Tags";
import { RatingInline, RatingSummary } from "./Rating";
import ReviewList from "./Reviews";

function money(value) {
    return Number(value).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

function Section({ title, children }) {
    if (!children) return null;
    return (
        <section className="ui-card ui-profile-section">
            <h2>{title}</h2>
            {children}
        </section>
    );
}

/**
 * Página de apresentação de um profissional. Recebe o perfil pronto e só desenha: serve
 * tanto para a página pública quanto para a pré-visualização de quem está editando.
 *
 * `profile`: `{ name, photo, registration, headline, bio, approaches, specialties, audiences,
 * languages, education, practicingSince, sessionPrice, sessionMinutes, attendsOnline,
 * attendsInPerson, city, acceptingClients, reviews, comments }`. `reviews` ausente = o
 * profissional não exibe avaliações.
 */
export default function PublicProfile({ profile, roleLabel = "Profissional", registrationLabel = "Registro", actions,
                                          reviewAuthor = "Paciente" }) {
    const p = profile || {};
    const years = p.practicingSince ? new Date().getFullYear() - p.practicingSince : null;
    const modes = [p.attendsOnline && "Online", p.attendsInPerson && "Presencial"].filter(Boolean).join(" e ");
    const has = (list) => Array.isArray(list) && list.length > 0;

    return (
        <div className="ui-stack">
            <div className="ui-card">
                <div className="ui-profile-hero">
                    <Avatar name={p.name} photo={p.photo} size={112} />
                    <div style={{ flex: 1, minWidth: 220 }}>
                        <h1>{p.name}</h1>
                        <div className="ui-person-sub" style={{ marginTop: 4 }}>
                            {roleLabel}{p.registration ? ` · ${registrationLabel} ${p.registration}` : ""}
                        </div>
                        {p.headline && <p style={{ marginTop: 8, color: "var(--ink-700)" }}>{p.headline}</p>}
                        <div className="ui-profile-facts">
                            {p.reviews && <RatingInline summary={p.reviews} />}
                            {modes && <span><FiMonitor aria-hidden="true" /> {modes}</span>}
                            {p.city && <span><FiMapPin aria-hidden="true" /> {p.city}</span>}
                            {years != null && years > 0 && (
                                <span><FiClock aria-hidden="true" /> {years} {years === 1 ? "ano" : "anos"} de atuação</span>
                            )}
                        </div>
                    </div>
                    {actions && <div className="ui-toolbar">{actions}</div>}
                </div>
            </div>

            <div className="ui-profile-layout">
                <div className="ui-stack">
                    <Section title="Sobre">
                        {p.bio ? <p className="ui-prose">{p.bio}</p> : <p className="ui-muted">Sem texto de apresentação.</p>}
                    </Section>
                    <Section title="Formação">{p.education && <p className="ui-prose">{p.education}</p>}</Section>
                    {p.reviews && (
                        <Section title="Avaliações">
                            <RatingSummary summary={p.reviews} />
                            {has(p.comments) && (
                                <div style={{ marginTop: 20 }}>
                                    <ReviewList items={p.comments} author={reviewAuthor} />
                                </div>
                            )}
                        </Section>
                    )}
                </div>

                <aside className="ui-stack">
                    <section className="ui-card ui-profile-section">
                        <h2>Atendimento</h2>
                        <div className="ui-stack" style={{ gap: 10 }}>
                            <span className={`ui-badge ${p.acceptingClients === false ? "ui-badge--warning" : "ui-badge--success"}`}
                                  style={{ alignSelf: "flex-start" }}>
                                {p.acceptingClients === false ? "Agenda fechada no momento" : "Recebendo novos atendimentos"}
                            </span>
                            {modes && <span className="ui-profile-facts" style={{ margin: 0 }}><span><FiMonitor aria-hidden="true" /> {modes}</span></span>}
                            {p.sessionMinutes && (
                                <span className="ui-profile-facts" style={{ margin: 0 }}>
                                    <span><FiClock aria-hidden="true" /> Sessão de {p.sessionMinutes} minutos</span>
                                </span>
                            )}
                            {p.sessionPrice != null && (
                                <span className="ui-profile-facts" style={{ margin: 0 }}>
                                    <span><FiDollarSign aria-hidden="true" /> {money(p.sessionPrice)} por sessão</span>
                                </span>
                            )}
                        </div>
                    </section>
                    {has(p.specialties) && (
                        <section className="ui-card ui-profile-section"><h2>Áreas de atuação</h2><TagList items={p.specialties} /></section>
                    )}
                    {has(p.approaches) && (
                        <section className="ui-card ui-profile-section"><h2>Abordagem</h2><TagList items={p.approaches} /></section>
                    )}
                    {has(p.audiences) && (
                        <section className="ui-card ui-profile-section">
                            <h2><FiUsers aria-hidden="true" /> Atende</h2><TagList items={p.audiences} tone="neutral" />
                        </section>
                    )}
                    {has(p.languages) && (
                        <section className="ui-card ui-profile-section">
                            <h2><FiGlobe aria-hidden="true" /> Idiomas</h2><TagList items={p.languages} tone="neutral" />
                        </section>
                    )}
                </aside>
            </div>
        </div>
    );
}
