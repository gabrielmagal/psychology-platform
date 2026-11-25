import React from "react";

/** Nome e assinatura do produto. A cor e a tipografia ficam em theme.css. */
export const brand = {
    name: "MindHaven",
    tagline: "Gestão de consultório",
    // Como este produto chama quem atende. No núcleo (usuários, pagamento) o perfil é só "profissional".
    professionalLabel: "Psicólogo(a)",
};

/** Marca: um círculo aberto, a ideia de espaço seguro. Herda a cor do texto ao redor. */
export function BrandMark({ size = 28 }) {
    return (
        <svg width={size} height={size} viewBox="0 0 32 32" fill="none" aria-hidden="true">
            <rect width="32" height="32" rx="9" fill="currentColor" opacity="0.16" />
            <path
                d="M22.5 11.2A8.5 8.5 0 1 0 24.5 16"
                stroke="currentColor"
                strokeWidth="2.6"
                strokeLinecap="round"
            />
            <circle cx="16" cy="16" r="3" fill="currentColor" />
        </svg>
    );
}
