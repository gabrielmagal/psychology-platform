/**
 * Lê a mensagem de erro de uma resposta sem consumir o corpo duas vezes.
 *
 * O padrão espalhado pelas telas tentava `res.json()` e, no catch, `res.text()` — mas o
 * corpo de uma Response só pode ser lido uma vez, então o fallback sempre falhava e a
 * mensagem real do backend (que vem como texto puro nos 401/403) se perdia.
 */
async function readErrorMessage(response) {
    const fallback = `Erro ${response.status}`;
    let body;

    try {
        body = await response.text();
    } catch {
        return fallback;
    }

    if (!body) return fallback;

    try {
        const data = JSON.parse(body);
        return data?.details || data?.message || fallback;
    } catch {
        return body;
    }
}

export async function fetchWithAuth(url, options = {}, { onUnauthorized, onForbidden } = {}) {
    const response = await fetch(url, options);

    if (!response.ok) {
        const errorMsg = await readErrorMessage(response);
        if (response.status === 401 && onUnauthorized) onUnauthorized();
        if (response.status === 403 && onForbidden) onForbidden();
        throw new Error(errorMsg);
    }

    return response;
}

/**
 * Wrapper de alto nível para as chamadas à API.
 *
 * Monta os headers `Authorization`/`Tenant` e devolve o JSON já desserializado. Antes cada
 * componente repetia esse cabeçalho e ~20 linhas de tratamento de erro que, na prática,
 * eram código morto: `fetchWithAuth` rejeita a promise em respostas não-ok, então o bloco
 * `else` dos chamadores nunca era alcançado.
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

    // Alguns endpoints (ex.: preferência de pagamento) devolvem texto puro, não JSON.
    try {
        return JSON.parse(text);
    } catch {
        return text;
    }
}

/** Normaliza a mensagem de erro para exibição. Devolve null quando a requisição foi cancelada. */
export function extractCleanErrorMessage(err) {
    if (err?.name === "AbortError") return null;
    if (typeof err?.message === "string") {
        const match = err.message.match(/: (.+)$/);
        return match ? match[1] : err.message;
    }
    return "Erro inesperado.";
}
