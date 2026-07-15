-- Seed inicial de exemplo. Ajusta ou substitui pelos serviços reais que estão hoje
-- no servicesStore (localStorage) do frontend.

INSERT INTO servicos (id, nome, categoria, descricao, preco, duracao_minutos, destaque, ativo, criado_em) VALUES
    (gen_random_uuid(), 'Sessão Fotográfica Individual', 'Fotografia', 'Sessão em estúdio, 1 pessoa, com tratamento de imagem incluído.', 1500.00, 60, TRUE, TRUE, NOW()),
    (gen_random_uuid(), 'Sessão Fotográfica em Casal', 'Fotografia', 'Sessão em estúdio para casais, com tratamento de imagem incluído.', 2200.00, 90, TRUE, TRUE, NOW()),
    (gen_random_uuid(), 'Ensaio Corporativo', 'Fotografia', 'Fotos profissionais para uso em LinkedIn, currículos e sites institucionais.', 1800.00, 60, FALSE, TRUE, NOW()),
    (gen_random_uuid(), 'Cobertura de Evento (meio-dia)', 'Vídeo', 'Cobertura fotográfica e/ou em vídeo de eventos até 4 horas.', 4500.00, 240, TRUE, TRUE, NOW()),
    (gen_random_uuid(), 'Vídeo Institucional Curto', 'Vídeo', 'Produção de vídeo institucional de até 2 minutos.', 6000.00, 180, FALSE, TRUE, NOW()),
    (gen_random_uuid(), 'Edição e Retoque Avançado', 'Edição', 'Tratamento avançado de imagem por foto, incluindo retoque de pele e cor.', 250.00, 30, FALSE, TRUE, NOW());
