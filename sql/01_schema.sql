-- =====================================================================
--  OFICINA MECÂNICA - Sistema de Ordens de Serviço
--  Disciplina: Banco de Dados (NP1) - UNIP
--  Script DDL - MySQL 8.x (InnoDB, utf8mb4)
--
--  Idempotente: pode ser executado várias vezes sem erro (IF NOT EXISTS).
--  Executado automaticamente pela aplicação na subida (db.autoSchema=true)
--  e também pelo container MySQL do docker-compose na primeira inicialização.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS oficina
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE oficina;

-- ---------------------------------------------------------------------
--  CLIENTE: proprietário dos veículos atendidos pela oficina
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cliente (
  id          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
  nome        VARCHAR(100)    NOT NULL,
  cpf         CHAR(11)        NOT NULL,                      -- somente dígitos
  telefone    VARCHAR(20)     NULL,
  email       VARCHAR(120)    NULL,
  criado_em   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_cliente        PRIMARY KEY (id),
  CONSTRAINT uq_cliente_cpf    UNIQUE (cpf),
  CONSTRAINT ck_cliente_cpf    CHECK (cpf REGEXP '^[0-9]{11}$'),
  INDEX idx_cliente_nome (nome)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  VEICULO: um cliente possui N veículos (1:N)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS veiculo (
  id          INT UNSIGNED        NOT NULL AUTO_INCREMENT,
  cliente_id  INT UNSIGNED        NOT NULL,
  placa       VARCHAR(8)          NOT NULL,                  -- ABC1D23 (Mercosul) ou ABC-1234
  marca       VARCHAR(50)         NOT NULL,
  modelo      VARCHAR(60)         NOT NULL,
  ano         SMALLINT UNSIGNED   NOT NULL,
  cor         VARCHAR(30)         NULL,
  CONSTRAINT pk_veiculo          PRIMARY KEY (id),
  CONSTRAINT uq_veiculo_placa    UNIQUE (placa),
  CONSTRAINT ck_veiculo_ano      CHECK (ano BETWEEN 1950 AND 2100),
  CONSTRAINT fk_veiculo_cliente  FOREIGN KEY (cliente_id) REFERENCES cliente (id)
    ON DELETE RESTRICT ON UPDATE CASCADE                     -- cliente com veículo não pode ser excluído
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  SERVICO: catálogo de serviços (mão de obra) e peças, com preço de tabela
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS servico (
  id          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
  descricao   VARCHAR(120)    NOT NULL,
  tipo        ENUM('MAO_DE_OBRA', 'PECA') NOT NULL DEFAULT 'MAO_DE_OBRA',
  preco       DECIMAL(10,2)   NOT NULL,
  ativo       TINYINT(1)      NOT NULL DEFAULT 1,            -- inativo = não pode entrar em novas OS
  CONSTRAINT pk_servico            PRIMARY KEY (id),
  CONSTRAINT uq_servico_descricao  UNIQUE (descricao),
  CONSTRAINT ck_servico_preco      CHECK (preco >= 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  ORDEM_SERVICO: cabeçalho da OS, sempre vinculada a um veículo
--  (e, através dele, ao cliente). O valor total NÃO é armazenado:
--  é calculado como SUM(quantidade * valor_unitario) dos itens.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ordem_servico (
  id                  INT UNSIGNED    NOT NULL AUTO_INCREMENT,
  veiculo_id          INT UNSIGNED    NOT NULL,
  status              ENUM('ABERTA', 'EM_ANDAMENTO', 'CONCLUIDA', 'CANCELADA') NOT NULL DEFAULT 'ABERTA',
  descricao_problema  VARCHAR(500)    NOT NULL,
  km_atual            INT UNSIGNED    NULL,
  data_abertura       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  data_conclusao      DATETIME        NULL,
  observacoes         VARCHAR(500)    NULL,
  CONSTRAINT pk_ordem_servico   PRIMARY KEY (id),
  CONSTRAINT fk_os_veiculo      FOREIGN KEY (veiculo_id) REFERENCES veiculo (id)
    ON DELETE RESTRICT ON UPDATE CASCADE,                    -- veículo com OS não pode ser excluído
  CONSTRAINT ck_os_conclusao    CHECK (data_conclusao IS NULL OR data_conclusao >= data_abertura),
  INDEX idx_os_status (status),
  INDEX idx_os_data_abertura (data_abertura)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  ITEM_OS: tabela associativa N:N entre ORDEM_SERVICO e SERVICO.
--  Guarda quantidade e o preço praticado no momento (histórico), pois
--  o preço de tabela em SERVICO pode mudar depois.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS item_os (
  id                INT UNSIGNED    NOT NULL AUTO_INCREMENT,
  ordem_servico_id  INT UNSIGNED    NOT NULL,
  servico_id        INT UNSIGNED    NOT NULL,
  quantidade        INT UNSIGNED    NOT NULL DEFAULT 1,
  valor_unitario    DECIMAL(10,2)   NOT NULL,
  CONSTRAINT pk_item_os          PRIMARY KEY (id),
  CONSTRAINT uq_item_os          UNIQUE (ordem_servico_id, servico_id),   -- mesmo serviço uma vez por OS
  CONSTRAINT ck_item_os_qtd      CHECK (quantidade > 0),
  CONSTRAINT ck_item_os_valor    CHECK (valor_unitario >= 0),
  CONSTRAINT fk_item_os_os       FOREIGN KEY (ordem_servico_id) REFERENCES ordem_servico (id)
    ON DELETE CASCADE ON UPDATE CASCADE,                     -- excluir a OS remove seus itens
  CONSTRAINT fk_item_os_servico  FOREIGN KEY (servico_id) REFERENCES servico (id)
    ON DELETE RESTRICT ON UPDATE CASCADE                     -- serviço já usado em OS não pode ser excluído
) ENGINE=InnoDB;
