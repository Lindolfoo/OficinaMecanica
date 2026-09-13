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
UNION ALL SELECT 'item_os',       COUNT(*) FROM item_os;

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

-- =====================================================================
--  9. PROVA DE INTEGRIDADE REFERENCIAL
--
--  Rode SEPARADAMENTE — este comando FALHA de propósito, e é justamente
--  o erro que serve de evidência (a FK impede a exclusão):
--
--      DELETE FROM cliente WHERE id = 1;
--
--  Saída esperada:
--      ERROR 1451 (23000): Cannot delete or update a parent row:
--      a foreign key constraint fails (`oficina`.`veiculo`, ...)
-- =====================================================================
