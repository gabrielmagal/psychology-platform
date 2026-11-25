-- O telefone deixa de ser único: numa clínica é comum mãe e filho, ou um casal, usarem o
-- mesmo número. Quem identifica a pessoa é o CPF.
ALTER TABLE user_info DROP CONSTRAINT IF EXISTS user_info_phone_number_key;
