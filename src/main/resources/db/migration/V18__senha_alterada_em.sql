-- Tokens JWT emitidos antes desta data deixam de valer (troca ou recuperação da palavra-passe).
ALTER TABLE usuarios ADD COLUMN senha_alterada_em TIMESTAMP;
