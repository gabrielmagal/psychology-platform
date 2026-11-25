import React from "react";
import { FiAlertTriangle, FiCheckCircle, FiInbox, FiInfo, FiWifiOff, FiXCircle } from "react-icons/fi";
import { describeError } from "../../utils/errors";

const TONE_ICON = {
    danger: FiXCircle,
    warning: FiAlertTriangle,
    info: FiInfo,
    success: FiCheckCircle,
};

/** Aviso dentro da página: título, explicação e, se couber, ações. */
export function Alert({ tone = "info", title, children, code, actions, icon }) {
    const Icon = icon || TONE_ICON[tone] || FiInfo;
    return (
        <div className={`ui-alert ui-alert--${tone}`} role={tone === "danger" ? "alert" : "status"}>
            <Icon className="ui-alert-icon" aria-hidden="true" />
            <div style={{ minWidth: 0 }}>
                {title && <div className="ui-alert-title">{title}</div>}
                {children && <div className="ui-alert-text">{children}</div>}
                {code && <div className="ui-alert-code">Código para o suporte: {code}</div>}
                {actions && <div className="ui-alert-actions">{actions}</div>}
            </div>
        </div>
    );
}

/**
 * Erro de carregamento no lugar do conteúdo: diz o que houve, o que fazer e oferece
 * "Tentar novamente". Aceita o erro bruto (ApiError) ou um texto.
 */
export function ErrorState({ error, onRetry, compact = false }) {
    const info = describeError(error);
    if (!info) return null;
    return (
        <div style={compact ? undefined : { margin: "8px 0 20px" }}>
            <Alert
                tone="danger"
                title={info.title}
                code={info.code}
                icon={info.status === 0 && error?.network ? FiWifiOff : undefined}
                actions={onRetry && (
                    <button type="button" className="ui-btn ui-btn--sm" onClick={onRetry}>Tentar novamente</button>
                )}
            >
                {info.message}
                {info.hint && <> {info.hint}</>}
            </Alert>
        </div>
    );
}

/** Lista vazia: explica por que está vazia e qual é o próximo passo. */
export function EmptyState({ icon: Icon = FiInbox, title, children, action }) {
    return (
        <div className="ui-state">
            <div className="ui-state-icon"><Icon aria-hidden="true" /></div>
            <div className="ui-state-title">{title}</div>
            {children && <div style={{ maxWidth: 440 }}>{children}</div>}
            {action && <div style={{ marginTop: 10 }}>{action}</div>}
        </div>
    );
}

/** Esqueleto de carregamento: mostra a forma do conteúdo em vez de um "Carregando...". */
export function Skeleton({ rows = 4, height = 18 }) {
    return (
        <div aria-busy="true" aria-label="Carregando" style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            {Array.from({ length: rows }, (_, i) => (
                <div key={i} className="ui-skeleton" style={{ height, width: `${92 - (i % 3) * 14}%` }} />
            ))}
        </div>
    );
}

export function PageHeader({ title, subtitle, children }) {
    return (
        <div className="ui-page-header">
            <div>
                <h1 className="ui-page-title">{title}</h1>
                {subtitle && <p className="ui-page-subtitle">{subtitle}</p>}
            </div>
            {children && <div className="ui-toolbar ui-no-print">{children}</div>}
        </div>
    );
}
