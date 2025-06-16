import { useNavigate } from "react-router-dom";
import styles from "./Home.module.css";
import { useRoles } from "./shared/UseRoles";
import React from "react";

export default function Home({ keycloak, realm }) {
    const navigate = useNavigate();
    const { isAdmin, isPsychologist, isPatient } = useRoles(keycloak);

    return (
        <div className={styles.container}>
            <div className={styles.welcomeMessage}>
                {isAdmin && (
                    <>
                        <h2>👋 Bem-vindo, Administrador!</h2>
                        <p>
                            Você tem acesso total à plataforma. Gerencie usuários, sessões e
                            acompanhe toda a operação com flexibilidade e controle.
                        </p>
                    </>
                )}
                {isPsychologist && (
                    <>
                        <h2>🧠 Olá, Psicólogo!</h2>
                        <p>
                            Aqui você pode gerenciar seus pacientes, registrar sessões e
                            acompanhar o progresso terapêutico com praticidade e segurança.
                        </p>
                    </>
                )}
                {isPatient && (
                    <>
                        <h2>🙋‍♂️ Olá, Paciente!</h2>
                        <p>
                            Acompanhe sua jornada terapêutica, veja seus pacotes de sessões e
                            mantenha-se conectado com seu processo de autoconhecimento.
                        </p>
                    </>
                )}
            </div>

            <div className={styles.cardGrid}>
                {(isAdmin || isPsychologist) && (
                    <div className={styles.card} onClick={() => navigate("/usuarios")}>
                        <h2>👥 Usuários</h2>
                        <p>Gerencie pacientes e profissionais de forma organizada.</p>
                    </div>
                )}
                <div className={styles.card} onClick={() => navigate("/sessions-package")}>
                    <h2>🧠 Pacote de Sessões</h2>
                    {isAdmin && <p>Acompanhe todos os pacotes de sessões cadastrados no sistema.</p>}
                    {isPsychologist && <p>Gerencie os pacotes de sessões dos seus pacientes com organização.</p>}
                    {isPatient && <p>Veja seus pacotes de sessões e acompanhe seu histórico terapêutico.</p>}
                </div>
                <div className={styles.card} onClick={() => navigate("/audit-log")}>
                    <h2>🧠 Auditoria</h2>
                    {isAdmin && <p>Acompanhe todos os pacotes de sessões cadastrados no sistema.</p>}
                    {isPsychologist && <p>Gerencie os pacotes de sessões dos seus pacientes com organização.</p>}
                    {isPatient && <p>Veja seus pacotes de sessões e acompanhe seu histórico terapêutico.</p>}
                </div>
            </div>

            <div className={styles.about}>
                <h2>Sobre a Plataforma</h2>

                {isAdmin && (
                    <>
                        <p>
                            Esta plataforma foi concebida com o objetivo de oferecer controle total sobre o ambiente terapêutico digital. Como administrador, você pode acompanhar a operação em todos os níveis: desde o gerenciamento de usuários e permissões, até o controle sobre pacotes de sessões, pagamentos e segurança da informação.
                        </p>
                        <p>
                            Nosso compromisso é garantir que os profissionais da saúde mental possam atuar com tranquilidade, sabendo que a infraestrutura está estável, segura e eficiente. Ao fornecer um ambiente robusto e intuitivo, você garante que os psicólogos e pacientes tenham uma experiência acolhedora e fluida.
                        </p>
                        <p>
                            Com painéis claros, permissões bem definidas e rastreabilidade completa das ações, você terá total visibilidade sobre os dados e poderá tomar decisões com confiança e agilidade.
                        </p>
                    </>
                )}

                {isPsychologist && (
                    <>
                        <p>
                            Esta plataforma foi desenvolvida para apoiar psicólogos em sua prática clínica com sensibilidade, organização e segurança. Sabemos que cada sessão representa um momento único na jornada do paciente — por isso, oferecemos ferramentas que facilitam o registro de anotações, o gerenciamento de pacotes de sessões, o acompanhamento do progresso e a comunicação ética com os pacientes.
                        </p>
                        <p>
                            Ao centralizar seus atendimentos, documentos e históricos em um só lugar, você ganha tempo, reduz riscos e fortalece o vínculo terapêutico com mais presença e foco no cuidado.
                        </p>
                        <p>
                            Além disso, com recursos intuitivos e suporte contínuo, você poderá manter sua agenda organizada e seu atendimento cada vez mais humanizado e eficaz.
                        </p>
                    </>
                )}

                {isPatient && (
                    <>
                        <p>
                            A jornada do autoconhecimento é feita de pequenos passos — e você está dando um deles ao utilizar esta plataforma. Aqui, você poderá acompanhar seu histórico de sessões, visualizar pacotes contratados, manter-se conectado ao seu psicólogo e ter mais clareza sobre sua evolução pessoal e emocional.
                        </p>
                        <p>
                            O acompanhamento psicológico é uma ferramenta poderosa para lidar com desafios emocionais, melhorar relacionamentos, entender sentimentos e fortalecer sua saúde mental. Ter um espaço seguro e privado para isso é essencial — e é exatamente isso que oferecemos aqui.
                        </p>
                        <p>
                            Esta plataforma foi pensada para ser sua aliada: intuitiva, respeitosa com sua privacidade, e comprometida com seu bem-estar. Aproveite cada sessão como uma oportunidade de crescer, refletir e cuidar de si.
                        </p>
                    </>
                )}
            </div>
        </div>
    );
}
