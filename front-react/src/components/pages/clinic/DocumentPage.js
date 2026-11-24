import React, { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import dayjs from "dayjs";
import styles from "./Clinic.module.css";
import { useApi, errorMessage, money, formatDate, formatCpf, PAYMENT_METHOD_LABEL } from "./clinicApi";

/**
 * Documentos para impressão (ou "Salvar como PDF" do navegador): recibo de pagamento,
 * declaração de comparecimento e contrato terapêutico.
 */
export default function DocumentPage({ keycloak, realm, onUnauthorized, onForbidden }) {
    const { kind, id } = useParams();
    const navigate = useNavigate();
    const request = useApi({ keycloak, realm, onUnauthorized, onForbidden });
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        let cancelled = false;
        const load = async () => {
            if (kind === "recibo") return request(`/clinic/document/receipt/${id}`);
            if (kind === "comparecimento") return request(`/clinic/document/attendance/${id}`);
            if (kind === "contrato") {
                const [record, me] = await Promise.all([
                    request(`/clinic/patient-record/${id}`),
                    request(`/user/keycloak`),
                ]);
                return {
                    patient: record.patient,
                    psychologist: {
                        name: [me.firstName, me.lastName].filter(Boolean).join(" "),
                        cpf: me.cpf,
                        crp: me.crp,
                        address: me.address,
                        phoneNumber: me.phoneNumber,
                    },
                };
            }
            throw new Error("Documento desconhecido.");
        };
        load()
            .then((result) => { if (!cancelled) setData(result); })
            .catch((err) => { if (!cancelled) setError(errorMessage(err)); });
        return () => { cancelled = true; };
    }, [kind, id, request]);

    if (error) return <div className={styles.page}><div className={styles.error}>{error}</div></div>;
    if (!data) return <div className={styles.page}><p className={styles.muted}>Carregando documento…</p></div>;

    const patient = data.patient || {};
    const psychologist = data.psychologist || {};
    const today = dayjs().format("DD [de] MMMM [de] YYYY");
    const crp = psychologist.crp ? `CRP ${psychologist.crp}` : "CRP não informado";

    return (
        <div className={styles.page}>
            <div className={`${styles.toolbar} ${styles.noPrint}`} style={{ maxWidth: 800, margin: "0 auto 1rem" }}>
                <button className={styles.btn} onClick={() => window.print()}>Imprimir / salvar PDF</button>
                <button className={styles.btnGhost} onClick={() => navigate(-1)}>Voltar</button>
                {!psychologist.crp && (
                    <span className={styles.muted}>
                        O CRP do psicólogo não está cadastrado — preencha em Usuários para sair no documento.
                    </span>
                )}
            </div>

            <div className={styles.sheet}>
                {kind === "recibo" && (
                    <>
                        <h1>RECIBO Nº {data.number}</h1>
                        <p>
                            Recebi de <strong>{patient.name}</strong>, CPF {formatCpf(patient.cpf)}, a quantia de{" "}
                            <strong>{money(data.amount)}</strong>, paga em {formatDate(data.paymentDate)} por{" "}
                            {PAYMENT_METHOD_LABEL[data.paymentMethod] || "outro meio"}, referente a serviços de
                            psicologia{data.packageTitle ? ` (${data.packageTitle})` : ""}.
                        </p>
                        <p>Por ser verdade, firmo o presente recibo.</p>
                    </>
                )}

                {kind === "comparecimento" && (
                    <>
                        <h1>DECLARAÇÃO DE COMPARECIMENTO</h1>
                        <p>
                            Declaro, para os devidos fins, que <strong>{patient.name}</strong>, CPF{" "}
                            {formatCpf(patient.cpf)}, compareceu a atendimento psicológico no dia{" "}
                            <strong>{formatDate(data.date)}</strong>
                            {data.startTime ? `, às ${data.startTime}, com duração de ${data.durationMinutes} minutos` : ""}.
                        </p>
                    </>
                )}

                {kind === "contrato" && (
                    <>
                        <h1>CONTRATO DE PRESTAÇÃO DE SERVIÇOS PSICOLÓGICOS</h1>
                        <p>
                            <strong>Contratante:</strong> {patient.name}, CPF {formatCpf(patient.cpf)}
                            {patient.guardianName ? `, representado(a) por ${patient.guardianName}` : ""}.<br />
                            <strong>Contratado(a):</strong> {psychologist.name}, {crp}, CPF {formatCpf(psychologist.cpf)}.
                        </p>
                        <p>
                            <strong>1. Objeto.</strong> Prestação de atendimento psicológico em sessões com duração e
                            periodicidade combinadas entre as partes, na modalidade presencial ou online.
                        </p>
                        <p>
                            <strong>2. Sigilo.</strong> O atendimento é protegido pelo sigilo profissional, nos termos do
                            Código de Ética Profissional do Psicólogo. As informações só serão compartilhadas nas hipóteses
                            previstas em lei ou com autorização do contratante.
                        </p>
                        <p>
                            <strong>3. Faltas e remarcações.</strong> Remarcações devem ser pedidas com antecedência mínima
                            de 24 horas. A sessão não desmarcada nesse prazo é considerada realizada para fins de cobrança.
                        </p>
                        <p>
                            <strong>4. Honorários.</strong> Os valores e a forma de pagamento são os registrados no pacote
                            de sessões contratado, com emissão de recibo a cada pagamento.
                        </p>
                        <p>
                            <strong>5. Dados pessoais.</strong> Os dados e o prontuário são tratados conforme a Lei Geral de
                            Proteção de Dados (Lei 13.709/2018) e guardados pelo prazo exigido pelo Conselho Federal de
                            Psicologia.
                        </p>
                        <p>
                            <strong>6. Encerramento.</strong> Qualquer das partes pode encerrar o atendimento a qualquer
                            momento, recomendando-se uma sessão de fechamento.
                        </p>
                    </>
                )}

                <p style={{ marginTop: "2.5rem" }}>{today}.</p>

                <div className={styles.signature}>
                    <div className={styles.signatureLine} />
                    <div><strong>{psychologist.name}</strong></div>
                    <div>Psicólogo(a) — {crp}</div>
                    {kind === "recibo" && psychologist.cpf && <div>CPF {formatCpf(psychologist.cpf)}</div>}
                </div>

                {kind === "contrato" && (
                    <div className={styles.signature}>
                        <div className={styles.signatureLine} />
                        <div><strong>{patient.guardianName || patient.name}</strong></div>
                        <div>Contratante</div>
                    </div>
                )}
            </div>
        </div>
    );
}
