-- 1) Diário: sono junto do humor.
ALTER TABLE mood_entry ADD COLUMN IF NOT EXISTS sleep_hours NUMERIC(4, 1);
ALTER TABLE mood_entry ADD COLUMN IF NOT EXISTS sleep_quality INT;

-- 2) Atendimento online: intercorrências técnicas da sessão (queda de conexão, troca de
--    plataforma). Faz parte do registro clínico e é cifrado pela aplicação.
ALTER TABLE session_info ADD COLUMN IF NOT EXISTS technical_issues TEXT;

-- 3) Tarefas combinadas entre as sessões.
CREATE TABLE patient_task (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    professional_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    due_date DATE,
    created_at TIMESTAMP NOT NULL,
    done_at TIMESTAMP,
    patient_note TEXT
);
CREATE INDEX IF NOT EXISTS idx_patient_task_patient ON patient_task (patient_id);

-- 4) Plano de segurança para crises (modelo Stanley-Brown): um por paciente e profissional.
CREATE TABLE safety_plan (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    professional_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    warning_signs TEXT,
    coping TEXT,
    distractions TEXT,
    support_people TEXT,
    professionals TEXT,
    safe_environment TEXT,
    reasons_to_live TEXT,
    updated_at TIMESTAMP,
    CONSTRAINT uq_safety_plan UNIQUE (patient_id, professional_id)
);

-- 5) Termos que o paciente aceita pelo aplicativo (hoje, o de atendimento online). O texto
--    aceito fica guardado junto: mudar o modelo depois não muda o que a pessoa assinou.
CREATE TABLE patient_term (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    professional_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    kind VARCHAR(40) NOT NULL,
    version VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    signed_at TIMESTAMP,
    signed_name VARCHAR(200)
);
CREATE INDEX IF NOT EXISTS idx_patient_term_patient ON patient_term (patient_id);
