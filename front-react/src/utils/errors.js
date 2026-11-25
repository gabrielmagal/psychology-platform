/**
 * Erros de chamada à API, em linguagem de usuário.
 *
 * Toda falha vira um {@link ApiError} com o código HTTP e, quando o servidor mandou, a
 * mensagem escrita para o usuário. `describeError` transforma qualquer erro num trio
 * título / explicação / o que fazer, que é o que as telas mostram.
 */
export class ApiError extends Error {
    constructor({ status = 0, message = "", code = null, network = false } = {}) {
        super(message);
        this.name = "ApiError";
        this.status = status;
        this.code = code;
        this.network = network;
    }
}

const GENERIC = {
    400: {
        title: "Confira os dados informados",
        message: "Algum campo está incompleto ou em formato inválido.",
        hint: "Revise o formulário e tente de novo.",
    },
    401: {
        title: "Sua sessão expirou",
        message: "Por segurança, é preciso entrar de novo.",
        hint: "Faça login novamente para continuar.",
    },
    403: {
        title: "Você não tem permissão para isso",
        message: "Seu perfil não pode executar esta ação.",
        hint: "Se precisar deste acesso, fale com o responsável pela clínica.",
    },
    404: {
        title: "Registro não encontrado",
        message: "O item que você procura não existe mais ou foi removido.",
        hint: "Atualize a página e tente novamente.",
    },
    409: {
        title: "Não foi possível concluir",
        message: "A operação conflita com a situação atual do registro.",
        hint: null,
    },
    502: {
        title: "O serviço de pagamento não respondeu",
        message: "Não conseguimos concluir a operação com o provedor de pagamento.",
        hint: "Tente novamente em alguns instantes.",
    },
};

/** @returns {{title: string, message: string, hint: string|null, code: string|null, status: number}|null} */
export function describeError(err) {
    if (!err) return null;
    if (err.name === "AbortError") return null; // requisição cancelada de propósito
    if (typeof err === "string") {
        return { title: "Não foi possível concluir", message: err, hint: null, code: null, status: 0 };
    }

    if (err.network) {
        return {
            title: "Sem conexão com o servidor",
            message: "Não conseguimos falar com o sistema agora.",
            hint: "Verifique sua internet. Se ela estiver funcionando, o sistema pode estar fora do ar por instantes; tente de novo em um minuto.",
            code: null,
            status: 0,
        };
    }

    const status = err.status || 0;
    if (status >= 500 && status !== 502) {
        return {
            title: "Tivemos um problema do nosso lado",
            message: "A operação não foi concluída. Nenhum dado seu foi perdido.",
            hint: "Tente novamente. Se o erro continuar, informe o código abaixo ao suporte.",
            code: err.code || `HTTP ${status}`,
            status,
        };
    }

    const base = GENERIC[status] || {
        title: "Não foi possível concluir",
        message: "Aconteceu algo inesperado.",
        hint: "Tente novamente.",
    };
    return {
        title: base.title,
        // A mensagem do servidor, quando existe, é a explicação específica do caso.
        message: err.message || base.message,
        hint: base.hint,
        code: err.code || null,
        status,
    };
}

/** Só o texto, para avisos curtos (toast). */
export function errorText(err, fallback = "Não foi possível concluir a operação.") {
    const info = describeError(err);
    if (!info) return null;
    return info.message || info.title || fallback;
}
