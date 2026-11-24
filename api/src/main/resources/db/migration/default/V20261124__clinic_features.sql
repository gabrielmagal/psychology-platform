-- Recursos de consultório: agenda, prontuário, financeiro, LGPD e acompanhamento do paciente.
-- Um ADD COLUMN por comando para valer tanto no Postgres quanto no H2 dos testes.

-- Agenda: a sessão passa a ter horário, duração, situação e modalidade.
ALTER TABLE session_info ADD COLUMN start_time TIME;
ALTER TABLE session_info ADD COLUMN duration_minutes INT DEFAULT 50;
ALTER TABLE session_info ADD COLUMN status VARCHAR(30) DEFAULT 'AGENDADA';
ALTER TABLE session_info ADD COLUMN modality VARCHAR(30) DEFAULT 'PRESENCIAL';
ALTER TABLE session_info ADD COLUMN meeting_link VARCHAR(500);
ALTER TABLE session_info ADD COLUMN confirmed_at TIMESTAMP;
ALTER TABLE session_info ADD COLUMN reminded_at TIMESTAMP;

-- Sessões antigas com presença registrada já aconteceram.
UPDATE session_info SET status = 'REALIZADA' WHERE attended_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_session_info_date ON session_info (date_session);

-- Cadastro: dados profissionais do psicólogo e dados complementares do paciente.
ALTER TABLE user_info ADD COLUMN crp VARCHAR(30);
ALTER TABLE user_info ADD COLUMN address VARCHAR(500);
ALTER TABLE user_info ADD COLUMN occupation VARCHAR(255);
ALTER TABLE user_info ADD COLUMN emergency_contact_name VARCHAR(255);
ALTER TABLE user_info ADD COLUMN emergency_contact_phone VARCHAR(50);
ALTER TABLE user_info ADD COLUMN guardian_name VARCHAR(255);
ALTER TABLE user_info ADD COLUMN guardian_phone VARCHAR(50);

-- Financeiro: valor combinado do pacote, para calcular o que falta receber.
ALTER TABLE session_package ADD COLUMN price NUMERIC(19, 2);

-- Prontuário: anamnese, uma por paciente.
CREATE TABLE patient_record (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL UNIQUE REFERENCES user_info(id) ON DELETE CASCADE,
    psychologist_id UUID,
    chief_complaint TEXT,
    complaint_history TEXT,
    family_history TEXT,
    health_history TEXT,
    diagnostic_hypothesis TEXT,
    therapeutic_goals TEXT,
    observations TEXT,
    updated_at TIMESTAMP
);

-- Diário de humor preenchido pelo paciente entre as sessões.
CREATE TABLE mood_entry (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    entry_date DATE NOT NULL,
    mood INT NOT NULL,
    note TEXT,
    created_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_mood_entry_patient ON mood_entry (patient_id, entry_date);

-- LGPD: registro de cada aceite do termo de consentimento (histórico, não um flag).
CREATE TABLE consent_record (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    version VARCHAR(30) NOT NULL,
    accepted_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_consent_record_user ON consent_record (user_id);

-- A entidade SessionRatingEntity existia sem tabela: toda avaliação de sessão falhava.
CREATE TABLE IF NOT EXISTS session_rating (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES session_info(id) ON DELETE CASCADE,
    patient_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    rating INT NOT NULL,
    comment TEXT,
    created_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_session_rating_session ON session_rating (session_id);
