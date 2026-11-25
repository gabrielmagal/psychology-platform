-- 1) Série de sessões: as criadas juntas numa repetição (semanal, quinzenal, mensal) levam o
--    mesmo identificador, para cancelar "esta e as próximas" de uma vez.
ALTER TABLE session_info ADD COLUMN IF NOT EXISTS series_id UUID;
CREATE INDEX IF NOT EXISTS idx_session_series ON session_info (series_id);

-- 2) Convênios. O cadastro é da clínica (como o de pacientes); o vínculo com o pacote fica em
--    tabela própria para não depender do formulário de pacotes.
CREATE TABLE health_insurance (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    ans_code VARCHAR(20),
    session_value NUMERIC(12, 2),
    payment_days INT,
    notes VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE package_insurance (
    package_id UUID PRIMARY KEY REFERENCES session_package(id) ON DELETE CASCADE,
    insurance_id UUID NOT NULL REFERENCES health_insurance(id),
    card_number VARCHAR(60)
);
CREATE INDEX IF NOT EXISTS idx_package_insurance_insurance ON package_insurance (insurance_id);

-- Guia, valor e datas de faturamento e repasse de cada sessão de convênio.
ALTER TABLE session_info ADD COLUMN IF NOT EXISTS insurance_guide VARCHAR(60);
ALTER TABLE session_info ADD COLUMN IF NOT EXISTS insurance_amount NUMERIC(12, 2);
ALTER TABLE session_info ADD COLUMN IF NOT EXISTS insurance_billed_at DATE;
ALTER TABLE session_info ADD COLUMN IF NOT EXISTS insurance_paid_at DATE;

-- 3) Receita Saúde: controle de quais recebimentos o profissional já lançou no programa da
--    Receita Federal (o lançamento em si é feito lá; não existe integração).
CREATE TABLE tax_receipt (
    payment_id UUID PRIMARY KEY REFERENCES payment(id) ON DELETE CASCADE,
    launched_at TIMESTAMP NOT NULL,
    launched_by UUID,
    receipt_number VARCHAR(60)
);

-- 4) Lista de espera. Quem espera pode ainda não ser paciente: nome e telefone bastam.
CREATE TABLE waitlist_entry (
    id UUID PRIMARY KEY,
    professional_id UUID REFERENCES user_info(id) ON DELETE CASCADE,
    patient_id UUID REFERENCES user_info(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    phone VARCHAR(50),
    preference VARCHAR(255),
    modality VARCHAR(20),
    notes VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'AGUARDANDO',
    created_at TIMESTAMP NOT NULL,
    created_by UUID,
    resolved_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_waitlist_professional ON waitlist_entry (professional_id, status);
