-- Índices de apoio às consultas mais frequentes da aplicação.
--
-- O schema inicial só criou PRIMARY KEY e UNIQUE. O Postgres NÃO cria índice automático
-- para chaves estrangeiras, então toda listagem de sessões/pagamentos de um pacote, e todo
-- DELETE em cascata a partir de session_package, faziam varredura sequencial das tabelas
-- filhas. Do mesmo jeito, a resolução do usuário logado (por keycloak_id) e o filtro de
-- visibilidade do psicólogo (por registered_by_keycloak_id) rodavam em cada requisição
-- sem índice de apoio.

-- Resolução do usuário logado a cada requisição autenticada.
-- (keycloak_id já é UNIQUE, portanto já indexado; registered_by_keycloak_id não era.)
CREATE INDEX IF NOT EXISTS idx_user_info_registered_by ON user_info (registered_by_keycloak_id);

-- Filtro por tipo de usuário nas telas de listagem.
CREATE INDEX IF NOT EXISTS idx_user_info_user_type ON user_info (user_type);

-- Chaves estrangeiras de session_package.
CREATE INDEX IF NOT EXISTS idx_session_package_patient ON session_package (patient_id);
CREATE INDEX IF NOT EXISTS idx_session_package_psychologist ON session_package (psychologist_id);

-- Filhas de session_package: usadas nas listagens e nos DELETE em cascata.
CREATE INDEX IF NOT EXISTS idx_session_info_package ON session_info (session_package_id);
CREATE INDEX IF NOT EXISTS idx_payment_package ON payment (session_package_id);
CREATE INDEX IF NOT EXISTS idx_annotation_session ON annotation (session_id);

-- Ordenação e busca da tela de auditoria.
CREATE INDEX IF NOT EXISTS idx_audit_log_timestamp ON audit_log (timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log (entity_name, entity_id);
