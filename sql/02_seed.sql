-- =====================================================================
--  OFICINA MECÂNICA - Carga inicial (dados de exemplo)
--  SQLite 3 — execute UMA vez, depois do 01_schema.sql.
--
--  Os valores monetários estão em CENTAVOS (ver comentário em servico,
--  no 01_schema.sql): 8000 = R$ 80,00.
--
--  INSERT OR IGNORE deixa o script repetível: rodar de novo não duplica
--  nem estoura erro de chave.
-- =====================================================================

INSERT OR IGNORE INTO cliente (id, nome, cpf, telefone, email) VALUES
  (1, 'Ana Paula Souza',     '52601815906', '(15) 99876-1122', 'ana.souza@email.com'),
  (2, 'Carlos Henrique Lima','08301661305', '(15) 98811-4455', 'carlos.lima@email.com'),
  (3, 'Mariana Oliveira',    '18609139034', '(11) 97654-3210', 'mariana.oli@email.com'),
  (4, 'José Roberto Santos', '99603082430', NULL,              NULL);

INSERT OR IGNORE INTO veiculo (id, cliente_id, placa, marca, modelo, ano, cor) VALUES
  (1, 1, 'ABC1D23', 'Fiat',       'Argo',   2021, 'Prata'),
  (2, 1, 'XYZ9K88', 'Honda',      'Fit',    2015, 'Preto'),
  (3, 2, 'DEF2E45', 'Volkswagen', 'Gol',    2012, 'Branco'),
  (4, 3, 'GHI3F67', 'Chevrolet',  'Onix',   2023, 'Vermelho');

INSERT OR IGNORE INTO servico (id, descricao, tipo, preco_centavos, ativo) VALUES
  (1, 'Troca de óleo (mão de obra)',      'MAO_DE_OBRA',  8000, 1),
  (2, 'Óleo 5W30 sintético - 1 litro',    'PECA',         4590, 1),
  (3, 'Alinhamento e balanceamento',      'MAO_DE_OBRA', 15000, 1),
  (4, 'Pastilha de freio dianteira (par)','PECA',        18990, 1),
  (5, 'Troca de pastilhas (mão de obra)', 'MAO_DE_OBRA', 12000, 1),
  (6, 'Revisão geral 10 mil km',          'MAO_DE_OBRA', 25000, 1),
  (7, 'Filtro de óleo',                   'PECA',         3500, 0);   -- exemplo de item inativo

INSERT OR IGNORE INTO ordem_servico (id, veiculo_id, status, descricao_problema, km_atual, data_abertura, data_conclusao, observacoes) VALUES
  (1, 1, 'ABERTA',       'Barulho ao frear em baixa velocidade',  45210, '2026-08-20 09:15:00', NULL,                  NULL),
  (2, 3, 'EM_ANDAMENTO', 'Revisão periódica e troca de óleo',      98750, '2026-08-22 14:00:00', NULL,                  'Cliente pediu para verificar palhetas'),
  (3, 4, 'CONCLUIDA',    'Volante puxando para a direita',          8120, '2026-08-10 08:30:00', '2026-08-10 11:45:00', NULL),
  (4, 2, 'CANCELADA',    'Orçamento para troca de embreagem',     132000, '2026-08-05 10:00:00', NULL,                  'Cliente desistiu do serviço');

INSERT OR IGNORE INTO item_os (ordem_servico_id, servico_id, quantidade, valor_unitario_centavos) VALUES
  (1, 4, 1, 18990),   -- pastilha
  (1, 5, 1, 12000),   -- mão de obra da troca
  (2, 1, 1,  8000),
  (2, 2, 4,  4590),   -- 4 litros de óleo
  (2, 6, 1, 25000),
  (3, 3, 1, 15000);
