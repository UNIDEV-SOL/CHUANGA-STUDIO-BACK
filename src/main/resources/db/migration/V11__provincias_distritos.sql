-- Classificadores geográficos (os dados entram pelo ProvinciasDistritosSeeder, como no SPO).
CREATE TABLE zonas_regionais (
    id SMALLINT PRIMARY KEY,
    nome VARCHAR(40) NOT NULL
);

CREATE TABLE provincias (
    id BIGINT PRIMARY KEY,
    codigo VARCHAR(10) NOT NULL UNIQUE,
    nome VARCHAR(80) NOT NULL,
    sigla VARCHAR(5) NOT NULL,
    zona_regional_id SMALLINT NOT NULL REFERENCES zonas_regionais (id)
);

CREATE TABLE distritos (
    id BIGINT PRIMARY KEY,
    codigo VARCHAR(10) NOT NULL,
    nome VARCHAR(80) NOT NULL,
    provincia_id BIGINT NOT NULL REFERENCES provincias (id)
);

CREATE INDEX idx_distritos_provincia ON distritos (provincia_id);

-- Morada da cliente (opcional).
ALTER TABLE usuarios ADD COLUMN provincia_id BIGINT REFERENCES provincias (id);
ALTER TABLE usuarios ADD COLUMN distrito_id BIGINT REFERENCES distritos (id);
