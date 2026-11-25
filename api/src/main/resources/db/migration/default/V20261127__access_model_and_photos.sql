-- Modelo de acesso: Administrador > Profissional > Paciente.
-- O cadastro do paciente pode ser compartilhado entre profissionais; o que cada um registra
-- (anamnese, evolução, anexos, pagamentos) é dele.

-- Vínculo explícito entre paciente e profissional. Quem cadastrou o paciente e quem tem
-- pacote com ele já são considerados vinculados; esta tabela guarda o caso do paciente que
-- passa a ser atendido por outro profissional.
CREATE TABLE patient_professional (
    patient_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    professional_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    linked_at TIMESTAMP,
    PRIMARY KEY (patient_id, professional_id)
);
CREATE INDEX IF NOT EXISTS idx_patient_professional_pro ON patient_professional (professional_id);

-- A anamnese deixa de ser uma por paciente e passa a ser uma por paciente E profissional.
-- A tabela é recriada porque o nome da restrição antiga (única por paciente) varia de banco
-- para banco e não pode ser removido de forma portátil.
CREATE TABLE patient_record_v2 (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    professional_id UUID,
    chief_complaint TEXT,
    complaint_history TEXT,
    family_history TEXT,
    health_history TEXT,
    diagnostic_hypothesis TEXT,
    therapeutic_goals TEXT,
    observations TEXT,
    updated_at TIMESTAMP,
    CONSTRAINT uq_patient_record_owner UNIQUE (patient_id, professional_id)
);
INSERT INTO patient_record_v2 (id, patient_id, professional_id, chief_complaint, complaint_history, family_history,
                               health_history, diagnostic_hypothesis, therapeutic_goals, observations, updated_at)
SELECT id, patient_id, professional_id, chief_complaint, complaint_history, family_history,
       health_history, diagnostic_hypothesis, therapeutic_goals, observations, updated_at
FROM patient_record;
DROP TABLE patient_record;
ALTER TABLE patient_record_v2 RENAME TO patient_record;

-- Foto de perfil no próprio banco: deixa de depender de um serviço de arquivos à parte.
CREATE TABLE user_photo (
    user_id UUID PRIMARY KEY REFERENCES user_info(id) ON DELETE CASCADE,
    content BYTEA NOT NULL,
    updated_at TIMESTAMP
);
