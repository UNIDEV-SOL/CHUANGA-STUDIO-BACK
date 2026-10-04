-- Equipa: cada marcação pode ficar com uma profissional; a capacidade do estúdio passa a ser o nº de profissionais ativas.
CREATE TABLE profissionais (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    telefone VARCHAR(30),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    ordem INTEGER NOT NULL DEFAULT 0,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);

ALTER TABLE marcacoes ADD COLUMN profissional_id UUID REFERENCES profissionais (id);
CREATE INDEX idx_marcacoes_profissional ON marcacoes (profissional_id);

-- Ficha da cliente: notas só visíveis para a equipa (alergias, preferências...).
ALTER TABLE usuarios ADD COLUMN notas_internas TEXT;
