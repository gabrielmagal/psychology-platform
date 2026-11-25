import React from "react";

function initials(name) {
    const parts = String(name || "").trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return "?";
    const first = parts[0][0];
    const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
    return (first + last).toUpperCase();
}

/** Aceita a foto como URL, data URL ou base64 puro (como a API devolve). */
function source(photo) {
    if (!photo) return null;
    return /^(data:|https?:|blob:|\/)/.test(photo) ? photo : `data:image/png;base64,${photo}`;
}

/** Foto redonda da pessoa; sem foto, as iniciais do nome. */
export default function Avatar({ name, photo, size = 44, className = "" }) {
    const src = source(photo);
    return (
        <span className={`ui-avatar ${className}`} style={{ "--avatar-size": `${size}px` }}
              role="img" aria-label={name ? `Foto de ${name}` : "Sem foto"}>
            {src ? <img src={src} alt="" /> : initials(name)}
        </span>
    );
}
