-- Fidelização: configuração única (o painel ajusta).
CREATE TABLE fidelizacao_config (
    id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    visitas_por_recompensa INTEGER NOT NULL DEFAULT 10,
    valor_recompensa NUMERIC(12,2) NOT NULL DEFAULT 500,
    atualizado_em TIMESTAMP
);
INSERT INTO fidelizacao_config (id) VALUES (1);

-- Vales-oferta.
CREATE TABLE vales (
    id UUID PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    valor_inicial NUMERIC(12,2) NOT NULL,
    saldo NUMERIC(12,2) NOT NULL,
    comprador_nome VARCHAR(150),
    comprador_contacto VARCHAR(150),
    beneficiario_nome VARCHAR(150),
    beneficiario_email VARCHAR(180),
    mensagem VARCHAR(400),
    valido_ate DATE,
    estado VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);

-- Fatura: recompensa de fidelização e vale usados no fecho de conta.
ALTER TABLE faturas ADD COLUMN desconto_fidelizacao NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE faturas ADD COLUMN vale_id UUID REFERENCES vales (id);
ALTER TABLE faturas ADD COLUMN valor_vale NUMERIC(12,2) NOT NULL DEFAULT 0;

-- Avaliações pedidas por email depois do atendimento.
CREATE TABLE avaliacoes (
    id UUID PRIMARY KEY,
    marcacao_id UUID NOT NULL UNIQUE REFERENCES marcacoes (id),
    cliente_id UUID NOT NULL REFERENCES usuarios (id),
    token VARCHAR(64) NOT NULL UNIQUE,
    nota SMALLINT CHECK (nota BETWEEN 1 AND 5),
    comentario VARCHAR(1000),
    respondida_em TIMESTAMP,
    publicada BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);
CREATE INDEX idx_avaliacoes_publicada ON avaliacoes (publicada, respondida_em DESC);

-- Lembrete de manutenção: dias depois do serviço (nulo = sem lembrete).
ALTER TABLE servicos ADD COLUMN manutencao_dias INTEGER;
UPDATE servicos SET manutencao_dias = 21 WHERE categoria IN ('alongamento', 'manutencao');
UPDATE servicos SET manutencao_dias = 14 WHERE categoria = 'verniz';
ALTER TABLE marcacoes ADD COLUMN lembrete_manutencao_em TIMESTAMP;
