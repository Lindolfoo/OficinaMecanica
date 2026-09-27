-- =====================================================================
--  OFICINA MECÂNICA - Sistema de Ordens de Serviço
--  Disciplina: Banco de Dados (NP1) - UNIP
--  Script DDL - SQLite 3
--
--  Idempotente: pode ser executado várias vezes sem erro (IF NOT EXISTS).
--  Executado automaticamente pela aplicação na subida (db.autoSchema=true).
--
--  DIFERENÇAS EM RELAÇÃO À VERSÃO MySQL (ver docs/MIGRACAO-SQLITE.md):
--    - não existe CREATE DATABASE nem USE: o banco é o próprio arquivo
--    - AUTO_INCREMENT              -> INTEGER PRIMARY KEY AUTOINCREMENT
--    - INT UNSIGNED / TINYINT      -> INTEGER (SQLite não tem UNSIGNED)
--    - ENUM(...)                   -> TEXT + CHECK (coluna IN (...))
--    - REGEXP                      -> GLOB (o SQLite não traz REGEXP embutido)
--    - LIKE com '\'                -> exige ESCAPE '\' explícito
--    - CURRENT_TIMESTAMP grava UTC -> usamos datetime('now','localtime')
--    - ÍNDICES                     -> fora do CREATE TABLE
--
--  ATENÇÃO: as chaves estrangeiras do SQLite vêm DESLIGADAS por padrão.
--  Quem liga é a aplicação, com PRAGMA foreign_keys = ON em toda conexão
--  (ver db/Database.java). Sem isso, RN04, RN06 e RN15 deixam de valer
--  silenciosamente — nenhum erro aparece, a integridade é que some.
-- =====================================================================

