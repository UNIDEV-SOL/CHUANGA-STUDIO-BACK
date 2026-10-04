-- Galeria de trabalhos do site, gerida pela administradora.
-- Os bytes ficam numa tabela à parte para as listagens não os carregarem.
CREATE TABLE galeria_imagens (
    id UUID PRIMARY KEY,
    content_type VARCHAR(40) NOT NULL,
    dados BYTEA NOT NULL,
    miniatura BYTEA NOT NULL
);

CREATE TABLE galeria_itens (
    id UUID PRIMARY KEY,
    imagem_id UUID NOT NULL REFERENCES galeria_imagens (id),
    titulo VARCHAR(120),
    descricao VARCHAR(400),
    categoria VARCHAR(80),
    largura INTEGER,
    altura INTEGER,
    ordem INTEGER NOT NULL DEFAULT 0,
    publicado BOOLEAN NOT NULL DEFAULT TRUE,
    destaque BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);

CREATE INDEX idx_galeria_itens_publicado ON galeria_itens (publicado, ordem);
