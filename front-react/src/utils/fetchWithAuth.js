import { ApiError } from "./errors";

/**
 * Lê o corpo de uma resposta de erro uma única vez e extrai o que der: a mensagem escrita
 * para o usuário (`message`) e, nos erros internos, o código para o suporte.
 */
async function readError(response) {
    let body = "";
    try {
        body = await response.text();
    } catch {
        // sem corpo legível: fica só o status
    }

    let message = "";
    let code = null;
    if (body) {
        try {
            const data = JSON.parse(body);
            message = data?.message || "";
            code = data?.errorId || null;
            // Formato padrão do Quarkus para erro não tratado: {"details": "Error id <uuid>"}.
            if (!code && typeof data?.details === "string") {
                const match = data.details.match(/Error id ([\w-]+)/i);
                if (match) code = match[1];
            }
        } catch {
            // Texto puro (ex.: respostas 401/403 do filtro de autorização).
            if (body.length < 300 && !body.trim().startsWith("<")) message = body.trim();
        }
    }
    // Detalhe técnico de erro interno não é mensagem para o usuário.
    if (response.status >= 500 && response.status !== 502) message = "";

    return new ApiError({ status: response.status, message, code });
}

export async function fetchWithAuth(url, options = {}, { onUnauthorized, onForbidden } = {}) {
    let response;
    try {
        response = await fetch(url, options);
    } catch (err) {
        if (err?.name === "AbortError") throw err;
        // fetch só rejeita quando não houve resposta: rede fora, servidor fora ou CORS.
        throw new ApiError({ network: true, message: "Sem conexão com o servidor." });
    }

    if (!response.ok) {
        const error = await readError(response);
        if (response.status === 401 && onUnauthorized) onUnauthorized();
        if (response.status === 403 && onForbidden && !error.message) onForbidden();
        throw error;
    }

    return response;
}

/**
 * Chamada à API: monta `Authorization`/`Tenant` e devolve o JSON já desserializado.
 * Em caso de falha rejeita com um {@link ApiError}.
 */
export async function apiFetch(path, {
    keycloak,
    realm,
    method = "GET",
    body,
    signal,
    onUnauthorized,
    onForbidden,
} = {}) {
    const apiUrl = process.env.REACT_APP_API_URL;

    const headers = {
        Authorization: "Bearer " + keycloak.token,
        Tenant: realm,
    };
    if (body !== undefined) headers["Content-Type"] = "application/json";

    const response = await fetchWithAuth(
        `${apiUrl}${path}`,
        {
            method,
            headers,
            signal,
            ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
        },
        { onUnauthorized, onForbidden }
    );

    if (response.status === 204) return null;

    const text = await response.text();
    if (!text) return null;

    try {
        return JSON.parse(text);
    } catch {
        return text;
    }
}

/**
 * Baixa um arquivo da API (PDF, anexo, planilha) com o token do usuário — um link comum
 * não enviaria o cabeçalho de autorização. Devolve o Blob e o nome sugerido pelo servidor.
 */
export async function apiBlob(path, { keycloak, realm, onUnauthorized, onForbidden } = {}) {
    const response = await fetchWithAuth(
        `${process.env.REACT_APP_API_URL}${path}`,
        { headers: { Authorization: "Bearer " + keycloak.token, Tenant: realm } },
        { onUnauthorized, onForbidden }
    );
    const disposition = response.headers.get("Content-Disposition") || "";
    const match = disposition.match(/filename="?([^";]+)"?/);
    return { blob: await response.blob(), fileName: match ? match[1] : null };
}

/** Entrega um Blob ao usuário como download. */
export function saveBlob(blob, fileName) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName || "arquivo";
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/**
 * Compatibilidade com as telas antigas: devolve o erro (não só o texto), para que os
 * diálogos mostrem título, explicação e orientação. Devolve null se a requisição foi
 * cancelada.
 */
export function extractCleanErrorMessage(err) {
    if (err?.name === "AbortError") return null;
    return err || "Erro inesperado.";
}
