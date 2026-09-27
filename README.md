# Oficina Mecânica — Sistema de Ordens de Serviço

Aplicação web completa integrada a um SGBD relacional, desenvolvida para a
atividade NP1 da disciplina de **Banco de Dados**.

> **Requisito para compilar: JDK 21 ou mais novo.** Confira com `java -version`.
> Se precisar instalar, use o [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21).
> Passo a passo completo no [guia de execução](#3-guia-de-instalação-e-execução).

## Identificação

| | |
|---|---|
| **Instituição** | Universidade Paulista (UNIP) |
| **Curso** | Análise e Desenvolvimento de Sistemas |
| **Disciplina** | Banco de Dados |
| **Turma** | DS4P-17 |
| **Atividade** | Trabalho NP1 |

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

## Guia rápido para o avaliador

Onde encontrar a evidência de cada um dos cinco critérios técnicos:

| # | Critério | Onde verificar |
|---|---|---|
| 1 | **Modelagem e Integridade** | [DER e dicionário de dados](#2-modelagem-de-dados) · DDL completo em [`sql/01_schema.sql`](sql/01_schema.sql) · normalização em [`docs/DER.md`](docs/DER.md) |
| 2 | **Manipulação de Dados (CRUD)** | [Escopo funcional](#escopo-funcional) · SQL em [`src/main/java/br/com/oficina/dao/`](src/main/java/br/com/oficina/dao/) · [transação explícita](#o-trecho-que-mais-importa-transação) |
| 3 | **Aderência às Restrições** | [Seção dedicada](#5-aderência-às-restrições-técnicas) — uma única dependência no [`pom.xml`](pom.xml) |
| 4 | **Funcionalidade e Usabilidade** | [Guia de execução](#3-guia-de-instalação-e-execução) · [validações em três camadas](#validações-e-tratamento-de-erros) |
| 5 | **Documentação e Reprodutibilidade** | Este arquivo · [evidências visuais](#4-evidências-visuais) · consultas de comprovação em [`docs/evidencias.sql`](docs/evidencias.sql) |

**Para rodar em 4 comandos**, veja o [guia de execução](#3-guia-de-instalação-e-execução).
Não há banco de dados para instalar: o banco é um arquivo, criado na primeira execução.

---

## 1. Descrição do Projeto

### Tema

Sistema de gestão para uma **oficina mecânica de bairro**. O fluxo do negócio é
direto: o cliente traz o veículo e relata um problema; a oficina abre uma
**Ordem de Serviço (OS)**, lança nela os serviços executados e as peças
aplicadas, acompanha o status até a conclusão e fecha com o valor total.

O domínio foi escolhido porque produz naturalmente os relacionamentos que a
disciplina cobra: um **1:N** (cliente possui veículos), outro **1:N** (veículo é
atendido em ordens de serviço) e um **N:N** resolvido por entidade associativa
com atributos próprios (a OS lança vários serviços, cada um com quantidade e
preço praticado).

### Escopo funcional

| Módulo | Operações |
|---|---|
| **Clientes** | Criar, listar, buscar por nome/CPF, editar e excluir |
| **Veículos** | Criar, listar, buscar por placa/marca/modelo, editar, excluir e ver o histórico de atendimentos |
| **Serviços** | Catálogo de mão de obra e peças: criar, listar, buscar, editar, excluir e ativar/inativar |
| **Ordens de Serviço** | Abrir com itens, listar, filtrar por status, editar, excluir, acompanhar o status e imprimir a via em A4 |
| **Dashboard** | Indicadores da oficina, distribuição por situação, movimento dos últimos 6 meses e itens que mais faturam |
| **Usuários** | Login por e-mail e senha, perfis ADMIN e ATENDENTE, criar/editar/excluir contas e trocar senha |
| **Backup** | Cópia automática diária e sob demanda, com rotação das 30 últimas |

As quatro operações CRUD estão implementadas em **todas** as entidades.

### Regras de negócio

Cada regra aponta onde ela é realmente garantida — quase sempre no banco.

| # | Regra | Onde é garantida |
|---|---|---|
| RN01 | O cliente é identificado pelo CPF, que não se repete | `UNIQUE (cpf)` + dígito verificador em `Validators.cpfValido` |
| RN02 | Um cliente pode ter vários veículos; todo veículo tem exatamente um dono | FK `veiculo.cliente_id` (1:N) |
| RN03 | Duas placas iguais não convivem no sistema | `UNIQUE (placa)` |
| RN04 | Cliente com veículo cadastrado não pode ser excluído | `ON DELETE RESTRICT` |
| RN05 | Toda OS é aberta para um veículo já cadastrado | FK `ordem_servico.veiculo_id` |
| RN06 | Veículo com OS registrada não pode ser excluído — o histórico é preservado | `ON DELETE RESTRICT` |
| RN07 | O status da OS só assume um de quatro valores: ABERTA, EM_ANDAMENTO, CONCLUIDA ou CANCELADA. O fluxo usual segue essa ordem, mas a transição entre eles não é restringida | `CHECK (status IN (...))` + `STATUS_VALIDOS` em `OrdemServicoHandler` |
| RN08 | A conclusão nunca é anterior à abertura | `CHECK (data_conclusao >= data_abertura)` |
| RN09 | Ao concluir uma OS a data de conclusão é preenchida; ao reabrir, volta a nulo | `OrdemServicoDao.atualizar` |
| RN10 | Uma OS é composta por itens: serviços e/ou peças, cada um com quantidade | Associativa `item_os` (N:N) |
| RN11 | O mesmo serviço entra uma única vez por OS — repetir é aumentar a quantidade | `UNIQUE (ordem_servico_id, servico_id)` |
| RN12 | Quantidade sempre positiva e valor nunca negativo | `CHECK (quantidade > 0)`, `CHECK (valor_unitario_centavos >= 0)` |
| RN13 | O preço cobrado é congelado no lançamento: reajuste de tabela não altera OS antiga | `item_os.valor_unitario_centavos`, copiado de `servico.preco_centavos` |
| RN14 | O total da OS nunca é armazenado — é sempre calculado a partir dos itens | `SUM(quantidade * valor_unitario_centavos)` |
| RN15 | Excluir uma OS apaga seus itens, mas nunca o serviço do catálogo | `CASCADE` em `item_os`, `RESTRICT` em `servico` |
| RN16 | Serviço inativado some das OS novas, mas continua no histórico | `servico.ativo` + filtro `?ativos=true` |
| RN17 | A gravação de uma OS com seus itens é tudo ou nada | Transação explícita em `OrdemServicoDao.inserirComItens` |
| RN18 | Ninguém usa o sistema sem entrar com e-mail e senha | `FiltroAutenticacao` — sessão exigida na API e nas páginas |
| RN19 | A senha nunca é armazenada, só o hash PBKDF2 com sal | `Senhas.gerarHash` + `CHECK (senha_hash LIKE 'pbkdf2\_sha256$%' ESCAPE '\')` |
| RN20 | O e-mail identifica a conta e não se repete | `UNIQUE (email)` em `usuario` |
| RN21 | Cinco senhas erradas bloqueiam a conta por 15 minutos | `usuario.tentativas_falhas` + `usuario.bloqueado_ate` |
| RN22 | Só o perfil ADMIN administra usuários, e o último ADMIN ativo não pode ser removido | `UsuarioHandler` + `UsuarioDao.contarAdminsAtivos` |

---

## 2. Modelagem de Dados

**SGBD:** SQLite 3 — banco em arquivo, sem servidor · **Charset:** UTF-8

O schema tem **6 tabelas** e **31 restrições nomeadas**: 6 chaves primárias,
4 chaves estrangeiras, 5 de unicidade e 16 de verificação (`CHECK`). São mais
`CHECK` do que teria em MySQL porque o SQLite não tem `ENUM` nem `UNSIGNED`:
o que lá era tipo, aqui vira restrição explícita — e fica visível no DDL.
Dá para conferir no próprio banco com a consulta 8 de
[`docs/evidencias.sql`](docs/evidencias.sql).

### Diagrama Entidade-Relacionamento

```mermaid
erDiagram
    CLIENTE ||--o{ VEICULO : "possui"
    VEICULO ||--o{ ORDEM_SERVICO : "é atendido em"
    ORDEM_SERVICO ||--o{ ITEM_OS : "é composta por"
    SERVICO ||--o{ ITEM_OS : "é lançado em"

    CLIENTE {
        INTEGER id PK "AUTOINCREMENT"
        TEXT nome "NOT NULL"
        TEXT cpf UK "NOT NULL, somente dígitos"
        TEXT telefone "NULL"
        TEXT email "NULL"
        TEXT criado_em "datetime(now, localtime)"
    }

    VEICULO {
        INTEGER id PK "AUTOINCREMENT"
        INTEGER cliente_id FK "NOT NULL"
        TEXT placa UK "NOT NULL"
        TEXT marca "NOT NULL"
        TEXT modelo "NOT NULL"
        INTEGER ano "CHECK: entre 1950 e 2100"
        TEXT cor "NULL"
    }

    SERVICO {
        INTEGER id PK "AUTOINCREMENT"
        TEXT descricao UK "NOT NULL"
        TEXT tipo "CHECK: MAO_DE_OBRA / PECA"
        INTEGER preco_centavos "CHECK: não negativo"
        INTEGER ativo "DEFAULT 1"
    }

    ORDEM_SERVICO {
        INTEGER id PK "AUTOINCREMENT"
        INTEGER veiculo_id FK "NOT NULL"
        TEXT status "CHECK: ABERTA / EM_ANDAMENTO / ..."
        TEXT descricao_problema "NOT NULL"
        INTEGER km_atual "NULL"
        TEXT data_abertura "datetime(now, localtime)"
        TEXT data_conclusao "NULL, CHECK: não anterior à abertura"
        TEXT observacoes "NULL"
    }

    ITEM_OS {
        INTEGER id PK "AUTOINCREMENT"
        INTEGER ordem_servico_id FK "NOT NULL"
        INTEGER servico_id FK "NOT NULL"
        INTEGER quantidade "CHECK: maior que zero"
        INTEGER valor_unitario_centavos "CHECK: não negativo"
    }

    USUARIO {
        INTEGER id PK "AUTOINCREMENT"
        TEXT nome "NOT NULL"
        TEXT email UK "NOT NULL, é o login"
        TEXT senha_hash "NOT NULL, PBKDF2 — nunca a senha"
        TEXT perfil "CHECK: ADMIN / ATENDENTE"
        INTEGER ativo "DEFAULT 1"
        TEXT bloqueado_ate "NULL, bloqueio por força bruta"
    }
```

O mesmo diagrama como imagem, com todas as colunas — para quem abrir o
arquivo fora do GitHub, onde o Mermaid não é desenhado:

![DER em imagem](docs/der.png)

Em texto:

    CLIENTE  1 ────< N  VEICULO  1 ────< N  ORDEM_SERVICO
                                                  │ 1
                                                  ∨ N
                                              ITEM_OS
                                                  ∧ N
                                                  │ 1
                                              SERVICO

`USUARIO` aparece isolada de propósito: ela é **controle de acesso**, não domínio
da oficina. Não há FK ligando usuário a ordem de serviço porque a OS é um fato do
negócio — ela continua válida mesmo que o funcionário que a digitou seja excluído
do sistema depois.

### Cardinalidades

| Relacionamento | Cardinalidade | Leitura |
|---|---|---|
| CLIENTE → VEICULO | 1 : N | Um cliente possui zero ou muitos veículos; todo veículo tem um dono |
| VEICULO → ORDEM_SERVICO | 1 : N | Um veículo é atendido em zero ou muitas OS; toda OS é de um veículo |
| ORDEM_SERVICO → ITEM_OS | 1 : N | Uma OS é composta por zero ou muitos itens |
| SERVICO → ITEM_OS | 1 : N | Um serviço do catálogo pode ser lançado em muitas OS |
| ORDEM_SERVICO ↔ SERVICO | N : N | Resolvido por `ITEM_OS`, que guarda quantidade e preço praticado |

### Integridade referencial

| FK | Referencia | ON DELETE | ON UPDATE | Por quê |
|---|---|---|---|---|
| `veiculo.cliente_id` | `cliente.id` | `RESTRICT` | `CASCADE` | Não se apaga um cliente que ainda tem veículo na oficina |
| `ordem_servico.veiculo_id` | `veiculo.id` | `RESTRICT` | `CASCADE` | O histórico de OS do veículo não pode ser perdido |
| `item_os.ordem_servico_id` | `ordem_servico.id` | `CASCADE` | `CASCADE` | Itens só existem dentro de uma OS |
| `item_os.servico_id` | `servico.id` | `RESTRICT` | `CASCADE` | Serviço já lançado em OS não some do catálogo |

### Normalização

O modelo está na **3ª Forma Normal**. Duas decisões merecem destaque:

**1. O total da OS não é armazenado.** Guardar `ordem_servico.valor_total` seria
dependência transitiva de dados que já estão em `item_os`, e qualquer alteração
de item deixaria o total defasado. O valor é sempre calculado:

```sql
SELECT SUM(quantidade * valor_unitario) FROM item_os WHERE ordem_servico_id = ?;
```

**2. `item_os.valor_unitario` não é redundância — é histórico.** À primeira vista
o preço já está em `servico.preco`. Mas `servico.preco` é o preço *de tabela
hoje*, e `item_os.valor_unitario` é o preço *cobrado naquela OS*. São fatos
diferentes: se a oficina reajustar a tabela, as OS já emitidas não podem mudar de
valor.

Dicionário de dados completo em [`docs/DER.md`](docs/DER.md).

### Script DDL

O DDL completo e comentado está em [`sql/01_schema.sql`](sql/01_schema.sql), e é
**idempotente** (`CREATE ... IF NOT EXISTS`) — pode rodar quantas vezes for
preciso. A carga de dados de exemplo está em [`sql/02_seed.sql`](sql/02_seed.sql).

Amostra, com os quatro tipos de restrição em uma tabela só:

```sql
CREATE TABLE IF NOT EXISTS item_os (
  id                INTEGER  NOT NULL,
  ordem_servico_id  INTEGER  NOT NULL,
  servico_id        INTEGER  NOT NULL,
  quantidade        INTEGER  NOT NULL DEFAULT 1,
  valor_unitario_centavos INTEGER NOT NULL,
  CONSTRAINT pk_item_os          PRIMARY KEY (id AUTOINCREMENT),
  CONSTRAINT uq_item_os          UNIQUE (ordem_servico_id, servico_id),
  CONSTRAINT ck_item_os_qtd      CHECK (quantidade > 0),
  CONSTRAINT ck_item_os_valor    CHECK (valor_unitario_centavos >= 0),
  CONSTRAINT fk_item_os_os       FOREIGN KEY (ordem_servico_id) REFERENCES ordem_servico (id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_item_os_servico  FOREIGN KEY (servico_id) REFERENCES servico (id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);
```

> **As chaves estrangeiras do SQLite vêm desligadas por padrão.** Quem as liga é
> a aplicação, com `PRAGMA foreign_keys = ON` em **toda** conexão
> ([`db/Database.java`](src/main/java/br/com/oficina/db/Database.java)). Sem
> isso, RN04, RN06 e RN15 deixariam de valer sem nenhum erro aparecer.

> **Dinheiro é inteiro de centavos.** O SQLite não tem tipo decimal de verdade:
> `DECIMAL(10,2)` acabaria guardando ponto flutuante, e somar dinheiro em float
> pode render centavo errado. `4590` é R$ 45,90, e `SUM()` é exato. A conversão
> fica em [`util/Dinheiro.java`](src/main/java/br/com/oficina/util/Dinheiro.java).

---

## 3. Guia de Instalação e Execução

Instruções a partir do **repositório limpo**, em Windows, Linux ou macOS.

### Pré-requisitos

| Programa | Versão | Para quê |
|---|---|---|
| **JDK** | 21 ou mais novo | Compilar e rodar |
| **Git** | qualquer | Clonar o repositório |

**Não é preciso instalar banco de dados.** O banco é um arquivo SQLite criado
na primeira execução. Também não é preciso instalar o Maven: o repositório traz
o Maven Wrapper (`mvnw` e `mvnw.cmd`), que baixa a versão certa sozinho.

Confira no terminal:

    java -version            # precisa mostrar 21 ou mais

### Passo 1 — Clonar

    git clone https://github.com/Lindolfoo/OficinaMecanica.git
    cd OficinaMecanica

### Passo 2 — Gerar o executável

    ./mvnw package          # Linux e macOS
    mvnw.cmd package        # Windows

Gera `target/oficina.jar`, com o driver do SQLite já embutido.

### Passo 3 — Rodar

    java -jar target/oficina.jar

Na primeira execução o programa cria o arquivo do banco, aplica o schema e
carrega os dados de exemplo. Em seguida abre a janela do sistema sozinho
(Edge ou Chrome em modo aplicativo; se não achar, usa o navegador padrão) e
instala um ícone na área de notificação, com a opção **Sair**.

Se preferir abrir na mão, o endereço aparece no console:
`Servidor no ar: http://localhost:8080`.

### Passo 4 — Primeiro acesso

Não existe senha padrão. Na primeira execução a tela de entrada vira **Primeiro
acesso** e pede nome, e-mail e senha para criar o administrador. Essa conta é
sua; a tela deixa de oferecer isso assim que ela existir.

Depois dá para trocar a senha em **menu do usuário › Trocar minha senha** e
cadastrar a equipe em **Administração › Usuários**.

### Onde ficam os dados

O banco, os backups e a trava de instância ficam na pasta de dados do usuário —
**nunca junto do programa**, porque em `C:\Program Files` o Windows bloqueia
a gravação:

| Sistema | Pasta |
|---|---|
| Windows | `%LOCALAPPDATA%\OficinaMecanica\` |
| Linux | `~/.local/share/OficinaMecanica/` |
| macOS | `~/Library/Application Support/OficinaMecanica/` |

Dentro dela: `oficina.db` (o banco inteiro, um arquivo só) e `backups/`.

**Para levar os dados para outro computador**, copie o `oficina.db`. Para uma
cópia segura com o sistema aberto, use **Administração › Backup**, que gera o
arquivo com `VACUUM INTO` — copiar o `.db` na mão enquanto alguém grava pode
gerar arquivo corrompido.

### Configuração

Os padrões estão em [`src/main/resources/config.properties`](src/main/resources/config.properties).
Qualquer chave pode ser sobrescrita por variável de ambiente:

| Chave | Variável | Padrão | Para quê |
|---|---|---|---|
| `server.host` | `HOST` | `127.0.0.1` | `0.0.0.0` libera o acesso pela rede |
| `server.port` | `PORT` | `8080` | Porta; se estiver ocupada, o programa escolhe uma livre |
| `db.pasta` | `OFICINA_DADOS` | (vazio) | Outra pasta para banco e backups |
| `db.autoSchema` | `DB_AUTO_SCHEMA` | `true` | Aplica o DDL na subida |
| `db.seedInicial` | `DB_SEED_INICIAL` | `true` | Carrega os dados de exemplo quando o banco nasce vazio |
| `auth.sessaoMinutos` | `SESSAO_MINUTOS` | `30` | Inatividade até a sessão cair |
| `auth.cookieSeguro` | `COOKIE_SEGURO` | `false` | `true` ao publicar em HTTPS |
| `app.abrirNavegador` | `APP_ABRIR_NAVEGADOR` | `true` | Abrir a janela ao iniciar |
| `app.bandeja` | `APP_BANDEJA` | `true` | Ícone na área de notificação |

Exemplo, guardando os dados em outra pasta e sem abrir janela:

    OFICINA_DADOS=/mnt/dados/oficina APP_ABRIR_NAVEGADOR=false java -jar target/oficina.jar

### Instaladores prontos

Cada versão publicada gera os pacotes dos três sistemas, pelo GitHub Actions
([`.github/workflows/pacotes.yml`](.github/workflows/pacotes.yml)). Baixe em
[Releases](https://github.com/Lindolfoo/OficinaMecanica/releases):

| Sistema | Arquivo | Observação |
|---|---|---|
| Windows | `Oficina.Mecanica-X.Y.Z.exe` | Instala na pasta do usuário, sem pedir administrador |
| Ubuntu / Debian | `oficina-mecanica_X.Y.Z_amd64.deb` | `sudo apt install ./arquivo.deb` |
| Fedora / openSUSE | `oficina-mecanica-X.Y.Z.rpm` | `sudo dnf install ./arquivo.rpm` |
| macOS | `Oficina Mecanica-X.Y.Z.dmg` | Veja o aviso abaixo |
| Qualquer um | `oficina.jar` | `java -jar oficina.jar` — exige Java 21+ |

Todos **embutem o Java**, menos o `.jar`. Não há um arquivo único que sirva aos
três sistemas: cada um tem formato próprio de instalação. O `.jar` é o que mais
se aproxima disso, com o custo de exigir Java na máquina.

**No macOS**, o `.dmg` não é assinado — assinar exige conta paga de
desenvolvedor da Apple. Na primeira abertura o sistema avisa que o
desenvolvedor não é identificado; abra pelo menu de contexto (**Abrir**) em vez
do duplo clique, e confirme uma vez.

Os três jobs rodam em paralelo, cada um no seu sistema, porque o `jpackage`
**não faz compilação cruzada**: ele usa as ferramentas do sistema em que está
(WiX no Windows, dpkg no Linux, hdiutil no macOS).

### Problemas comuns

| Sintoma | Causa provável | Solução |
|---|---|---|
| `release version 21 not supported` | JDK anterior ao 21 | Instale o [Temurin 21](https://adoptium.net/temurin/releases/?version=21) |
| `./mvnw: Permission denied` | Script sem permissão | `chmod +x mvnw` |
| "O sistema já está aberto" | Já há uma cópia rodando | É o esperado: só uma por vez. Use o ícone da bandeja |
| A janela não abre sozinha | Sem Edge/Chrome, ou sem ambiente gráfico | Abra o endereço que aparece no console |
| `não foi possível abrir o banco` | Pasta sem permissão de escrita | Informe outra em `OFICINA_DADOS` |

### Recomeçar do zero

Feche o programa e apague o arquivo do banco (`oficina.db`) na pasta de dados.
Na próxima execução ele é recriado com os dados de exemplo — e o primeiro
acesso volta a pedir a criação do administrador.

---

## 4. Evidências Visuais

### Interface

| | |
|---|---|
| ![Tela de login](docs/prints/00-login.png) | ![Dashboard](docs/prints/25-dashboard.png) |
| Tela de login — acesso por e-mail e senha | Dashboard: indicadores, distribuição por situação e movimento por mês |
| ![Clientes](docs/prints/01-clientes-lista.png) | ![Ordens de serviço](docs/prints/07-ordens-lista.png) |
| Lista de clientes com busca | Ordens de serviço com situação e total calculado |
| ![Veículos](docs/prints/05-veiculos-lista.png) | ![Serviços](docs/prints/06-servicos-catalogo.png) |
| Veículos com o proprietário (o 1:N na tela) | Catálogo com preço e itens ativos/inativos |
| ![Usuários](docs/prints/26-usuarios.png) | |
| Administração de contas — só o perfil ADMIN chega aqui | |

### CRUD em funcionamento

| | |
|---|---|
| ![Cadastro](docs/prints/02-cliente-cadastro.png) | ![Cliente criado](docs/prints/03-cliente-criado.png) |
| **CREATE** — formulário preenchido | O cliente #7 aparece na lista, que passa a 5 registros |
| ![Edição](docs/prints/23-crud-edicao-alterando.png) | ![Edição salva](docs/prints/24-crud-edicao-salva.png) |
| **UPDATE** — alterando o telefone do cliente #7 | A lista já com o telefone novo |
| ![Exclusão](docs/prints/20-crud-exclusao-confirmacao.png) | ![Validação de CPF](docs/prints/04-validacao-cpf.png) |
| **DELETE** — o registro sumiu da lista, que volta a 4 | **Validação** — CPF rejeitado pelo dígito verificador |
| ![OS com itens](docs/prints/08-os-com-itens.png) | ![Filtro por status](docs/prints/10-filtro-status.png) |
| OS com itens e total calculado | Filtro por status |

### Persistência no banco

Cada imagem abaixo mostra **o comando executado e a saída real do cliente
`mysql`**. Todas as consultas estão em [`docs/evidencias.sql`](docs/evidencias.sql)
e podem ser reproduzidas por quem avalia, com um comando só (veja
[Como reproduzir](#como-reproduzir-as-evidências)).

| | |
|---|---|
| ![Estrutura](docs/prints/12-banco-estrutura.png) | ![Cliente e veículo](docs/prints/13-banco-cliente-veiculo.png) |
| As 6 tabelas em InnoDB/utf8mb4 e a contagem real de linhas | O 1:N entre cliente e veículo |
| ![Clientes gravados](docs/prints/19-banco-clientes-gravados.png) | ![Cliente excluído](docs/prints/21-banco-cliente-excluido.png) |
| O cliente #7, criado pela tela, gravado no banco — repare no `criado_em` diferente dos registros de carga | Depois do DELETE, o #7 não está mais na tabela |
| ![Total calculado](docs/prints/14-banco-os-total-calculado.png) | ![Itens da OS](docs/prints/15-banco-itens-da-os.png) |
| JOIN duplo e total por `SUM` — não existe coluna de total | Os itens gravados pela transação (associativa N:N) |
| ![Constraints](docs/prints/16-banco-constraints.png) | ![Políticas das FKs](docs/prints/17-banco-fk-politicas.png) |
| As 24 restrições: o total por tipo e cada uma nomeada | `RESTRICT`/`CASCADE` de cada chave estrangeira |
| ![FK bloqueia](docs/prints/18-banco-fk-bloqueia-exclusao.png) | ![Senha em hash](docs/prints/27-banco-senha-em-hash.png) |
| `ERROR 1451` — a FK recusa a exclusão e nomeia a constraint | A senha só existe como hash PBKDF2, e o `CHECK` recusa texto puro (`ERROR 3819`) |

Todos os prints estão em [`docs/prints/`](docs/prints/).

### Como reproduzir as evidências

    sqlite3 -header -column "CAMINHO/oficina.db" < docs/evidencias.sql

O caminho aparece no console quando a aplicação sobe ("Arquivo: ...").

O arquivo traz ainda três comandos que **falham de propósito** — o erro é
justamente a prova de que a integridade está ativa (FK bloqueando exclusão,
`CHECK` recusando senha em texto puro e `CHECK` recusando CPF inválido).

---

## 5. Aderência às Restrições Técnicas

| Restrição da atividade | Como o projeto cumpre | Onde verificar |
|---|---|---|
| SGBD: SQL Server, MySQL ou SQLite | **SQLite 3** — banco em arquivo | [`sql/01_schema.sql`](sql/01_schema.sql) |
| Linguagem: C#, Python, TypeScript, PHP ou Java | **Java 21** | [`pom.xml`](pom.xml) |
| **Proibido framework no back-end** | Nenhum. Servidor HTTP é o `com.sun.net.httpserver`, que já vem no JDK | [`App.java`](src/main/java/br/com/oficina/App.java) |
| **Proibido ORM** | Nenhum. Todo mapeamento é escrito à mão nos DAOs | [`dao/`](src/main/java/br/com/oficina/dao/) |
| SQL direto via driver nativo | JDBC puro (`java.sql`) com `PreparedStatement` em todas as consultas | [`ClienteDao.java`](src/main/java/br/com/oficina/dao/ClienteDao.java) |
| Front-end: HTML5, CSS3, JS puro, jQuery, Bootstrap | Exatamente isso — jQuery 3 e Bootstrap 5 (com Bootstrap Icons), servidos localmente | [`static/vendor/`](src/main/resources/static/vendor/) |
| **Proibido SPA** (React, Angular, Vue) | Nenhum. A navegação é jQuery puro | [`app.js`](src/main/resources/static/js/app.js) |

**O `pom.xml` tem uma única dependência: o driver JDBC do SQLite (sqlite-jdbc)** —
exatamente o "driver nativo de conexão da linguagem" que a atividade pede. Nem
para criptografia há biblioteca externa: o PBKDF2 vem do `javax.crypto`, do
próprio JDK. Os gráficos do dashboard são SVG escrito à mão, sem biblioteca de
gráficos.

Sobre `Statement` × `PreparedStatement`: o projeto tem **38 usos de
`PreparedStatement` e um único `createStatement`**, em
[`Database.java`](src/main/java/br/com/oficina/db/Database.java#L47), usado
apenas para executar o script DDL na subida — onde não há entrada de usuário.
Toda consulta que recebe dado de fora é parametrizada.

---

## Arquitetura do código

    sql/                      DDL e carga inicial
    .github/workflows/        gera o instalador .exe no GitHub Actions
    src/main/java/br/com/oficina/
      App.java                sobe o servidor, registra rotas e filtros
      config/                 leitura de config.properties e variáveis de ambiente
      db/Database.java        conexões JDBC e aplicação do schema
      http/                   Json, HttpUtil, BaseHandler, StaticHandler
      security/               Senhas (PBKDF2), Sessoes e os filtros de acesso
      desktop/                janela do navegador, bandeja e instância única
      db/Backup.java          cópias de segurança com VACUUM INTO
      model/                  records espelhando as tabelas
      dao/                    o SQL de cada tabela
      api/                    validação de entrada e tradução HTTP
      util/Validators.java    CPF (módulo 11), e-mail e força da senha
      util/Dinheiro.java      converte reais e centavos
    src/main/resources/static/
      login.html, js/login.js   tela de entrada
      index.html, js/app.js     menu lateral, dashboard e os módulos
      css/style.css             identidade visual
      vendor/                   jQuery e Bootstrap locais — roda sem internet
    docs/                     DER, arquitetura, evidências e prints

Cada módulo tem sempre as mesmas três camadas:

    model/X.java      -> espelho da tabela (record)
    dao/XDao.java     -> o SQL: listar, buscarPorId, inserir, atualizar, excluir
    api/XHandler.java -> validação de entrada + tradução HTTP

Fluxo de uma requisição:

    navegador (jQuery $.ajax)
        -> FiltroSeguranca      (cabeçalhos de segurança)
        -> FiltroAutenticacao   (exige sessão; confere o token CSRF)
        -> App.java (rota)
        -> XHandler (valida a entrada)
        -> XDao (PreparedStatement)
        -> SQLite (arquivo oficina.db)
        -> volta como JSON

[`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) sugere a ordem de leitura do código.

### O trecho que mais importa: transação

[`OrdemServicoDao.inserirComItens`](src/main/java/br/com/oficina/dao/OrdemServicoDao.java)
é o único ponto com transação explícita — a gravação de uma OS com seus itens é
tudo ou nada:

```java
c.setAutoCommit(false);   // abre a transação
// ... grava o cabeçalho e depois cada item ...
c.commit();               // confirma tudo
// no catch: c.rollback() -> desfaz tudo
```

Sem isso, uma falha ao gravar o terceiro item deixaria no banco uma OS pela metade.

### Validações e tratamento de erros

São **três camadas**, de propósito:

1. **HTML** (`required`, `pattern`) — conveniência; o usuário vê na hora.
2. **Handler** (Java) — a regra real; o HTML pode ser burlado pelo DevTools.
3. **Banco** (`CHECK`/`UNIQUE`/`FK`) — a última linha de defesa, vale até para
   quem acessar o banco por fora da aplicação.

Os erros do banco são traduzidos para mensagens em português e para o status HTTP
certo em [`BaseHandler`](src/main/java/br/com/oficina/http/BaseHandler.java):

| Status | Quando |
|---|---|
| `400` | Entrada inválida (CPF, placa, campo obrigatório) ou `CHECK` do banco |
| `401` | Sem sessão, ou e-mail/senha inválidos |
| `403` | Sem permissão (ATENDENTE em rota de ADMIN) ou sem token CSRF |
| `404` | Registro não encontrado |
| `409` | `UNIQUE` ou `FOREIGN KEY` — CPF repetido, ou exclusão com dependentes |
| `429` | Conta bloqueada por senhas erradas, ou tentativas demais do mesmo endereço |

### API

Respostas em JSON; entrada como formulário (`application/x-www-form-urlencoded`).

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/clientes` | lista — `?busca=texto` |
| `POST` | `/api/clientes` | cria — `nome, cpf, telefone, email` |
| `PUT` | `/api/clientes/{id}` | altera |
| `DELETE` | `/api/clientes/{id}` | exclui |
| `GET` | `/api/veiculos` | lista — `?busca=`, `?clienteId=` |
| `GET` | `/api/servicos` | lista — `?busca=`, `?ativos=true` |
| `GET` | `/api/ordens` | lista — `?status=`, `?busca=`, `?veiculoId=` |
| `GET` | `/api/ordens/resumo` | quantidade e total por status (`GROUP BY`) |
| `GET` | `/api/ordens/{id}` | uma OS com seus itens |
| `POST` | `/api/ordens` | cria a OS e os itens **em uma transação** |
| `POST` | `/api/ordens/{id}/itens` | adiciona um item |
| `DELETE` | `/api/ordens/{id}/itens/{itemId}` | remove um item |
| `GET` | `/api/dashboard` | indicadores, gráficos e últimas OS |
| `POST` | `/api/auth/login` · `/logout` · `/senha` | entrar, sair e trocar a senha |
| `GET` | `/api/usuarios` | lista contas — **só ADMIN** |
| `GET` | `/api/auth/estado` | diz se o sistema ainda não tem administrador |
| `POST` | `/api/auth/configurar` | cria o primeiro administrador (409 depois disso) |
| `GET` `POST` | `/api/backup` | lista e gera cópias de segurança — **só ADMIN** |

Veículos e serviços seguem o mesmo padrão CRUD dos clientes. Toda rota que altera
dados exige o cabeçalho `X-CSRF-Token`.

---

## Segurança

Não era exigência da atividade, mas o sistema tem login e vale registrar o que
foi feito:

| Ataque | Defesa |
|---|---|
| Vazamento do banco expondo senhas | Senha nunca é gravada: só o **PBKDF2-SHA256**, 210 mil iterações, com sal por usuário |
| Gravar senha em texto direto no banco | `CHECK (senha_hash LIKE 'pbkdf2\_sha256$%' ESCAPE '\')` — o banco recusa |
| Força bruta | 5 erros bloqueiam a conta por 15 min; no máximo 10 tentativas/min por endereço |
| Enumeração de usuários | Mensagem sempre igual e tempo de resposta constante |
| Editar o cookie para virar ADMIN | O cookie só tem um token aleatório de 256 bits |
| Roubo de cookie por script injetado | Cookie `HttpOnly` + CSP travando `script-src` em `'self'` |
| CSRF | Token obrigatório em `POST`/`PUT`/`DELETE` + `SameSite=Strict` |
| Senha conhecida em toda instalação | Não há senha padrão: o administrador é criado no primeiro acesso |
| Site malicioso falando com o programa local | O `Host` precisa ser `localhost`/`127.0.0.1`, senão `403` — impede DNS rebinding, em que o navegador trataria o atacante como mesma origem e nem o CSRF protegeria |
| Envio gigante consumindo memória | Corpo de requisição limitado a 1 MB (`413`) |
| Perda de dados | Backup diário automático com `VACUUM INTO`, rotação de 30, e verificação de integridade antes de copiar |

O que **não** está resolvido, e é honesto dizer: o tráfego é HTTP puro. Como o
servidor escuta só em `127.0.0.1` e o `Host` é conferido, isso é aceitável num
programa de desktop; publicado em rede, pediria um proxy com HTTPS e
`auth.cookieSeguro=true`. As sessões vivem em memória — reiniciar desloga todo
mundo. E qualquer pessoa com acesso ao computador pode copiar o arquivo do
banco: ele não é criptografado.

---

## Licença

MIT — veja [LICENSE](LICENSE).
