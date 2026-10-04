-- Chat ao estilo WhatsApp: grupos com membros e administradoras, grupos por serviço,
-- comunidade (avisos + grupos), conversas diretas entre clientes, reações, favoritas,
-- documentos/áudio, fixar e arquivar conversas.

ALTER TABLE conversas ADD COLUMN descricao VARCHAR(500);
ALTER TABLE conversas ADD COLUMN foto_anexo_id UUID REFERENCES chat_anexos (id);
ALTER TABLE conversas ADD COLUMN servico_id UUID REFERENCES servicos (id);
-- COMUNIDADE (conversa geral) e AVISOS (só a equipa escreve): grupos fixos que não se apagam.
ALTER TABLE conversas ADD COLUMN sistema VARCHAR(20);
-- Grupo listado na comunidade; aberto = qualquer cliente pode entrar sozinha.
ALTER TABLE conversas ADD COLUMN comunidade BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversas ADD COLUMN aberto BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversas ADD COLUMN so_admins_editam BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE conversas ADD COLUMN mensagem_fixada_id UUID;
CREATE UNIQUE INDEX uk_conversas_servico ON conversas (servico_id) WHERE servico_id IS NOT NULL AND deletado = FALSE;
CREATE UNIQUE INDEX uk_conversas_sistema ON conversas (sistema) WHERE sistema IS NOT NULL AND deletado = FALSE;

-- Membros dos grupos e das conversas diretas (a PRIVADA cliente <-> estúdio dispensa: é de toda a equipa).
CREATE TABLE conversa_membros (
    conversa_id UUID NOT NULL REFERENCES conversas (id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuarios (id),
    admin BOOLEAN NOT NULL DEFAULT FALSE,
    entrou_em TIMESTAMP NOT NULL DEFAULT NOW(),
    -- Saiu (ou foi removida): a linha fica para não voltar a ser adicionada automaticamente.
    saiu_em TIMESTAMP,
    PRIMARY KEY (conversa_id, usuario_id)
);
CREATE INDEX idx_conversa_membros_usuario ON conversa_membros (usuario_id) WHERE saiu_em IS NULL;

-- Preferências por utilizador (junto da leitura).
ALTER TABLE conversa_leituras ALTER COLUMN lida_ate DROP NOT NULL;
ALTER TABLE conversa_leituras ADD COLUMN fixada BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversa_leituras ADD COLUMN arquivada BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversa_leituras ADD COLUMN silenciada BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE chat_anexos ALTER COLUMN content_type TYPE VARCHAR(120);
ALTER TABLE chat_anexos ADD COLUMN nome VARCHAR(200);
ALTER TABLE chat_anexos ADD COLUMN tamanho BIGINT;

-- TEXTO | IMAGEM | AUDIO | DOCUMENTO | SISTEMA ("Ana entrou no grupo")
ALTER TABLE mensagens ALTER COLUMN tipo TYPE VARCHAR(12);
ALTER TABLE mensagens ADD COLUMN encaminhada BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE mensagens ADD COLUMN duracao_segundos INTEGER;
CREATE INDEX idx_mensagens_conversa_tipo ON mensagens (conversa_id, tipo) WHERE deletado = FALSE AND apagada = FALSE;

CREATE TABLE mensagem_reacoes (
    mensagem_id UUID NOT NULL REFERENCES mensagens (id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuarios (id),
    emoji VARCHAR(16) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (mensagem_id, usuario_id)
);

CREATE TABLE mensagem_favoritas (
    mensagem_id UUID NOT NULL REFERENCES mensagens (id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuarios (id),
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (mensagem_id, usuario_id)
);

-- "Apagar para mim".
CREATE TABLE mensagem_ocultas (
    mensagem_id UUID NOT NULL REFERENCES mensagens (id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuarios (id),
    PRIMARY KEY (mensagem_id, usuario_id)
);

-- A comunidade que já existia passa a grupo fixo; cria-se o grupo de avisos.
UPDATE conversas SET sistema = 'COMUNIDADE', comunidade = TRUE,
       descricao = 'Espaço de conversa entre as clientes do Chuanga Studio. Seja simpática: a equipa modera.'
 WHERE id = (SELECT id FROM conversas WHERE tipo = 'GRUPO' AND deletado = FALSE ORDER BY criado_em LIMIT 1);
INSERT INTO conversas (id, tipo, titulo, sistema, comunidade, descricao, criado_em)
SELECT gen_random_uuid(), 'GRUPO', 'Comunidade Chuanga', 'COMUNIDADE', TRUE,
       'Espaço de conversa entre as clientes do Chuanga Studio. Seja simpática: a equipa modera.', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM conversas WHERE sistema = 'COMUNIDADE');
INSERT INTO conversas (id, tipo, titulo, sistema, comunidade, bloqueada, descricao, criado_em)
VALUES (gen_random_uuid(), 'GRUPO', 'Avisos Chuanga Studio', 'AVISOS', TRUE, TRUE,
        'Novidades, promoções e horários do estúdio. Só a equipa escreve aqui.', NOW());

-- Todas as pessoas na comunidade e nos avisos (a equipa como administradora).
INSERT INTO conversa_membros (conversa_id, usuario_id, admin)
SELECT c.id, u.id, u.papel = 'ADMIN'
  FROM conversas c CROSS JOIN usuarios u
 WHERE c.sistema IN ('COMUNIDADE', 'AVISOS') AND u.deletado = FALSE;

-- Um grupo por cada serviço já marcado, com as clientes que o marcaram.
INSERT INTO conversas (id, tipo, titulo, servico_id, comunidade, so_admins_editam, descricao, criado_em)
SELECT gen_random_uuid(), 'GRUPO', s.nome, s.id, TRUE, TRUE,
       'Para quem já fez ' || s.nome || ' no Chuanga Studio: dicas de cuidados, fotos e dúvidas.', NOW()
  FROM servicos s
 WHERE s.deletado = FALSE
   AND EXISTS (SELECT 1 FROM marcacao_servicos ms JOIN marcacoes m ON m.id = ms.marcacao_id
                WHERE ms.servico_id = s.id AND m.deletado = FALSE AND m.status <> 'CANCELADA');

INSERT INTO conversa_membros (conversa_id, usuario_id, admin)
SELECT DISTINCT c.id, m.cliente_id, FALSE
  FROM conversas c
  JOIN marcacao_servicos ms ON ms.servico_id = c.servico_id
  JOIN marcacoes m ON m.id = ms.marcacao_id AND m.deletado = FALSE AND m.status <> 'CANCELADA'
 WHERE c.servico_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO conversa_membros (conversa_id, usuario_id, admin)
SELECT c.id, u.id, TRUE
  FROM conversas c CROSS JOIN usuarios u
 WHERE c.servico_id IS NOT NULL AND u.papel = 'ADMIN' AND u.deletado = FALSE
ON CONFLICT DO NOTHING;
