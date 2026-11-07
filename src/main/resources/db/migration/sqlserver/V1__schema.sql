-- =============================================================================
-- Vê AI · SQL Server / Azure SQL · V1 · Schema
-- Mesmo modelo da versão PostgreSQL. Também é o script-bd.sql da entrega de DevOps.
-- Textos em NVARCHAR (acentos) e datas em DATETIMEOFFSET (fuso preservado).
-- =============================================================================

CREATE TABLE regiao (
    id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    nome       NVARCHAR(80) NOT NULL,
    cidade     NVARCHAR(80) NOT NULL,
    uf         NVARCHAR(2)  NOT NULL,
    latitude   FLOAT        NOT NULL,
    longitude  FLOAT        NOT NULL,
    CONSTRAINT uk_regiao_nome_cidade UNIQUE (nome, cidade)
);

CREATE TABLE usuario (
    id                   BIGINT IDENTITY(1,1) PRIMARY KEY,
    nome                 NVARCHAR(120)     NOT NULL,
    email                NVARCHAR(160)     NOT NULL,
    senha_hash           NVARCHAR(100)     NOT NULL,
    papel                NVARCHAR(10)      NOT NULL,
    velocidade_voz       FLOAT             NOT NULL CONSTRAINT df_usuario_velocidade DEFAULT 1.0,
    pergunta_livre_ativa BIT               NOT NULL CONSTRAINT df_usuario_pergunta DEFAULT 0,
    criado_em            DATETIMEOFFSET(6) NOT NULL,
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT ck_usuario_papel CHECK (papel IN ('CIDADAO', 'GESTOR')),
    CONSTRAINT ck_usuario_velocidade CHECK (velocidade_voz BETWEEN 0.5 AND 2.0)
);

CREATE TABLE gestor_regiao (
    gestor_id BIGINT NOT NULL CONSTRAINT fk_gestor_regiao_gestor REFERENCES usuario (id) ON DELETE CASCADE,
    regiao_id BIGINT NOT NULL CONSTRAINT fk_gestor_regiao_regiao REFERENCES regiao (id) ON DELETE CASCADE,
    CONSTRAINT pk_gestor_regiao PRIMARY KEY (gestor_id, regiao_id)
);

CREATE TABLE descricao (
    id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
    usuario_id         BIGINT            NOT NULL CONSTRAINT fk_descricao_usuario REFERENCES usuario (id),
    modo               NVARCHAR(10)      NOT NULL,
    resumo             NVARCHAR(500)     NOT NULL,
    descricao_completa NVARCHAR(4000)    NOT NULL,
    perigos            NVARCHAR(MAX)     NOT NULL CONSTRAINT df_descricao_perigos DEFAULT '[]',
    objetos            NVARCHAR(MAX)     NOT NULL CONSTRAINT df_descricao_objetos DEFAULT '[]',
    barreira_detectada BIT               NOT NULL CONSTRAINT df_descricao_barreira DEFAULT 0,
    barreira_tipo      NVARCHAR(30),
    latencia_ms        INT               NOT NULL,
    util               BIT,
    criado_em          DATETIMEOFFSET(6) NOT NULL,
    CONSTRAINT ck_descricao_modo CHECK (modo IN ('AMBIENTE', 'TEXTO')),
    CONSTRAINT ck_descricao_perigos_json CHECK (ISJSON(perigos) = 1),
    CONSTRAINT ck_descricao_objetos_json CHECK (ISJSON(objetos) = 1),
    CONSTRAINT ck_descricao_barreira_tipo CHECK (barreira_tipo IS NULL OR barreira_tipo IN
        ('CALCADA_DANIFICADA', 'OBSTRUCAO_PERMANENTE', 'PISO_TATIL', 'RAMPA', 'SEMAFORO_SEM_SOM', 'OBRA_SEM_SINALIZACAO', 'OUTRO'))
);
CREATE INDEX ix_descricao_usuario ON descricao (usuario_id, criado_em DESC);

