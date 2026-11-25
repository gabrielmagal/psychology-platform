-- Núcleo neutro + segunda leva de recursos do consultório.
-- Um comando por alteração, na sintaxe aceita pelo Postgres e pelo H2 dos testes.

-- 1) O perfil de quem atende deixa de se chamar "psicólogo" no núcleo (usuário/pagamento),
--    que será reaproveitado em outros sistemas.
ALTER TABLE session_package RENAME COLUMN psychologist_id TO professional_id;
ALTER TABLE patient_record RENAME COLUMN psychologist_id TO professional_id;
UPDATE user_info SET user_type = 'PROFISSIONAL' WHERE user_type = 'PSICOLOGO';

-- 2) Conteúdo clínico passa a ser cifrado no banco: o texto cifrado é maior que o original,
--    então as colunas de tamanho fixo viram TEXT. A anotação estruturada vira opcional —
--    a evolução da sessão pode ser um texto corrido.
ALTER TABLE session_info ADD COLUMN evolution TEXT;
ALTER TABLE session_info ALTER COLUMN summary SET DATA TYPE TEXT;
ALTER TABLE annotation ALTER COLUMN main_feeling SET DATA TYPE TEXT;
ALTER TABLE annotation ALTER COLUMN significant_events SET DATA TYPE TEXT;
ALTER TABLE annotation ALTER COLUMN current_phase SET DATA TYPE TEXT;
ALTER TABLE annotation ALTER COLUMN intervention SET DATA TYPE TEXT;
ALTER TABLE annotation ALTER COLUMN main_feeling DROP NOT NULL;
ALTER TABLE annotation ALTER COLUMN significant_events DROP NOT NULL;
ALTER TABLE annotation ALTER COLUMN current_phase DROP NOT NULL;
ALTER TABLE annotation ALTER COLUMN dominant_thought DROP NOT NULL;
ALTER TABLE annotation ALTER COLUMN intervention DROP NOT NULL;

-- 3) Sessão avulsa: um pacote de uma sessão só, criado na hora do agendamento.
ALTER TABLE session_package ADD COLUMN single_session BOOLEAN DEFAULT FALSE;

-- 4) Agenda: horário de atendimento e bloqueios (férias, compromissos) de cada profissional.
CREATE TABLE working_hours (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    weekday INT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_working_hours_owner ON working_hours (owner_id);

CREATE TABLE schedule_block (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    reason VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_schedule_block_owner ON schedule_block (owner_id, start_at);

-- 5) Documentos emitidos: PDF guardado, com numeração por tipo e ano.
CREATE TABLE issued_document (
    id UUID PRIMARY KEY,
    doc_type VARCHAR(30) NOT NULL,
    doc_year INT NOT NULL,
    doc_number INT NOT NULL,
    title VARCHAR(255),
    patient_id UUID REFERENCES user_info(id),
    professional_id UUID,
    reference_id UUID,
    issued_by UUID,
    issued_at TIMESTAMP NOT NULL,
    content BYTEA NOT NULL,
    CONSTRAINT uq_issued_document_number UNIQUE (doc_type, doc_year, doc_number)
);
CREATE INDEX IF NOT EXISTS idx_issued_document_patient ON issued_document (patient_id);

-- 6) Despesas do consultório (livro-caixa / Carnê-Leão).
CREATE TABLE expense (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    expense_date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    category VARCHAR(60),
    amount NUMERIC(19, 2) NOT NULL,
    deductible BOOLEAN DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_expense_owner_date ON expense (owner_id, expense_date);

-- 7) Anexos do prontuário (laudos, encaminhamentos). O arquivo fica no próprio banco: entra
--    no mesmo backup do prontuário e não depende de um serviço de armazenamento à parte.
CREATE TABLE patient_attachment (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    professional_id UUID,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120),
    size_bytes BIGINT,
    content BYTEA NOT NULL,
    description VARCHAR(255),
    uploaded_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_patient_attachment_patient ON patient_attachment (patient_id);

-- 8) Cobranças online: tabela do módulo de pagamento, sem referência ao domínio do sistema.
--    "payable" é o que está sendo pago (aqui, um pacote); quem sabe o que ele é é a aplicação.
CREATE TABLE online_charge (
    id UUID PRIMARY KEY,
    payable_id UUID NOT NULL,
    receiver_id UUID NOT NULL,
    gateway_charge_id VARCHAR(100) NOT NULL,
    invoice_url VARCHAR(500),
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    billing_type VARCHAR(30),
    created_at TIMESTAMP NOT NULL,
    paid_on DATE
);
CREATE INDEX IF NOT EXISTS idx_online_charge_payable ON online_charge (payable_id);
CREATE INDEX IF NOT EXISTS idx_online_charge_gateway ON online_charge (gateway_charge_id);
