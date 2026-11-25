import React, { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { FiDownload, FiPrinter } from "react-icons/fi";
import styles from "./Clinic.module.css";
import { ErrorState, PageHeader, Skeleton } from "../../ui/Feedback";
import { saveBlob } from "../../../utils/fetchWithAuth";
import { useApi, useDownload } from "./clinicApi";

const KINDS = {
    recibo: { type: "RECIBO", title: "Recibo" },
    comparecimento: { type: "DECLARACAO", title: "Declaração de comparecimento" },
    contrato: { type: "CONTRATO", title: "Contrato terapêutico" },
    emitido: { type: null, title: "Documento emitido" },
};

/**
 * Documento em PDF. Recibo, declaração e contrato são emitidos pelo servidor, recebem número
 * e ficam guardados; aqui o PDF é exibido e pode ser baixado ou impresso.
 * `/documento/emitido/:id` abre um documento que já existe.
 */
export default function DocumentPage(props) {
    const { kind, id } = useParams();
    const navigate = useNavigate();
    const request = useApi(props);
    const download = useDownload(props);
    const [info, setInfo] = useState(null);
    const [file, setFile] = useState(null);
    const [error, setError] = useState(null);
    const config = KINDS[kind];

    useEffect(() => {
        if (!config) {
            setError("Documento desconhecido.");
            return undefined;
        }
        let cancelled = false;
        let objectUrl = null;
        (async () => {
            try {
                const issued = config.type
                    ? await request(`/clinic/documents`, { method: "POST", body: { type: config.type, referenceId: id } })
                    : { id };
                const { blob, fileName } = await download(`/clinic/documents/${issued.id}/pdf`);
                if (cancelled) return;
                objectUrl = URL.createObjectURL(blob);
                setInfo(issued);
                setFile({ url: objectUrl, blob, fileName: fileName || "documento.pdf" });
            } catch (err) {
                if (!cancelled) setError(err);
            }
        })();
        return () => {
            cancelled = true;
            if (objectUrl) URL.revokeObjectURL(objectUrl);
        };
    }, [config, id, request, download]);

    return (
        <div className={styles.page}>
            <PageHeader
                title={config?.title || "Documento"}
                subtitle={info?.number ? `Nº ${info.number} — guardado no sistema como foi emitido.` : undefined}
            >
                {file && (
                    <>
                        <button className="ui-btn ui-btn--primary" onClick={() => saveBlob(file.blob, file.fileName)}>
                            <FiDownload aria-hidden="true" /> Baixar PDF
                        </button>
                        <button className="ui-btn" onClick={() => window.open(file.url, "_blank", "noopener")}>
                            <FiPrinter aria-hidden="true" /> Abrir para imprimir
                        </button>
                    </>
                )}
                <button className="ui-btn" onClick={() => navigate(-1)}>Voltar</button>
            </PageHeader>

            {error && <ErrorState error={error} />}
            {!error && !file && <div className="ui-card"><Skeleton rows={8} /></div>}
            {file && (
                <iframe title={config.title} src={file.url}
                        style={{ width: "100%", height: "78vh", border: "1px solid var(--border)", borderRadius: "var(--radius-md)", background: "#fff" }} />
            )}
        </div>
    );
}
