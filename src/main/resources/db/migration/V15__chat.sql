-- Chat (mesma lógica do SPO): conversa privada cliente <-> estúdio e grupo da comunidade.
CREATE TABLE conversas (
    id UUID PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL,                 -- PRIVADA | GRUPO
    titulo VARCHAR(120) NOT NULL,
    cliente_id UUID REFERENCES usuarios (id),  -- só nas PRIVADA
    ultima_mensagem VARCHAR(300),
    ultima_mensagem_em TIMESTAMP,
    bloqueada BOOLEAN NOT NULL DEFAULT FALSE,  -- grupo bloqueado: só a equipa escreve
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);
CREATE UNIQUE INDEX uk_conversas_cliente ON conversas (cliente_id) WHERE tipo = 'PRIVADA' AND deletado = FALSE;
CREATE INDEX idx_conversas_ultima ON conversas (ultima_mensagem_em DESC);

-- Leitura por utilizador (as não lidas calculam-se a partir daqui).
CREATE TABLE conversa_leituras (
    conversa_id UUID NOT NULL REFERENCES conversas (id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuarios (id),
    lida_ate TIMESTAMP NOT NULL,
    PRIMARY KEY (conversa_id, usuario_id)
);

CREATE TABLE chat_anexos (
    id UUID PRIMARY KEY,
    content_type VARCHAR(40) NOT NULL,
    dados BYTEA NOT NULL
);

CREATE TABLE mensagens (
    id UUID PRIMARY KEY,
    conversa_id UUID NOT NULL REFERENCES conversas (id) ON DELETE CASCADE,
    remetente_id UUID NOT NULL REFERENCES usuarios (id),
    tipo VARCHAR(10) NOT NULL DEFAULT 'TEXTO',  -- TEXTO | IMAGEM
    conteudo VARCHAR(2000),
    anexo_id UUID REFERENCES chat_anexos (id),
    resposta_a UUID REFERENCES mensagens (id),
    editada_em TIMESTAMP,
    apagada BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);
CREATE INDEX idx_mensagens_conversa_criado ON mensagens (conversa_id, criado_em);
CREATE INDEX idx_mensagens_conversa_atualizado ON mensagens (conversa_id, atualizado_em);
