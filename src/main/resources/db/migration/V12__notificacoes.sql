-- Notificações do sistema para a equipa (sino no painel). Cada admin tem o seu estado de leitura.
CREATE TABLE notificacoes (
    id UUID PRIMARY KEY,
    tipo VARCHAR(40) NOT NULL,
    titulo VARCHAR(160) NOT NULL,
    mensagem VARCHAR(400),
    link VARCHAR(200),
    referencia_id UUID,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);

CREATE INDEX idx_notificacoes_criado_em ON notificacoes (criado_em DESC);

CREATE TABLE notificacao_leituras (
    notificacao_id UUID NOT NULL REFERENCES notificacoes (id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuarios (id),
    lida_em TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (notificacao_id, usuario_id)
);