-- ---------------------------------------------------------------------
--  CLIENTE: proprietário dos veículos atendidos pela oficina
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cliente (
  id          INTEGER      NOT NULL,
  nome        TEXT         NOT NULL,
  cpf         TEXT         NOT NULL,                      -- somente dígitos
  telefone    TEXT         NULL,
  email       TEXT         NULL,
  criado_em   TEXT         NOT NULL DEFAULT (datetime('now', 'localtime')),
  CONSTRAINT pk_cliente        PRIMARY KEY (id AUTOINCREMENT),
  CONSTRAINT uq_cliente_cpf    UNIQUE (cpf),
  -- GLOB no lugar do REGEXP: 11 dígitos, nem mais nem menos
  CONSTRAINT ck_cliente_cpf    CHECK (cpf GLOB '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'),
  CONSTRAINT ck_cliente_nome   CHECK (LENGTH(nome) BETWEEN 2 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_cliente_nome ON cliente (nome);

-- ---------------------------------------------------------------------
--  VEICULO: um cliente possui N veículos (1:N)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS veiculo (
  id          INTEGER      NOT NULL,
  cliente_id  INTEGER      NOT NULL,
  placa       TEXT         NOT NULL,                      -- ABC1D23 (Mercosul) ou ABC-1234
  marca       TEXT         NOT NULL,
  modelo      TEXT         NOT NULL,
  ano         INTEGER      NOT NULL,
  cor         TEXT         NULL,
  CONSTRAINT pk_veiculo          PRIMARY KEY (id AUTOINCREMENT),
  CONSTRAINT uq_veiculo_placa    UNIQUE (placa),
  CONSTRAINT ck_veiculo_ano      CHECK (ano BETWEEN 1950 AND 2100),
  CONSTRAINT fk_veiculo_cliente  FOREIGN KEY (cliente_id) REFERENCES cliente (id)
    ON DELETE RESTRICT ON UPDATE CASCADE                  -- cliente com veículo não pode ser excluído
);

-- ---------------------------------------------------------------------
--  SERVICO: catálogo de serviços (mão de obra) e peças, com preço de tabela
--
--  preco em CENTAVOS (INTEGER). O SQLite não tem tipo decimal de verdade:
--  DECIMAL(10,2) ali vira ponto flutuante, e somar dinheiro em float
--  produz centavo errado. Guardando o inteiro, SUM() é exato.
--  A conversão para reais acontece no DAO.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS servico (
  id          INTEGER      NOT NULL,
  descricao   TEXT         NOT NULL,
  tipo        TEXT         NOT NULL DEFAULT 'MAO_DE_OBRA',
  preco_centavos INTEGER   NOT NULL,
  ativo       INTEGER      NOT NULL DEFAULT 1,            -- inativo = não pode entrar em novas OS
  CONSTRAINT pk_servico            PRIMARY KEY (id AUTOINCREMENT),
  CONSTRAINT uq_servico_descricao  UNIQUE (descricao),
  CONSTRAINT ck_servico_tipo       CHECK (tipo IN ('MAO_DE_OBRA', 'PECA')),
  CONSTRAINT ck_servico_preco      CHECK (preco_centavos >= 0),
  CONSTRAINT ck_servico_ativo      CHECK (ativo IN (0, 1))
);

-- ---------------------------------------------------------------------
--  ORDEM_SERVICO: cabeçalho da OS, sempre vinculada a um veículo
--  (e, através dele, ao cliente). O valor total NÃO é armazenado:
--  é calculado como SUM(quantidade * valor_unitario_centavos) dos itens.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ordem_servico (
  id                  INTEGER  NOT NULL,
  veiculo_id          INTEGER  NOT NULL,
  status              TEXT     NOT NULL DEFAULT 'ABERTA',
  descricao_problema  TEXT     NOT NULL,
  km_atual            INTEGER  NULL,
  data_abertura       TEXT     NOT NULL DEFAULT (datetime('now', 'localtime')),
  data_conclusao      TEXT     NULL,
  observacoes         TEXT     NULL,
  CONSTRAINT pk_ordem_servico   PRIMARY KEY (id AUTOINCREMENT),
  CONSTRAINT fk_os_veiculo      FOREIGN KEY (veiculo_id) REFERENCES veiculo (id)
    ON DELETE RESTRICT ON UPDATE CASCADE,                 -- veículo com OS não pode ser excluído
  CONSTRAINT ck_os_status       CHECK (status IN ('ABERTA', 'EM_ANDAMENTO', 'CONCLUIDA', 'CANCELADA')),
  CONSTRAINT ck_os_conclusao    CHECK (data_conclusao IS NULL OR data_conclusao >= data_abertura),
  CONSTRAINT ck_os_km           CHECK (km_atual IS NULL OR km_atual BETWEEN 0 AND 9999999)
);

CREATE INDEX IF NOT EXISTS idx_os_status        ON ordem_servico (status);
CREATE INDEX IF NOT EXISTS idx_os_data_abertura ON ordem_servico (data_abertura);

-- ---------------------------------------------------------------------
--  ITEM_OS: tabela associativa N:N entre ORDEM_SERVICO e SERVICO.
--  Guarda quantidade e o preço praticado no momento (histórico), pois
--  o preço de tabela em SERVICO pode mudar depois.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS item_os (
  id                INTEGER  NOT NULL,
  ordem_servico_id  INTEGER  NOT NULL,
  servico_id        INTEGER  NOT NULL,
  quantidade        INTEGER  NOT NULL DEFAULT 1,
  valor_unitario_centavos INTEGER NOT NULL,
  CONSTRAINT pk_item_os          PRIMARY KEY (id AUTOINCREMENT),
  CONSTRAINT uq_item_os          UNIQUE (ordem_servico_id, servico_id),   -- mesmo serviço uma vez por OS
  CONSTRAINT ck_item_os_qtd      CHECK (quantidade > 0),
  CONSTRAINT ck_item_os_valor    CHECK (valor_unitario_centavos >= 0),
  CONSTRAINT fk_item_os_os       FOREIGN KEY (ordem_servico_id) REFERENCES ordem_servico (id)
    ON DELETE CASCADE ON UPDATE CASCADE,                  -- excluir a OS remove seus itens
  CONSTRAINT fk_item_os_servico  FOREIGN KEY (servico_id) REFERENCES servico (id)
    ON DELETE RESTRICT ON UPDATE CASCADE                  -- serviço já usado em OS não pode ser excluído
);

-- ---------------------------------------------------------------------
--  USUARIO: quem pode entrar no sistema.
--
--  A senha NUNCA é gravada: a coluna guarda o resultado de um PBKDF2 com
--  sal aleatório, no formato "pbkdf2_sha256$iteracoes$sal$hash"
--  (ver security/Senhas.java). O CHECK abaixo é a última linha de defesa:
--  o próprio banco recusa qualquer INSERT com senha em texto puro.
--
--  O ESCAPE '\' é obrigatório aqui. No MySQL a barra invertida já escapava
--  o '_' sozinha; no SQLite, sem o ESCAPE, o padrão exigiria uma barra
--  invertida literal no hash e NENHUM usuário conseguiria ser criado.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS usuario (
  id                 INTEGER  NOT NULL,
  nome               TEXT     NOT NULL,
  email              TEXT     NOT NULL,                   -- login
  senha_hash         TEXT     NOT NULL,                   -- PBKDF2, nunca a senha
  perfil             TEXT     NOT NULL DEFAULT 'ATENDENTE',
  ativo              INTEGER  NOT NULL DEFAULT 1,         -- inativo não consegue entrar
  tentativas_falhas  INTEGER  NOT NULL DEFAULT 0,
  bloqueado_ate      TEXT     NULL,
  ultimo_acesso      TEXT     NULL,
  criado_em          TEXT     NOT NULL DEFAULT (datetime('now', 'localtime')),
  CONSTRAINT pk_usuario         PRIMARY KEY (id AUTOINCREMENT),
  CONSTRAINT uq_usuario_email   UNIQUE (email),
  CONSTRAINT ck_usuario_email   CHECK (email LIKE '_%@_%._%'),
  CONSTRAINT ck_usuario_senha   CHECK (senha_hash LIKE 'pbkdf2\_sha256$%' ESCAPE '\'),
  CONSTRAINT ck_usuario_nome    CHECK (LENGTH(nome) >= 2),
  CONSTRAINT ck_usuario_perfil  CHECK (perfil IN ('ADMIN', 'ATENDENTE')),
  CONSTRAINT ck_usuario_ativo   CHECK (ativo IN (0, 1))
);
