-- Recuperação por código OTP de 6 dígitos (email → código → nova palavra-passe).
-- O código só existe em hash; o token_hash passa a ser o do token emitido depois de o código ser confirmado.
ALTER TABLE tokens_recuperacao_senha ADD COLUMN codigo_hash VARCHAR(64);
ALTER TABLE tokens_recuperacao_senha ADD COLUMN tentativas INT NOT NULL DEFAULT 0;
