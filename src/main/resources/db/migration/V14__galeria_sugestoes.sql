-- Sugestões de fotos pelas clientes: entram como PENDENTE e só aparecem no site depois de aprovadas.
ALTER TABLE galeria_itens ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'APROVADA';
ALTER TABLE galeria_itens ADD COLUMN sugerido_por UUID REFERENCES usuarios (id);
ALTER TABLE galeria_itens ADD COLUMN motivo_rejeicao VARCHAR(200);
CREATE INDEX idx_galeria_itens_estado ON galeria_itens (estado);
