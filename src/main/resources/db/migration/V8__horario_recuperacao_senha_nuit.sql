-- Horário de funcionamento semanal (1 = segunda ... 7 = domingo, como java.time.DayOfWeek).
CREATE TABLE horario_funcionamento (
    id UUID PRIMARY KEY,
    dia_semana SMALLINT NOT NULL UNIQUE CHECK (dia_semana BETWEEN 1 AND 7),
    aberto BOOLEAN NOT NULL DEFAULT TRUE,
    abre TIME,
    fecha TIME,
    pausa_inicio TIME,
    pausa_fim TIME,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);

-- Valores iniciais (o estúdio ajusta no painel): segunda a sábado 08:00–19:00, domingo fechado.
INSERT INTO horario_funcionamento (id, dia_semana, aberto, abre, fecha) VALUES
    (gen_random_uuid(), 1, TRUE, '08:00', '19:00'),
    (gen_random_uuid(), 2, TRUE, '08:00', '19:00'),
    (gen_random_uuid(), 3, TRUE, '08:00', '19:00'),
    (gen_random_uuid(), 4, TRUE, '08:00', '19:00'),
    (gen_random_uuid(), 5, TRUE, '08:00', '19:00'),
    (gen_random_uuid(), 6, TRUE, '08:00', '19:00'),
    (gen_random_uuid(), 7, FALSE, NULL, NULL);

-- Feriados, férias, formação... dias em que não se aceitam marcações.
CREATE TABLE dias_fechados (
    id UUID PRIMARY KEY,
    data DATE NOT NULL,
    motivo VARCHAR(150),
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);

CREATE UNIQUE INDEX uk_dias_fechados_data ON dias_fechados (data) WHERE deletado = FALSE;

-- Recuperação de palavra-passe: guarda-se só o hash do token enviado por email.
CREATE TABLE tokens_recuperacao_senha (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuarios (id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_em TIMESTAMP NOT NULL,
    usado_em TIMESTAMP,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    criado_por VARCHAR(180),
    atualizado_por VARCHAR(180),
    deletado BOOLEAN NOT NULL DEFAULT FALSE,
    deletado_em TIMESTAMP
);

CREATE INDEX idx_tokens_recuperacao_usuario ON tokens_recuperacao_senha (usuario_id);

-- ISPC (2026): a fatura identifica o NUIT das duas partes; o do cliente é opcional.
ALTER TABLE faturas ADD COLUMN cliente_nuit VARCHAR(20);
