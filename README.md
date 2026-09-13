# Oficina Mecânica — Sistema de Ordens de Serviço

**Universidade Paulista (UNIP)** — Trabalho NP1 da disciplina de **Banco de Dados**

| | |
|---|---|
| **Curso** | Análise e Desenvolvimento de Sistemas |
| **Turma** | DS4P-17 |

### Integrantes

| Nome completo | RA |
|---|---|
| Eduardo Matheus do Amaral Alves | H753BJ9 |
| Henrique Rodrigues Lindolfo | H6250F3 |
| Matheus Fernandes Pereira | H75IHD9 |
| Matheus Sousa Ribeiro | R850862 |
| Nicollas Abreu Svidevska de Camargo | R200965 |
| Vitor Roma Cunha Santos | R8504C4 |

---

Sistema web de gestão para uma oficina mecânica: cadastro de clientes e veículos,
catálogo de serviços, e abertura de ordens de serviço com itens e total calculado.

![Lista de ordens de serviço](docs/prints/07-ordens-lista.png)

## Por que sem framework

O objetivo da disciplina é **modelagem relacional e SQL**, não produtividade.
Não há Spring, nem Hibernate, nem JPA: todo o SQL é escrito à mão em
`PreparedStatement` e o controle de transação é explícito.

| Camada | Como foi feito |
|---|---|
| Banco | MySQL 8.4 — PK, FK, `UNIQUE`, `CHECK` e políticas `RESTRICT`/`CASCADE` |
| Acesso a dados | JDBC puro (`java.sql`), um DAO por tabela |
| HTTP | `com.sun.net.httpserver` do JDK, uma thread virtual por requisição |
| JSON | Serializador escrito à mão (`http/Json.java`) |
| Front-end | jQuery + Bootstrap servidos do classpath — roda sem internet |

Única dependência do `pom.xml`: o driver JDBC do MySQL (Connector/J).

## Como executar

Pré-requisitos: **JDK 25** e **Docker** (ou um MySQL 8 já instalado).

    # 1. sobe o MySQL e cria schema + dados de exemplo
    docker compose up -d

    # 2. gera o jar executável e roda
    mvn package
    java -jar target/oficina.jar

Abra <http://localhost:8080>.

O schema é aplicado automaticamente na subida (`db.autoSchema=true`), então a
aplicação também roda contra um MySQL já existente — basta apontar as credenciais:

    DB_URL="jdbc:mysql://localhost:3306/oficina" DB_USER=root DB_PASSWORD=senha \
      java -jar target/oficina.jar

Padrões em `src/main/resources/config.properties`; qualquer chave pode ser
sobrescrita por variável de ambiente (`PORT`, `DB_URL`, `DB_USER`, `DB_PASSWORD`,
`DB_AUTO_SCHEMA`).

## Modelo de dados

    cliente 1 ──< veiculo 1 ──< ordem_servico >── item_os ──< servico

| Tabela | Papel | Regra de integridade |
|---|---|---|
| `cliente` | Proprietário dos veículos | CPF `UNIQUE`, validado por dígito verificador |
| `veiculo` | Um cliente tem N veículos | Placa `UNIQUE`; `ON DELETE RESTRICT` no cliente |
| `servico` | Catálogo de mão de obra e peças | `CHECK (preco >= 0)` |
| `ordem_servico` | Cabeçalho da OS | Total **não** é armazenado: é `SUM` dos itens |
| `item_os` | Associativa N:N entre OS e serviço | `CASCADE` na OS, `RESTRICT` no serviço |

O DDL comentado está em [`sql/01_schema.sql`](sql/01_schema.sql).

### O trecho que mais importa

`dao/OrdemServicoDao.java`, método `inserirComItens` — o único ponto com
transação explícita:

```java
c.setAutoCommit(false);   // abre
// ... grava o cabeçalho e depois cada item ...
c.commit();               // confirma tudo
// no catch: c.rollback() -> desfaz tudo
```

Sem isso, uma falha ao gravar o terceiro item deixaria no banco uma OS pela metade.

## API

Respostas em JSON; entrada como formulário (`application/x-www-form-urlencoded`).

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/clientes` | lista — `?busca=texto` |
| `POST` | `/api/clientes` | cria — `nome, cpf, telefone, email` |
| `PUT` | `/api/clientes/{id}` | altera |
| `DELETE` | `/api/clientes/{id}` | exclui |
| `GET` | `/api/veiculos` | lista — `?busca=`, `?clienteId=` |
| `GET` | `/api/servicos` | lista — `?busca=`, `?ativos=true` |
| `GET` | `/api/ordens` | lista — `?status=ABERTA`, `?busca=` |
| `GET` | `/api/ordens/resumo` | quantidade e total por status (`GROUP BY`) |
| `GET` | `/api/ordens/{id}` | uma OS com seus itens |
| `POST` | `/api/ordens` | cria a OS e os itens **em uma transação** |
| `POST` | `/api/ordens/{id}/itens` | adiciona um item |
| `DELETE` | `/api/ordens/{id}/itens/{itemId}` | remove um item |

Veículos e serviços seguem o mesmo padrão de CRUD dos clientes.

### Erros

| Status | Quando |
|---|---|
| `400` | Entrada inválida (CPF, placa, campo obrigatório) ou `CHECK` do banco |
| `404` | Registro não encontrado |
| `409` | `UNIQUE` ou `FOREIGN KEY` — CPF repetido, ou exclusão com dependentes |

## Onde estão as validações

São três camadas, de propósito:

1. **HTML** (`required`, `pattern`) — conveniência; o usuário vê na hora.
2. **Handler** (Java) — a regra real; o HTML pode ser burlado pelo DevTools.
3. **Banco** (`CHECK`/`UNIQUE`/`FK`) — a última linha de defesa, vale até para
   quem acessar o banco por fora da aplicação.

## Estrutura

    sql/                      DDL e carga inicial
    src/main/java/br/com/oficina/
      App.java                sobe o servidor e registra as rotas
      config/                 leitura de config.properties e variáveis de ambiente
      db/Database.java        conexões JDBC e aplicação do schema
      http/                   Json, HttpUtil, BaseHandler, StaticHandler
      model/                  records espelhando as tabelas
      dao/                    o SQL de cada tabela
      api/                    validação de entrada e tradução HTTP
      util/Validators.java    CPF (módulo 11) e e-mail
    src/main/resources/static/  front-end (index.html, app.js, style.css, vendor)
    docs/ARQUITETURA.md       mapa do código, por onde começar a ler

[`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) explica a ordem de leitura do código.

## Telas

| | |
|---|---|
| ![Clientes](docs/prints/01-clientes-lista.png) | ![Cadastro](docs/prints/02-cliente-cadastro.png) |
| Lista de clientes com busca | Cadastro com validação |
| ![Validação de CPF](docs/prints/04-validacao-cpf.png) | ![OS com itens](docs/prints/08-os-com-itens.png) |
| CPF rejeitado pelo dígito verificador | OS com itens e total calculado |
| ![Filtro por status](docs/prints/10-filtro-status.png) | ![Integridade](docs/prints/11-integridade-fk.png) |
| Filtro por status | FK impedindo exclusão indevida |

Mais prints em [`docs/prints/`](docs/prints/).

## Licença

MIT — veja [LICENSE](LICENSE).
