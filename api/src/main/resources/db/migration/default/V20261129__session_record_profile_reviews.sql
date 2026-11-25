-- Registro clínico da sessão em campos estruturados (Resolução CFP 001/2009): demanda,
-- observações, procedimentos, evolução (já existia) e encaminhamento. O conteúdo é cifrado
-- pela aplicação, por isso o que estava em "resumo" e nas "anotações" antigas não é
-- convertido aqui: a aplicação o apresenta dentro dos campos novos e o grava neles no
-- primeiro salvamento.
ALTER TABLE session_info ADD COLUMN IF NOT EXISTS demand TEXT;
ALTER TABLE session_info ADD COLUMN IF NOT EXISTS observations TEXT;
ALTER TABLE session_info ADD COLUMN IF NOT EXISTS interventions TEXT;
ALTER TABLE session_info ADD COLUMN IF NOT EXISTS plan TEXT;
ALTER TABLE session_info ADD COLUMN IF NOT EXISTS addenda TEXT;
ALTER TABLE session_info ADD COLUMN IF NOT EXISTS risk_alert BOOLEAN;
ALTER TABLE session_info ADD COLUMN IF NOT EXISTS record_signed_at TIMESTAMP;

-- Perfil público de apresentação do profissional.
CREATE TABLE public_profile (
    user_id UUID PRIMARY KEY REFERENCES user_info(id) ON DELETE CASCADE,
    headline VARCHAR(160),
    bio TEXT,
    approaches TEXT,
    specialties TEXT,
    audiences TEXT,
    languages TEXT,
    education TEXT,
    practicing_since INT,
    session_price NUMERIC(12, 2),
    session_minutes INT,
    attends_online BOOLEAN NOT NULL DEFAULT FALSE,
    attends_in_person BOOLEAN NOT NULL DEFAULT FALSE,
    city VARCHAR(120),
    accepting_clients BOOLEAN NOT NULL DEFAULT TRUE,
    show_reviews BOOLEAN NOT NULL DEFAULT FALSE,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP
);

-- Avaliações: módulo genérico (avaliado, autor, contexto). Substitui session_rating, que
-- só sabia avaliar sessão e não guardava de quem era a nota.
CREATE TABLE review (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES user_info(id) ON DELETE CASCADE,
    context_type VARCHAR(40) NOT NULL,
    context_id UUID NOT NULL,
    rating INT NOT NULL,
    recommend BOOLEAN,
    comment TEXT,
    comment_status VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMP,
    CONSTRAINT uq_review_author_context UNIQUE (author_id, context_type, context_id)
);
CREATE INDEX IF NOT EXISTS idx_review_subject ON review (subject_id);

-- As avaliações de sessão já feitas passam para o módulo novo, como retorno privado.
INSERT INTO review (id, subject_id, author_id, context_type, context_id, rating, comment, comment_status, created_at)
SELECT r.id, p.professional_id, r.patient_id, 'SESSION', r.session_id, r.rating, r.comment, 'PRIVATE', r.created_at
FROM session_rating r
JOIN session_info s ON s.id = r.session_id
JOIN session_package p ON p.id = s.session_package_id
WHERE p.professional_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM session_rating d
                  WHERE d.session_id = r.session_id AND d.patient_id = r.patient_id AND d.id > r.id);
DROP TABLE session_rating;
