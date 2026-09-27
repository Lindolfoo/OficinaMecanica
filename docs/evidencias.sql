-- =====================================================================
--  EVIDÊNCIAS DE PERSISTÊNCIA NO BANCO  —  SQLite
--
--  Consultas para comprovar que os dados criados pela interface estão
--  realmente gravados, e que as regras de integridade estão ativas.
--
--  Como rodar:
--
--      sqlite3 -header -column "CAMINHO/oficina.db" < docs/evidencias.sql
--
--  O caminho do banco aparece no console quando a aplicação sobe
--  ("Arquivo: ..."). Por padrão:
--      Windows  %LOCALAPPDATA%\OficinaMecanica\oficina.db
--      Linux    ~/.local/share/OficinaMecanica/oficina.db
--      macOS    ~/Library/Application Support/OficinaMecanica/oficina.db
--
--  Sem o sqlite3 instalado (ele não vem no Windows), baixe em
--  https://sqlite.org/download.html — "sqlite-tools" — ou use o
--  DB Browser for SQLite e cole as consultas.
--
--  IMPORTANTE: a primeira linha não é enfeite. No SQLite as chaves
--  estrangeiras vêm DESLIGADAS em cada conexão, e sem ligá-las a prova
--  de integridade do item 9 passaria sem erro, dando a impressão falsa
--  de que não há integridade referencial.
-- =====================================================================
PRAGMA foreign_keys = ON;

-- ---------------------------------------------------------------------
--  1. As tabelas do banco
-- ---------------------------------------------------------------------
SELECT name AS tabela
  FROM sqlite_master
 WHERE type = 'table' AND name NOT LIKE 'sqlite_%'
 ORDER BY name;

-- ---------------------------------------------------------------------
--  2. Quantas linhas existem em cada tabela
-- ---------------------------------------------------------------------
SELECT 'cliente' AS tabela, COUNT(*) AS linhas FROM cliente
UNION ALL SELECT 'veiculo',       COUNT(*) FROM veiculo
UNION ALL SELECT 'servico',       COUNT(*) FROM servico
UNION ALL SELECT 'ordem_servico', COUNT(*) FROM ordem_servico
UNION ALL SELECT 'item_os',       COUNT(*) FROM item_os
UNION ALL SELECT 'usuario',       COUNT(*) FROM usuario;

-- ---------------------------------------------------------------------
--  3. Clientes e seus veículos (relacionamento 1:N)
-- ---------------------------------------------------------------------
SELECT c.id, c.nome, c.cpf, v.placa,
       v.marca || ' ' || v.modelo AS veiculo, v.ano
  FROM cliente c
  LEFT JOIN veiculo v ON v.cliente_id = c.id
 ORDER BY c.id, v.placa;

-- ---------------------------------------------------------------------
--  4. Ordens de serviço com cliente, veículo e total calculado
--     (JOIN duplo + subconsulta com SUM — o total NÃO é coluna)
--
--     Os valores são guardados em CENTAVOS (INTEGER), porque o SQLite não
--     tem tipo decimal de verdade e somar dinheiro em ponto flutuante
--     pode render centavo errado. A divisão por 100 é só para exibir.
-- ---------------------------------------------------------------------
SELECT os.id AS os, v.placa, c.nome AS cliente, os.status,
       os.data_abertura,
       printf('%.2f', COALESCE((SELECT SUM(i.quantidade * i.valor_unitario_centavos)
                                  FROM item_os i
                                 WHERE i.ordem_servico_id = os.id), 0) / 100.0) AS total_reais
  FROM ordem_servico os
  JOIN veiculo v ON v.id = os.veiculo_id
  JOIN cliente c ON c.id = v.cliente_id
 ORDER BY os.data_abertura DESC;

-- ---------------------------------------------------------------------
--  5. Itens de uma OS — prova de que a transação gravou cabeçalho e itens
-- ---------------------------------------------------------------------
SELECT i.ordem_servico_id AS os, s.descricao AS item, s.tipo,
       i.quantidade AS qtd,
       printf('%.2f', i.valor_unitario_centavos / 100.0) AS unitario,
       printf('%.2f', i.quantidade * i.valor_unitario_centavos / 100.0) AS subtotal
  FROM item_os i
  JOIN servico s ON s.id = i.servico_id
 ORDER BY i.ordem_servico_id, i.id;

