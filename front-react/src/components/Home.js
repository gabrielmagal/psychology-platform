import { useNavigate } from "react-router-dom";
import styles from "./Home.module.css";

export default function Home({ keycloak, realm }) {
  const navigate = useNavigate();

  return (
    <div className={styles.container}>
      <div className={styles.cardGrid}>
        <div className={styles.card} onClick={() => navigate("/usuarios")}>
          <h2>👥 Usuários</h2>
          <p>Gerencie pacientes e profissionais de forma organizada.</p>
        </div>
         <div className={styles.card} onClick={() => navigate("/sessions-package")}>
          <h2>🧠 Pacote de Sessões</h2>
          <p>Regisasdsaicas com facilidade.</p>
        </div>
        <div className={styles.card} onClick={() => navigate("/sessions")}>
          <h2>🧠 Sessões</h2>
          <p>Registre e acompanhe sessões terapêuticas com facilidade.</p>
        </div>
        <div className={styles.card} onClick={() => navigate("/payments")}>
          <h2>💳 Pagamentos</h2>
          <p>Visualize os pagamentos realizados e pendentes.</p>
        </div>
        <div className={styles.card} onClick={() => navigate("/annotations")}>
          <h2>📝 Anotações</h2>
          <p>Documente insights, sentimentos e intervenções clínicas.</p>
        </div>
      </div>

      <div className={styles.about}>
        <h2>Sobre a Plataforma</h2>
        <p>
          Desenvolvida para psicólogos e profissionais da saúde mental, esta plataforma tem como missão facilitar o acompanhamento terapêutico de forma ética, sensível e organizada.
        </p>
        <p>
          Acreditamos que cada sessão é um passo na jornada do autoconhecimento. Aqui, você pode registrar anotações importantes, acessar históricos e manter um relacionamento cuidadoso com seus pacientes.
        </p>
        <p>
          Com um ambiente acolhedor e intuitivo, oferecemos ferramentas que apoiam o trabalho clínico com segurança, respeito e humanidade.
        </p>
      </div>
    </div>
  );
}
