import React from "react";
import { Alert } from "./Feedback";

/**
 * Última barreira: se uma tela quebrar ao renderizar, o usuário vê uma explicação e um
 * botão para recarregar — não uma página em branco.
 */
export default class ErrorBoundary extends React.Component {
    state = { failed: false };

    static getDerivedStateFromError() {
        return { failed: true };
    }

    componentDidCatch(error, info) {
        console.error("Falha ao renderizar a tela", error, info);
    }

    componentDidUpdate(prevProps) {
        // Trocar de página limpa o erro: a tela seguinte pode estar perfeita.
        if (this.state.failed && prevProps.resetKey !== this.props.resetKey) {
            this.setState({ failed: false });
        }
    }

    render() {
        if (!this.state.failed) return this.props.children;
        return (
            <div className="ui-page">
                <Alert
                    tone="danger"
                    title="Esta tela não pôde ser exibida"
                    actions={(
                        <button type="button" className="ui-btn ui-btn--sm" onClick={() => window.location.reload()}>
                            Recarregar a página
                        </button>
                    )}
                >
                    Aconteceu um erro inesperado ao montar a tela. Seus dados não foram alterados.
                    Recarregue a página; se o problema continuar, avise o suporte.
                </Alert>
            </div>
        );
    }
}
