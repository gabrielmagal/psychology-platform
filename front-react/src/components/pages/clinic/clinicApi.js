import { useCallback } from "react";
import { apiFetch, apiBlob } from "../../../utils/fetchWithAuth";
import { errorText } from "../../../utils/errors";

/** Chamada à API já com token, tenant e tratamento de sessão expirada. */
export function useApi({ keycloak, realm, onUnauthorized, onForbidden }) {
    return useCallback(
        (path, options) => apiFetch(path, { keycloak, realm, onUnauthorized, onForbidden, ...options }),
        [keycloak, realm, onUnauthorized, onForbidden]
    );
}

/** Download autenticado de arquivo (PDF, anexo, planilha). */
export function useDownload({ keycloak, realm, onUnauthorized, onForbidden }) {
    return useCallback(
        (path) => apiBlob(path, { keycloak, realm, onUnauthorized, onForbidden }),
        [keycloak, realm, onUnauthorized, onForbidden]
    );
}

/** Texto do erro para avisos curtos; as telas preferem `ErrorState`, que explica mais. */
export function errorMessage(err, fallback) {
    return errorText(err, fallback);
}

export const STATUS_LABEL = {
    AGENDADA: "Agendada",
    REALIZADA: "Realizada",
    FALTA: "Falta",
    CANCELADA: "Cancelada",
};

export const MODALITY_LABEL = {
    PRESENCIAL: "Presencial",
    ONLINE: "Online",
};

export const PAYMENT_METHOD_LABEL = {
    PIX: "Pix",
    CASH: "dinheiro",
    TRANSFER: "transferência",
    BOLETO: "boleto",
    CARD: "cartão",
    OTHER: "outro meio",
};

export const MOODS = [
    { value: 1, emoji: "😞", label: "Muito mal" },
    { value: 2, emoji: "🙁", label: "Mal" },
    { value: 3, emoji: "😐", label: "Neutro" },
    { value: 4, emoji: "🙂", label: "Bem" },
    { value: 5, emoji: "😄", label: "Muito bem" },
];

export function money(value) {
    return Number(value || 0).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

export function formatDate(isoDate) {
    if (!isoDate) return "";
    const [year, month, day] = isoDate.slice(0, 10).split("-");
    return `${day}/${month}/${year}`;
}

export function formatCpf(cpf) {
    if (!cpf || cpf.length !== 11) return cpf || "";
    return `${cpf.slice(0, 3)}.${cpf.slice(3, 6)}.${cpf.slice(6, 9)}-${cpf.slice(9)}`;
}

/** Link "clique para conversar" do WhatsApp; assume telefone brasileiro quando falta o DDI. */
export function whatsappLink(phone, message) {
    const digits = String(phone || "").replace(/\D/g, "");
    if (digits.length < 10) return null;
    const number = digits.length <= 11 ? `55${digits}` : digits;
    return `https://wa.me/${number}?text=${encodeURIComponent(message)}`;
}
