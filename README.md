# Oficina Mecânica — Sistema de Ordens de Serviço

Sistema de gestão para uma oficina mecânica: clientes, veículos, catálogo de
serviços e ordens de serviço. Trabalho da disciplina de Banco de Dados (NP1).

O objetivo da disciplina é **SQL e modelagem relacional**, não produtividade —
por isso o projeto não usa framework web nem ORM:

- **Java 25**, com o servidor HTTP do próprio JDK (`com.sun.net.httpserver`)
- **JDBC puro** (`java.sql`): todo o SQL é escrito à mão em `PreparedStatement`
- **MySQL 8.4**
- Front-end em **jQuery + Bootstrap**, servidos localmente

Única dependência do `pom.xml`: o driver JDBC do MySQL.

## Como executar

Pré-requisitos: JDK 25 e Docker.

1. Suba o banco (cria o schema e os dados de exemplo na primeira vez):

       docker compose up -d

2. Rode a classe `br.com.oficina.App` pela IDE.

3. Abra <http://localhost:8080>.

A configuração fica em `src/main/resources/config.properties` e pode ser
sobrescrita por variáveis de ambiente (`PORT`, `DB_URL`, `DB_USER`,
`DB_PASSWORD`, `DB_AUTO_SCHEMA`).

## Estado atual

- [x] Modelagem do banco (5 tabelas, com FK, UNIQUE e CHECK)
- [x] Infraestrutura HTTP + JSON
- [x] CRUD de clientes
- [ ] CRUD de serviços
- [ ] CRUD de veículos
- [ ] Ordens de serviço com itens
