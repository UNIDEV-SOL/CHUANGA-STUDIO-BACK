-- Fatura emitida no fecho do atendimento (caixa), com os serviços realmente feitos.
CREATE SEQUENCE fatura_numero_seq START 1;

CREATE TABLE faturas (
    id UUID PRIMARY KEY,
    marcacao_id UUID NOT NULL REFERENCES marcacoes (id),
    numero VARCHAR(20) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    data_emissao TIMESTAMP NOT NULL,
    subtotal NUMERIC(12,2) NOT NULL,
    desconto NUMERIC(12,2) NOT NULL DEFAULT 0,
    adiantamento_deduzido NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_a_pagar NUMERIC(12,2) NOT NULL,
    forma_pagamento VARCHAR(20),
    referencia_pagamento VARCHAR(60),
    pago_em TIMESTAMP,
    observacoes TEXT,
    -- Link público do PDF (enviado por WhatsApp); não adivinhável.
    token_publico VARCHAR(64) NOT NULL UNIQUE,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);

-- Uma marcação só tem uma fatura válida; anulada, pode emitir-se outra.
CREATE UNIQUE INDEX uk_faturas_marcacao_ativa ON faturas (marcacao_id) WHERE status <> 'ANULADA' AND deletado = FALSE;
CREATE INDEX idx_faturas_status ON faturas (status);
CREATE INDEX idx_faturas_data_emissao ON faturas (data_emissao);

CREATE TABLE fatura_itens (
    id UUID PRIMARY KEY,
    fatura_id UUID NOT NULL REFERENCES faturas (id) ON DELETE CASCADE,
    servico_id UUID REFERENCES servicos (id),
    descricao VARCHAR(150) NOT NULL,
    quantidade INTEGER NOT NULL,
    preco_unitario NUMERIC(12,2) NOT NULL,
    total NUMERIC(12,2) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);

CREATE INDEX idx_fatura_itens_fatura ON fatura_itens (fatura_id);
