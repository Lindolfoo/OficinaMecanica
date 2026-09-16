-- =====================================================================
--  EVIDÊNCIAS DE PERSISTÊNCIA NO BANCO
--  Consultas para comprovar que os dados criados pela interface estão
--  realmente gravados no MySQL (exigência de "Evidências Visuais").
--
--  Como rodar (e tirar o print da saída):
--
--      docker exec -i oficina-mysql mysql -uroot -proot \
--        --table --default-character-set=utf8mb4 < docs/evidencias.sql
--
--  A flag --default-character-set=utf8mb4 é obrigatória: sem ela o cliente
--  mysql imprime os acentos como "?" e o print sai com o texto corrompido
--  (o dado no banco está correto; o problema é só a saída do terminal).
--
--  Ou cole as consultas no MySQL Workbench e tire o print de cada grade.
-- =====================================================================
USE oficina;

-- ---------------------------------------------------------------------
--  1. Quantas linhas existem em cada tabela
-- ---------------------------------------------------------------------
SELECT 'cliente' AS tabela, COUNT(*) AS linhas FROM cliente
UNION ALL SELECT 'veiculo',       COUNT(*) FROM veiculo
UNION ALL SELECT 'servico',       COUNT(*) FROM servico
UNION ALL SELECT 'ordem_servico', COUNT(*) FROM ordem_servico
UNION ALL SELECT 'item_os',       COUNT(*) FROM item_os
UNION ALL SELECT 'usuario',       COUNT(*) FROM usuario;

-- ---------------------------------------------------------------------
--  2. Clientes e seus veículos (relacionamento 1:N)
-- ---------------------------------------------------------------------
SELECT c.id, c.nome, c.cpf, v.placa, CONCAT(v.marca, ' ', v.modelo) AS veiculo, v.ano
  FROM cliente c
  LEFT JOIN veiculo v ON v.cliente_id = c.id
 ORDER BY c.id, v.placa;

-- ---------------------------------------------------------------------
--  3. Ordens de serviço com cliente, veículo e total calculado
--     (JOIN duplo + subconsulta com SUM — o total NÃO é coluna)
-- ---------------------------------------------------------------------
SELECT os.id AS os, v.placa, c.nome AS cliente, os.status,
       os.data_abertura,
       COALESCE((SELECT SUM(i.quantidade * i.valor_unitario)
                   FROM item_os i WHERE i.ordem_servico_id = os.id), 0) AS total
  FROM ordem_servico os
  JOIN veiculo v ON v.id = os.veiculo_id
  JOIN cliente c ON c.id = v.cliente_id
 ORDER BY os.data_abertura DESC;

-- ---------------------------------------------------------------------
--  4. Itens de uma OS — prova de que a transação gravou cabeçalho e itens
-- ---------------------------------------------------------------------
SELECT i.ordem_servico_id AS os, s.descricao AS item, s.tipo,
       i.quantidade AS qtd, i.valor_unitario AS unitario,
       (i.quantidade * i.valor_unitario) AS subtotal
  FROM item_os i
  JOIN servico s ON s.id = i.servico_id
 ORDER BY i.ordem_servico_id, i.id;

-- ---------------------------------------------------------------------
--  5. Faturamento por status (GROUP BY)
-- ---------------------------------------------------------------------
SELECT os.status, COUNT(DISTINCT os.id) AS quantidade,
       COALESCE(SUM(i.quantidade * i.valor_unitario), 0) AS total
  FROM ordem_servico os
  LEFT JOIN item_os i ON i.ordem_servico_id = os.id
 GROUP BY os.status
 ORDER BY total DESC;

-- ---------------------------------------------------------------------
--  6. Histórico de um veículo (a consulta por trás da tela de histórico)
-- ---------------------------------------------------------------------
SELECT os.id AS os, os.descricao_problema, os.status,
       os.data_abertura, os.data_conclusao,
       COALESCE((SELECT SUM(i.quantidade * i.valor_unitario)
                   FROM item_os i WHERE i.ordem_servico_id = os.id), 0) AS total
  FROM ordem_servico os
  JOIN veiculo v ON v.id = os.veiculo_id
 WHERE v.placa = 'ABC1D23'
 ORDER BY os.data_abertura DESC;

-- ---------------------------------------------------------------------
--  7. As restrições que o banco realmente tem (PK, FK, UNIQUE, CHECK)
-- ---------------------------------------------------------------------
SELECT TABLE_NAME AS tabela, CONSTRAINT_NAME AS restricao, CONSTRAINT_TYPE AS tipo
  FROM information_schema.TABLE_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = 'oficina'
 ORDER BY TABLE_NAME, CONSTRAINT_TYPE, CONSTRAINT_NAME;

-- ---------------------------------------------------------------------
--  7b. Total de restrições por tipo (o número que o README cita)
-- ---------------------------------------------------------------------
SELECT CONSTRAINT_TYPE AS tipo, COUNT(*) AS quantidade
  FROM information_schema.TABLE_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = 'oficina'
 GROUP BY CONSTRAINT_TYPE
 ORDER BY quantidade DESC;

-- ---------------------------------------------------------------------
--  8. As chaves estrangeiras e suas políticas de integridade
--     (é o que comprova o RESTRICT/CASCADE descrito no DER)
-- ---------------------------------------------------------------------
SELECT rc.TABLE_NAME AS tabela, rc.CONSTRAINT_NAME AS fk, k.COLUMN_NAME AS coluna,
       rc.REFERENCED_TABLE_NAME AS referencia,
       rc.DELETE_RULE AS ao_excluir, rc.UPDATE_RULE AS ao_atualizar
  FROM information_schema.REFERENTIAL_CONSTRAINTS rc
  JOIN information_schema.KEY_COLUMN_USAGE k
    ON k.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
   AND k.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
 WHERE rc.CONSTRAINT_SCHEMA = 'oficina'
 ORDER BY rc.TABLE_NAME;

-- ---------------------------------------------------------------------
--  9. Nenhuma senha em texto puro: a coluna guarda só o hash PBKDF2
--     (o prefixo "pbkdf2_sha256$" é exigido por um CHECK da tabela)
-- ---------------------------------------------------------------------
SELECT id, nome, email, perfil, ativo,
       LEFT(senha_hash, 30) AS inicio_do_hash,
       CHAR_LENGTH(senha_hash) AS tamanho
  FROM usuario
 ORDER BY id;

-- =====================================================================
--  10. PROVAS DE INTEGRIDADE — comandos que FALHAM de propósito
--
--  Rode SEPARADAMENTE: o erro é justamente a evidência. Tire o print
--  da mensagem de erro.
--
--  a) A FK impede excluir um cliente que ainda tem veículo:
--
--         DELETE FROM cliente WHERE id = 1;
--
--     Saída esperada:
--         ERROR 1451 (23000): Cannot delete or update a parent row:
--         a foreign key constraint fails (`oficina`.`veiculo`, ...)
--
--  b) O CHECK impede gravar senha em texto puro, mesmo por fora da aplicação:
--
--         INSERT INTO usuario (nome, email, senha_hash, perfil)
--         VALUES ('Invasor', 'x@y.com', '123456', 'ADMIN');
--
--     Saída esperada:
--         ERROR 3819 (HY000): Check constraint 'ck_usuario_senha' is violated.
--
--  c) O CHECK do CPF recusa um valor fora do formato:
--
--         INSERT INTO cliente (nome, cpf) VALUES ('Teste', 'abc');
--
--     Saída esperada:
--         ERROR 3819 (HY000): Check constraint 'ck_cliente_cpf' is violated.
-- =====================================================================
