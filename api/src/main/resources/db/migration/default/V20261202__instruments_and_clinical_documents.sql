-- 1) Instrumentos clínicos: escalas e formulários. Os modelos prontos (PHQ-9, GAD-7, WHO-5)
--    ficam no código; aqui ficam os que cada profissional cria.
CREATE TABLE form_template (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    definition TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Cada aplicação de um instrumento a um paciente. O modelo é copiado para a aplicação: mudar
-- o modelo depois não muda o que já foi respondido. As respostas são cifradas pela aplicação.
CREATE TABLE form_response (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    professional_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    template_code VARCHAR(60) NOT NULL,
    title VARCHAR(160) NOT NULL,
    definition TEXT NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    due_date DATE,
    repeat_days INT,
    answered_at TIMESTAMP,
    answered_by VARCHAR(20),
    answers TEXT,
    score INT,
    alert BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_form_response_patient ON form_response (patient_id, template_code);

-- 2) Documentos escritos pelo psicólogo (Resolução CFP 06/2019): atestado, relatório, laudo,
--    parecer. Ficam como rascunho até a emissão, que gera o PDF numerado em issued_document.
CREATE TABLE clinical_document (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    professional_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    kind VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    sections TEXT,
    issued_document_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_clinical_document_patient ON clinical_document (patient_id);

-- Modelos de texto do próprio profissional, para começar um documento já preenchido.
CREATE TABLE document_template (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    kind VARCHAR(30) NOT NULL,
    title VARCHAR(160) NOT NULL,
    sections TEXT,
    created_at TIMESTAMP NOT NULL
);
