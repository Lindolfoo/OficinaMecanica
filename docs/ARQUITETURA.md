# Mapa do código — por onde começar a leitura

Ordem sugerida para entender o projeto (e para dividir entre o grupo).

## 1. O banco vem primeiro
| Arquivo | O que ver |
|---|---|
| `sql/01_schema.sql` | As 5 tabelas, PK/FK/UNIQUE/CHECK e as políticas `RESTRICT`/`CASCADE`. **Leia este antes de qualquer Java.** |
| `sql/02_seed.sql` | Dados de exemplo para a tela não nascer vazia. |

## 2. Infraestrutura (escreve-se uma vez, não se mexe mais)
| Arquivo | Responsabilidade |
|---|---|
| `config/Config.java` | Lê `config.properties`; variáveis de ambiente têm prioridade. |
| `db/Database.java` | Abre conexões JDBC e executa o schema na subida. |
| `http/Json.java` | Serializador JSON escrito à mão (zero bibliotecas). |
| `http/HttpUtil.java` | Lê formulário/query string, escreve resposta JSON. |
| `http/BaseHandler.java` | Roteia por método HTTP e traduz exceção SQL em status HTTP. |
| `http/StaticHandler.java` | Entrega o front-end a partir do classpath. |
| `App.java` | Sobe o servidor e registra as rotas. |

## 3. Os quatro módulos (o trabalho de verdade)
Cada módulo tem sempre as mesmas três camadas:

    model/X.java     -> espelho da tabela (record)
    dao/XDao.java    -> o SQL: listar, buscarPorId, inserir, atualizar, excluir
    api/XHandler.java-> validação de entrada + tradução HTTP

| Módulo | Dificuldade | Sugestão |
|---|---|---|
| `Cliente` | Simples | CRUD mais direto — comece por aqui. |
| `Servico` | Simples | Igual ao Cliente, com `DECIMAL` e `ENUM`. |
| `Veiculo` | Média | Primeiro `JOIN` (traz o nome do dono). |
| `OrdemServico` | **Alta** | `JOIN` duplo, subconsulta `SUM`, tabela associativa e **transação**. |

## 4. O trecho mais importante do trabalho
`dao/OrdemServicoDao.java`, método **`inserirComItens`**.

É o único ponto com transação explícita:

    c.setAutoCommit(false);   // abre
    ... grava o cabeçalho e os itens ...
    c.commit();               // confirma tudo
    // no catch: c.rollback() -> desfaz tudo

Sem isso, uma falha no terceiro item deixaria no banco uma OS pela metade.
Se o professor perguntar "onde está o controle de transação?", é aqui.

## 5. Front-end
| Arquivo | Conteúdo |
|---|---|
| `static/index.html` | 4 abas + 4 modais (Bootstrap). |
| `static/js/app.js` | Um bloco por módulo, sempre: `carregar` → `abrirModal` → `submit` → `excluir`. |
| `static/css/style.css` | Só a identidade visual; o resto é Bootstrap. |
| `static/vendor/` | jQuery e Bootstrap locais — o projeto roda sem internet. |

## Fluxo de uma requisição

    navegador (jQuery $.ajax)
        -> App.java (rota)
        -> XHandler (valida a entrada)
        -> XDao (PreparedStatement)
        -> MySQL
        -> volta como JSON

## Onde estão as validações
São **três camadas**, de propósito:

1. **HTML** (`required`, `pattern`) — conveniência, o usuário vê na hora.
2. **Handler** (Java) — a regra real; o HTML pode ser burlado pelo DevTools.
3. **Banco** (CHECK/UNIQUE/FK) — a última linha de defesa; vale mesmo para quem
   acessar o banco por fora da aplicação.
