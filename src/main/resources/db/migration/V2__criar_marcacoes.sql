CREATE TABLE marcacoes (
    id UUID PRIMARY KEY,
    cliente_id UUID NOT NULL REFERENCES usuarios (id),
    data_marcacao DATE NOT NULL,
    hora_marcacao TIME NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'AGUARDA_CONFIRMACAO',
    valor_total NUMERIC(12,2) NOT NULL,
    valor_adiantamento NUMERIC(12,2) NOT NULL,
    metodo_pagamento VARCHAR(20),
    referencia_pagamento VARCHAR(60),
    adiantamento_pago BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);

CREATE INDEX idx_marcacoes_cliente ON marcacoes (cliente_id);
CREATE INDEX idx_marcacoes_data ON marcacoes (data_marcacao);
CREATE INDEX idx_marcacoes_status ON marcacoes (status);

CREATE TABLE marcacao_servicos (
    id UUID PRIMARY KEY,
    marcacao_id UUID NOT NULL REFERENCES marcacoes (id) ON DELETE CASCADE,
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

CREATE INDEX idx_marcacao_servicos_marcacao ON marcacao_servicos (marcacao_id);
