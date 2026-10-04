-- Lembrete enviado (para não repetir) e índice para a tarefa que procura as marcações de amanhã.
ALTER TABLE marcacoes ADD COLUMN lembrete_enviado_em TIMESTAMP;
CREATE INDEX idx_marcacoes_data_status ON marcacoes (data_marcacao, status);
