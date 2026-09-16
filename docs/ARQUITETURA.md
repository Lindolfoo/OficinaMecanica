# Mapa do código — por onde começar a leitura

Ordem sugerida para entender o projeto (e para dividir entre o grupo).

## 1. O banco vem primeiro
| Arquivo | O que ver |
|---|---|
| `sql/01_schema.sql` | As 6 tabelas e as 24 restrições (PK/FK/UNIQUE/CHECK), com as políticas `RESTRICT`/`CASCADE`. **Leia este antes de qualquer Java.** |
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
| `App.java` | Sobe o servidor, registra as rotas e pendura os filtros. |

## 2b. Controle de acesso (pacote `security/`)
| Arquivo | Responsabilidade |
|---|---|
| `security/Senhas.java` | Hash PBKDF2 com sal. A senha nunca é gravada nem comparada com `equals`. |
| `security/Sessoes.java` | Token aleatório em cookie HttpOnly; o servidor guarda de quem é cada token. |
| `security/FiltroSeguranca.java` | Cabeçalhos que fecham portas no navegador (CSP, nosniff, anti-iframe). |
| `security/FiltroAutenticacao.java` | O porteiro: exige sessão e confere o token anti-CSRF. |
| `api/AuthHandler.java` | Entrar, sair e trocar a própria senha. |
| `api/UsuarioHandler.java` | CRUD de contas — só perfil ADMIN. |

As quatro defesas, em ordem de quem tenta o quê:

    senha adivinhada     -> PBKDF2 caro + bloqueio da conta após 5 erros
    cookie forjado       -> token aleatório de 256 bits, o cookie não carrega dados
    POST de outro site   -> token anti-CSRF em cabeçalho (SameSite=Strict reforça)
    script injetado      -> CSP fecha script-src em 'self' + cookie HttpOnly

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
| `static/login.html` + `js/login.js` | Tela de entrada. Só manda e-mail e senha; o token da sessão volta no cookie e o JavaScript nem o enxerga. |
| `static/index.html` | Menu lateral + 6 páginas (Dashboard, Clientes, Veículos, Serviços, OS, Usuários) e os modais. |
| `static/js/app.js` | Um bloco por módulo, sempre: `carregar` → `abrirModal` → `submit` → `excluir`. No topo ficam a sessão, o token CSRF e a navegação. |
| `static/css/style.css` | Identidade visual, menu lateral, dashboard e a via impressa; o resto é Bootstrap. |
| `static/img/beagle.png` | Mascote da tela de login. Fica no classpath como os demais estáticos. |
| `static/vendor/` | jQuery e Bootstrap locais — o projeto roda sem internet. |

Os gráficos do dashboard (rosca e barras) são **SVG montado à mão** em
`app.js`: sem biblioteca de gráficos, o projeto continua rodando offline e com
uma dependência só no `pom.xml`.

## Fluxo de uma requisição

    navegador (jQuery $.ajax, com o cabeçalho X-CSRF-Token)
        -> FiltroSeguranca      (põe CSP e os demais cabeçalhos)
        -> FiltroAutenticacao   (exige a sessão; confere o CSRF se altera dados)
        -> App.java (rota)
        -> XHandler (valida a entrada)
        -> XDao (PreparedStatement)
        -> MySQL
        -> volta como JSON

Sem sessão, o pedido morre no filtro: a API responde `401` e as páginas
redirecionam para `/login.html`. Nenhum handler precisa se preocupar com isso.

## Onde estão as validações
São **três camadas**, de propósito:

1. **HTML** (`required`, `pattern`) — conveniência, o usuário vê na hora.
2. **Handler** (Java) — a regra real; o HTML pode ser burlado pelo DevTools.
3. **Banco** (CHECK/UNIQUE/FK) — a última linha de defesa; vale mesmo para quem
   acessar o banco por fora da aplicação.
