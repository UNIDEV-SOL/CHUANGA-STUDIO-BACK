# CHUANGA-STUDIO — Backend

Backend em **Spring Boot 3 / Java 21** para o projeto CHUANGA-STUDIO, seguindo a mesma
estrutura e convenções usadas no SPO-BACK (`BaseAuditEntity`, soft delete, `usuarioService.obterUsuarioOnline()`,
Flyway, `GlobalExceptionHandler`, DTOs de request/response, JWT stateless).

## Stack

- Java 21, Spring Boot 3.3.x
- Spring Web, Spring Security (JWT stateless), Spring Data JPA
- PostgreSQL + Flyway
- Lombok, springdoc-openapi (Swagger em `/api/docs`)

## Como correr localmente

1. Cria a base de dados PostgreSQL (`chuanga_studio`, ou o nome que definires em `DB_NAME`).
2. Copia `.env.example` para `.env` e ajusta os valores (ou exporta as variáveis no teu shell).
3. Corre:
   ```bash
   mvn spring-boot:run
   ```
4. A API sobe em `http://localhost:8080/api`. Swagger em `http://localhost:8080/api/docs`.

No arranque, o `DataSeeder` cria automaticamente um usuário **admin** com o email/senha
definidos em `ADMIN_SEED_EMAIL` / `ADMIN_SEED_PASSWORD` (por padrão
`admin@chuangastudio.co.mz` / `ChuangaAdmin123` — **troca isto em produção**).

## Estrutura de pastas

```
src/main/java/mz/netlineserv/chuangastudio/
├── ChuangaStudioApplication.java
├── common/            (BaseAuditEntity, ApiResponse, exceções, GlobalExceptionHandler)
├── config/            (SecurityConfig, JpaAuditingConfig, DataSeeder)
├── security/           (JwtService, JwtAuthenticationFilter, UsuarioAutenticado, UsuarioDetailsServiceImpl)
├── domain/
│   ├── entity/         (Usuario, Servico, Marcacao, MarcacaoServico, Cotacao, CotacaoServico)
│   └── enums/          (PapelUsuario, StatusMarcacao, MetodoPagamento, StatusCotacao)
├── repository/
├── service/ + service/impl/
├── controller/
└── dto/
    ├── request/
    └── response/

src/main/resources/db/migration/   (V1..V4 — Flyway)
```

## Mapeamento rota do frontend → endpoint

| Página / fluxo do frontend                 | Endpoint                                            | Autenticação           |
|--------------------------------------------|-----------------------------------------------------|------------------------|
| `/servicos`, `/cotacao`, `/reservar`       | `GET /publico/servicos`                             | Público                |
| `/entrar` (login)                          | `POST /auth/entrar`                                 | Público                |
| `/entrar` (cadastro)                       | `POST /auth/cadastrar`                              | Público                |
| `/cotacao` (envio)                         | `POST /publico/cotacoes`                            | Público                |
| `/reservar` (passo final do wizard)        | `POST /publico/marcacoes`                           | Público ou autenticado |
| `/minha-area` (perfil)                     | `GET /minha-area/perfil`                            | Cliente                |
| `/minha-area` (próximas/histórico)         | `GET /minha-area/marcacoes`                         | Cliente                |
| `/minha-area` (cancelar)                   | `PATCH /minha-area/marcacoes/{id}/cancelar`         | Cliente                |
| `/admin` (visão geral)                     | `GET /admin/estatisticas`                           | Admin                  |
| `/admin` (próximas marcações)              | `GET /admin/marcacoes/proximas`                     | Admin                  |
| `/admin` (atividade recente)               | `GET /admin/atividade-recente`                      | Admin                  |
| `/admin/clientes`                          | `GET /admin/clientes`                               | Admin                  |
| `/admin/marcacoes` (listar + filtros)      | `GET /admin/marcacoes?status=&busca=`               | Admin                  |
| `/admin/marcacoes` (mudar status)          | `PATCH /admin/marcacoes/{id}/status`                | Admin                  |
| `/admin/marcacoes` (confirmar pagamento)   | `PATCH /admin/marcacoes/{id}/confirmar-pagamento`   | Admin                  |
| `/admin/marcacoes` (eliminar)              | `DELETE /admin/marcacoes/{id}`                      | Admin                  |
| `/admin/cotacoes` (listar + filtro status) | `GET /admin/cotacoes?status=`                       | Admin                  |
| `/admin/cotacoes` (marcar respondido)      | `PATCH /admin/cotacoes/{id}/respondido`             | Admin                  |
| `/admin/cotacoes` (arquivar)               | `PATCH /admin/cotacoes/{id}/arquivar`               | Admin                  |
| `/admin/servicos` (CRUD)                   | `GET/POST/PUT/DELETE /admin/servicos[/{id}]`        | Admin                  |

Todas as respostas seguem o wrapper `ApiResponse<T>`:
```json
{ "sucesso": true, "mensagem": "...", "dados": { ... }, "timestamp": "..." }
```

## Pontos a decidir contigo antes de ligar ao frontend real

1. **Catálogo de serviços real**: a migration `V4__seed_servicos_exemplo.sql` tem dados de
   exemplo. Preciso da lista real (nome, categoria, preço, duração) que está hoje no
   `servicesStore` do frontend para substituir por um seed fiel.
2. **Login com Google**: no frontend é mock (`signInWithGoogle`). Não implementei OAuth2 real
   ainda — dá para adicionar Spring Security OAuth2 Client quando definires o provedor.
3. **Regra de negócio do adiantamento**: fixei 150 MT como valor fixo
   (`MarcacaoServiceImpl.VALOR_ADIANTAMENTO_PADRAO`), conforme a wireframe do wizard. Confirma
   se isto deve ser configurável por serviço/categoria.
4. **Refresh token**: o JWT atual é apenas de acesso (24h por padrão). Se precisares de
   refresh token dedicado, já deixei a env `JWT_REFRESH_EXPIRATION_MS` pronta para isso.

## Próximos passos sugeridos

- Ligar o frontend (`AuthContext`, `bookingsStore`, `quotationsStore`, `servicesStore`) aos
  endpoints acima, trocando `localStorage` por chamadas HTTP (React Query, como nos teus
  outros projetos).
- Adicionar testes de integração para `MarcacaoServiceImpl` (fluxo de reserva é o mais crítico).
- Adicionar paginação em `/admin/marcacoes` e `/admin/cotacoes` quando o volume crescer.
