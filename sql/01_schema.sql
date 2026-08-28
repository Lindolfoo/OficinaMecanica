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
