CREATE TABLE usuarios (
    id UUID PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(180) NOT NULL,
    senha_hash VARCHAR(255) NOT NULL,
    telefone VARCHAR(30),
    papel VARCHAR(20) NOT NULL DEFAULT 'CLIENTE',
    conta_ativa BOOLEAN NOT NULL DEFAULT TRUE,
    login_google BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);

CREATE UNIQUE INDEX uk_usuarios_email ON usuarios (email) WHERE deletado = FALSE;
CREATE INDEX idx_usuarios_papel ON usuarios (papel);

CREATE TABLE servicos (
    id UUID PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    categoria VARCHAR(80) NOT NULL,
    descricao TEXT,
    preco NUMERIC(12,2) NOT NULL,
    duracao_minutos INTEGER NOT NULL,
    destaque BOOLEAN NOT NULL DEFAULT FALSE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);

CREATE INDEX idx_servicos_categoria ON servicos (categoria);
CREATE INDEX idx_servicos_ativo ON servicos (ativo);
