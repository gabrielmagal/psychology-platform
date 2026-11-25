import React from "react";
import { Link } from "react-router-dom";
import Avatar from "./Avatar";
import { TagList } from "./Tags";

/**
 * Cartão de apresentação de uma pessoa: foto, nome, uma linha de identificação, um texto
 * curto, etiquetas e um rodapé livre. Com `to`, o cartão inteiro vira link.
 */
export default function PersonCard({ to, name, photo, subtitle, text, tags, maxTags = 4, badge, footer }) {
    const body = (
        <>
            <div className="ui-person-head">
                <Avatar name={name} photo={photo} size={60} />
                <div style={{ minWidth: 0 }}>
                    <div className="ui-person-name">{name}</div>
                    {subtitle && <div className="ui-person-sub">{subtitle}</div>}
                    {badge && <div style={{ marginTop: 6 }}>{badge}</div>}
                </div>
            </div>
            {text && <p className="ui-person-text">{text}</p>}
            <TagList items={tags} max={maxTags} />
            {footer && <div className="ui-person-foot">{footer}</div>}
        </>
    );
    return to
        ? <Link to={to} className="ui-person-card">{body}</Link>
        : <div className="ui-person-card">{body}</div>;
}
