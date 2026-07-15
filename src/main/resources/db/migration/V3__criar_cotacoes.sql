CREATE TABLE cotacoes (
    id UUID PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    telefone VARCHAR(30) NOT NULL,
    email VARCHAR(180),
    mensagem TEXT,
    total_estimado NUMERIC(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NOVO',
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);

CREATE INDEX idx_cotacoes_status ON cotacoes (status);

CREATE TABLE cotacao_servicos (
    id UUID PRIMARY KEY,
    cotacao_id UUID NOT NULL REFERENCES cotacoes (id) ON DELETE CASCADE,
    servico_id UUID NOT NULL REFERENCES servicos (id),
    nome_servico VARCHAR(150) NOT NULL,
    preco_no_momento NUMERIC(12,2) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);

CREATE INDEX idx_cotacao_servicos_cotacao ON cotacao_servicos (cotacao_id);
