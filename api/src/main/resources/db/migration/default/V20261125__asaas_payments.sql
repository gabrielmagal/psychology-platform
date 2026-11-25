-- Pagamento online pelo Asaas (substitui o Mercado Pago).

-- Conta do Asaas de cada profissional: é ele quem recebe.
CREATE TABLE asaas_account (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL UNIQUE REFERENCES user_info(id) ON DELETE CASCADE,
    api_key VARCHAR(500) NOT NULL,
    sandbox BOOLEAN NOT NULL DEFAULT TRUE,
    webhook_token VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP
);

-- Link da cobrança aberta no Asaas, para o paciente poder voltar a ela.
ALTER TABLE payment ADD COLUMN invoice_url VARCHAR(500);

-- Busca do pagamento pelo identificador da cobrança (retorno e webhook).
CREATE INDEX IF NOT EXISTS idx_payment_payment_id ON payment (payment_id);

-- A tabela mercado_pago_info fica como está: a aplicação não a usa mais, mas nenhum dado
-- é apagado por esta migração.
