import React from "react";

/**
 * Gráfico de linha simples, em SVG, para acompanhar um número ao longo do tempo: pontuação
 * de escala, peso, frequência... `points` é `[{ label, value, flagged }]`, na ordem do tempo;
 * `max` fixa o topo do eixo (a pontuação máxima do instrumento, por exemplo).
 */
export default function LineChart({ points, max, min = 0, height = 180, unit = "", ariaLabel }) {
    const data = (points || []).filter((p) => typeof p.value === "number");
    if (data.length === 0) return null;

    const width = 640;
    const pad = { top: 26, right: 20, bottom: 30, left: 36 };
    const top = max ?? Math.max(...data.map((p) => p.value), min + 1);
    const innerWidth = width - pad.left - pad.right;
    const innerHeight = height - pad.top - pad.bottom;
    const x = (index) => pad.left + (data.length === 1 ? innerWidth / 2 : (index * innerWidth) / (data.length - 1));
    const y = (value) => pad.top + innerHeight - ((value - min) / (top - min || 1)) * innerHeight;
    const path = data.map((p, i) => `${i === 0 ? "M" : "L"}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(" ");
    const everyLabel = data.length <= 8;
    const summary = ariaLabel || `Evolução em ${data.length} registro(s): de ${data[0].value} a ${data[data.length - 1].value}${unit}.`;

    return (
        <svg className="ui-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label={summary}>
            {[min, (min + top) / 2, top].map((tick) => (
                <g key={tick}>
                    <line className="ui-chart-grid" x1={pad.left} x2={width - pad.right} y1={y(tick)} y2={y(tick)} />
                    <text className="ui-chart-axis" x={pad.left - 8} y={y(tick) + 4} textAnchor="end">{Math.round(tick)}</text>
                </g>
            ))}
            <path className="ui-chart-line" d={path} />
            {data.map((p, i) => (
                <g key={i}>
                    <circle className={`ui-chart-dot ${p.flagged ? "ui-chart-dot--flagged" : ""}`} cx={x(i)} cy={y(p.value)} r={p.flagged ? 6 : 4.5}>
                        <title>{`${p.label}: ${p.value}${unit}${p.flagged ? " (atenção)" : ""}`}</title>
                    </circle>
                    <text className="ui-chart-value" x={x(i)} y={y(p.value) - 10} textAnchor="middle">{p.value}</text>
                    {(everyLabel || i === 0 || i === data.length - 1) && (
                        <text className="ui-chart-axis" x={x(i)} y={height - 8} textAnchor="middle">{p.label}</text>
                    )}
                </g>
            ))}
        </svg>
    );
}
