export async function fetchWithAuth(url, options = {}, { onUnauthorized, onForbidden } = {}) {
    try {
        const response = await fetch(url, options);
        if (!response.ok) {
            let errorMsg = `Erro ${response.status}`;
            try {
                const data = await response.json();
                if (data?.details) {
                    errorMsg = data.details;
                } else if (data?.message) {
                    errorMsg = data.message;
                }
            } catch (e) {
            }
            if (response.status === 401 && onUnauthorized) onUnauthorized();
            if (response.status === 403 && onForbidden) onForbidden();
            return Promise.reject(new Error(errorMsg));
        }
        return response;
    } catch (err) {
        return Promise.reject(err);
    }
}