CREATE TABLE relato (
    id               BIGINT IDENTITY(1,1) PRIMARY KEY,
    cidadao_id       BIGINT            NOT NULL CONSTRAINT fk_relato_cidadao REFERENCES usuario (id),
    descricao_id     BIGINT            CONSTRAINT fk_relato_descricao REFERENCES descricao (id) ON DELETE SET NULL,
    regiao_id        BIGINT            NOT NULL CONSTRAINT fk_relato_regiao REFERENCES regiao (id),
    tipo             NVARCHAR(30)      NOT NULL,
    gravidade        NVARCHAR(10)      NOT NULL,
    texto            NVARCHAR(1000)    NOT NULL,
    latitude         FLOAT             NOT NULL,
    longitude        FLOAT             NOT NULL,
    endereco         NVARCHAR(255),
    status           NVARCHAR(15)      NOT NULL CONSTRAINT df_relato_status DEFAULT 'ABERTO',
    protocolo_orgao  NVARCHAR(60),
    motivo_descarte  NVARCHAR(500),
    total_apoios     INT               NOT NULL CONSTRAINT df_relato_apoios DEFAULT 0,
    prioridade       INT               NOT NULL CONSTRAINT df_relato_prioridade DEFAULT 0,
    norma_referencia NVARCHAR(500),
    criado_em        DATETIMEOFFSET(6) NOT NULL,
    atualizado_em    DATETIMEOFFSET(6) NOT NULL,
    CONSTRAINT ck_relato_tipo CHECK (tipo IN
        ('CALCADA_DANIFICADA', 'OBSTRUCAO_PERMANENTE', 'PISO_TATIL', 'RAMPA', 'SEMAFORO_SEM_SOM', 'OBRA_SEM_SINALIZACAO', 'OUTRO')),
    CONSTRAINT ck_relato_gravidade CHECK (gravidade IN ('BAIXA', 'MEDIA', 'ALTA')),
    CONSTRAINT ck_relato_status CHECK (status IN ('ABERTO', 'EM_TRIAGEM', 'ENCAMINHADO', 'RESOLVIDO', 'DESCARTADO')),
    CONSTRAINT ck_relato_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_relato_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_relato_encaminhado CHECK (status <> 'ENCAMINHADO' OR protocolo_orgao IS NOT NULL),
    CONSTRAINT ck_relato_descartado CHECK (status <> 'DESCARTADO' OR motivo_descarte IS NOT NULL)
);
CREATE INDEX ix_relato_dedup ON relato (tipo, status, latitude, longitude);
CREATE INDEX ix_relato_fila ON relato (regiao_id, status, prioridade DESC);
CREATE INDEX ix_relato_cidadao ON relato (cidadao_id, criado_em DESC);

CREATE TABLE apoio_relato (
    id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    relato_id  BIGINT            NOT NULL CONSTRAINT fk_apoio_relato REFERENCES relato (id) ON DELETE CASCADE,
    usuario_id BIGINT            NOT NULL CONSTRAINT fk_apoio_usuario REFERENCES usuario (id),
    criado_em  DATETIMEOFFSET(6) NOT NULL,
    CONSTRAINT uk_apoio_relato_usuario UNIQUE (relato_id, usuario_id)
);

CREATE TABLE atualizacao_relato (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    relato_id       BIGINT            NOT NULL CONSTRAINT fk_atualizacao_relato REFERENCES relato (id) ON DELETE CASCADE,
    gestor_id       BIGINT            NOT NULL CONSTRAINT fk_atualizacao_gestor REFERENCES usuario (id),
    status_anterior NVARCHAR(15)      NOT NULL,
    status_novo     NVARCHAR(15)      NOT NULL,
    comentario      NVARCHAR(500),
    criado_em       DATETIMEOFFSET(6) NOT NULL
);
CREATE INDEX ix_atualizacao_relato ON atualizacao_relato (relato_id, criado_em);

CREATE TABLE notificacao (
    id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    usuario_id BIGINT            NOT NULL CONSTRAINT fk_notificacao_usuario REFERENCES usuario (id),
    relato_id  BIGINT            CONSTRAINT fk_notificacao_relato REFERENCES relato (id) ON DELETE CASCADE,
    mensagem   NVARCHAR(300)     NOT NULL,
    lida       BIT               NOT NULL CONSTRAINT df_notificacao_lida DEFAULT 0,
    criado_em  DATETIMEOFFSET(6) NOT NULL
);
CREATE INDEX ix_notificacao_usuario ON notificacao (usuario_id, lida, criado_em DESC);

CREATE TABLE idempotencia (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    usuario_id      BIGINT            NOT NULL CONSTRAINT fk_idempotencia_usuario REFERENCES usuario (id),
    chave           NVARCHAR(100)     NOT NULL,
    hash_requisicao NVARCHAR(64)      NOT NULL,
    status_http     INT               NOT NULL,
    corpo_resposta  NVARCHAR(MAX)     NOT NULL,
    criado_em       DATETIMEOFFSET(6) NOT NULL,
    CONSTRAINT uk_idempotencia_usuario_chave UNIQUE (usuario_id, chave)
);
CREATE INDEX ix_idempotencia_criado ON idempotencia (criado_em);