-- ---------------------------------------------------------------------
--  6. Faturamento por status (GROUP BY)
-- ---------------------------------------------------------------------
SELECT os.status, COUNT(DISTINCT os.id) AS quantidade,
       printf('%.2f', COALESCE(SUM(i.quantidade * i.valor_unitario_centavos), 0) / 100.0) AS total_reais
  FROM ordem_servico os
  LEFT JOIN item_os i ON i.ordem_servico_id = os.id
 GROUP BY os.status
 ORDER BY total_reais DESC;

-- ---------------------------------------------------------------------
--  7. Histórico de um veículo (a consulta por trás da tela de histórico)
-- ---------------------------------------------------------------------
SELECT os.id AS os, os.descricao_problema, os.status,
       os.data_abertura, os.data_conclusao,
       printf('%.2f', COALESCE((SELECT SUM(i.quantidade * i.valor_unitario_centavos)
                                  FROM item_os i
                                 WHERE i.ordem_servico_id = os.id), 0) / 100.0) AS total_reais
  FROM ordem_servico os
  JOIN veiculo v ON v.id = os.veiculo_id
 WHERE v.placa = 'ABC1D23'
 ORDER BY os.data_abertura DESC;

-- ---------------------------------------------------------------------
--  8. As restrições que o banco realmente tem
--
--     O SQLite não tem information_schema: quem guarda as restrições é o
--     próprio texto do CREATE TABLE, em sqlite_master. Este SELECT mostra
--     o DDL de verdade que está dentro do arquivo, com PK, UNIQUE, CHECK
--     e FOREIGN KEY de cada tabela.
-- ---------------------------------------------------------------------
SELECT sql AS ddl_gravado_no_banco
  FROM sqlite_master
 WHERE type = 'table' AND name NOT LIKE 'sqlite_%'
 ORDER BY name;

-- ---------------------------------------------------------------------
--  9. As chaves estrangeiras e suas políticas
--     (comprova o RESTRICT/CASCADE descrito no DER)
-- ---------------------------------------------------------------------
SELECT 'veiculo'       AS tabela, "table" AS referencia, "from" AS coluna,
       on_delete AS ao_excluir, on_update AS ao_atualizar FROM pragma_foreign_key_list('veiculo')
UNION ALL
SELECT 'ordem_servico', "table", "from", on_delete, on_update FROM pragma_foreign_key_list('ordem_servico')
UNION ALL
SELECT 'item_os',       "table", "from", on_delete, on_update FROM pragma_foreign_key_list('item_os');

-- ---------------------------------------------------------------------
--  10. Nenhuma senha em texto puro: a coluna guarda só o hash PBKDF2
-- ---------------------------------------------------------------------
SELECT id, nome, email, perfil, ativo,
       substr(senha_hash, 1, 30) AS inicio_do_hash,
       length(senha_hash) AS tamanho
  FROM usuario
 ORDER BY id;

-- ---------------------------------------------------------------------
--  11. O banco está íntegro?
-- ---------------------------------------------------------------------
PRAGMA integrity_check;

-- =====================================================================
--  12. PROVAS DE INTEGRIDADE — comandos que FALHAM de propósito
--
--  Rode SEPARADAMENTE: o erro É a evidência. Lembre do
--  PRAGMA foreign_keys = ON antes, senão o primeiro passa e não prova nada.
--
--  a) A FK impede excluir um cliente que ainda tem veículo:
--
--         DELETE FROM cliente WHERE id = 1;
--     -> Runtime error: FOREIGN KEY constraint failed
--
--  b) O CHECK impede gravar senha em texto puro, mesmo por fora da aplicação:
--
--         INSERT INTO usuario (nome, email, senha_hash, perfil)
--         VALUES ('Invasor', 'x@y.com', '123456', 'ADMIN');
--     -> Runtime error: CHECK constraint failed: ck_usuario_senha
--
--  c) O CHECK do CPF recusa valor fora do formato de 11 dígitos:
--
--         INSERT INTO cliente (nome, cpf) VALUES ('Teste', 'abc');
--     -> Runtime error: CHECK constraint failed: ck_cliente_cpf
--
--  d) O CHECK do status recusa valor fora da lista:
--
--         INSERT INTO ordem_servico (veiculo_id, status, descricao_problema)
--         VALUES (1, 'INVENTADO', 'teste');
--     -> Runtime error: CHECK constraint failed: ck_os_status
-- =====================================================================
