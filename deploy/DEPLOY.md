# Deploy do Chuanga Studio (chuanga-studio.com)

Guia para hospedar o Chuanga Studio num servidor com Docker, a partir das imagens publicadas no
GitHub Container Registry (ghcr.io).

## Como está montado

```
                      servidor
 internet ─HTTPS─► proxy do servidor (nginx/Caddy, certificado TLS)
                      │  http://127.0.0.1:8085
                      ▼
               ┌─ web (nginx + site React) ──────────────┐
               │   /            → ficheiros do site       │
               │   /api/...     → api:8080                │  rede interna
               └──────────────────────┬──────────────────┘  do Docker
                                      ▼
                         api (Spring Boot) ──► db (PostgreSQL 16, volume dbdata)
```

- **Imagens**: `ghcr.io/unidev-sol/chuanga-studio-frontend` e `ghcr.io/unidev-sol/chuanga-studio-backend`.
- O site e a API estão no **mesmo domínio**: o browser chama `https://chuanga-studio.com/api/...`. Não há CORS
  e basta um certificado.
- Só o contentor `web` tem uma porta no servidor, e só em `127.0.0.1`. A API e a base de dados não ficam
  expostas à internet.
- Todos os dados, incluindo as fotos da galeria e do chat, ficam no volume `dbdata`. **É isso que se faz backup.**

## 1. Publicar as imagens (no GitHub)

Cada repositório tem um workflow (`.github/workflows/docker-publish.yml`) que testa o código e publica
a imagem:

| Ação no GitHub                    | Tag da imagem              |
|-----------------------------------|----------------------------|
| push / merge na `main`            | `latest` e `sha-xxxxxxx`   |
| criar a tag `v1.0.0`              | `1.0.0` e `1.0`            |
| Actions → "Run workflow" (manual) | nome do branch             |

As imagens no ghcr.io são **privadas** por omissão. Há duas opções:

- **Tornar públicas**: GitHub → perfil → Packages → `chuanga-studio-backend` → Package settings → Change
  visibility → Public. Repetir para o frontend. O código não fica público, só a imagem.
- **Manter privadas**: criar um token (GitHub → Settings → Developer settings → Personal access tokens,
  só com a permissão `read:packages`) e dá-lo a quem gere o servidor, para o `docker login` abaixo.

## 2. Preparar o servidor (uma vez)

Requisitos: Docker com o plugin Compose (`docker compose version`), e os registos DNS **A** de
`chuanga-studio.com` e `www.chuanga-studio.com` a apontar para o IP do servidor.

```bash
sudo mkdir -p /opt/chuanga-studio && cd /opt/chuanga-studio
# copiar para aqui o compose.yaml e o .env.example desta pasta
cp .env.example .env
nano .env                     # preencher; gerar segredos com: openssl rand -base64 48
chmod 600 .env                # só o dono lê os segredos

# só se as imagens forem privadas:
echo "<TOKEN>" | docker login ghcr.io -u <utilizador-github> --password-stdin

docker compose pull
docker compose up -d
docker compose ps             # os 3 serviços devem ficar "healthy" (a API demora ~1 min)
curl -s http://127.0.0.1:8085/api/actuator/health   # {"status":"UP"}
```

No primeiro arranque, o Flyway cria as tabelas e é criada a conta admin com `ADMIN_SEED_EMAIL` /
`ADMIN_SEED_PASSWORD`. Convém trocar essa palavra-passe no painel depois de entrar.

## 3. HTTPS no proxy do servidor

O proxy do servidor recebe o tráfego da internet e encaminha-o para `127.0.0.1:8085`
(ou para o `WEB_PORT` escolhido).

**Opção A — nginx + Certbot**, quando o servidor já usa nginx para outros sites:

```nginx
server {
    server_name chuanga-studio.com www.chuanga-studio.com;
    client_max_body_size 25m;                 # uploads de fotos

    location / {
        proxy_pass http://127.0.0.1:8085;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo certbot --nginx -d chuanga-studio.com -d www.chuanga-studio.com
```

**Opção B — Caddy**, que obtém e renova o certificado sozinho:

```
chuanga-studio.com, www.chuanga-studio.com {
    reverse_proxy 127.0.0.1:8085
}
```

Os cabeçalhos `X-Forwarded-*` são importantes. Com eles, a API conhece o IP real de quem faz o pedido,
e é por esse IP que aplica os limites de tentativas de login.

## 4. Atualizar para uma versão nova

```bash
cd /opt/chuanga-studio
docker compose pull && docker compose up -d
docker image prune -f        # apaga imagens antigas
```

**Voltar atrás**: pôr no `.env` uma versão anterior, por exemplo `TAG=sha-1a2b3c4` ou `TAG=1.0.0`, e voltar a
fazer `docker compose up -d`. Atenção: as migrações da base de dados (Flyway) não são desfeitas. Por isso
deve fazer-se um backup antes de cada atualização.

## 5. Backups

```bash
# backup (ficheiro comprimido com a data)
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > backup-$(date +%F).dump

# restaurar (com a API parada)
docker compose stop api
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' < backup-AAAA-MM-DD.dump
docker compose start api
```

Exemplo de backup diário às 02:30, a guardar os últimos 14 dias (`crontab -e`):

```
30 2 * * * cd /opt/chuanga-studio && docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > backups/$(date +\%F).dump && find backups -name '*.dump' -mtime +14 -delete
```

(criar primeiro a pasta `backups/`; o ideal é copiar também os backups para fora do servidor.)

## 6. Comandos úteis e problemas comuns

```bash
docker compose ps                 # estado e healthchecks
docker compose logs -f api        # logs da API
docker compose logs -f web        # pedidos ao site
docker compose restart api
```

| Sintoma | Causa provável |
|---|---|
| `api` não fica healthy e reinicia | Falta uma variável obrigatória no `.env` ou o `JWT_SECRET`/`ADMIN_SEED_PASSWORD` é o de exemplo. Ver `docker compose logs api`. |
| `denied` no `docker compose pull` | As imagens são privadas e falta o `docker login ghcr.io`. |
| 413 ao enviar fotos | Falta `client_max_body_size 25m` no proxy do servidor. |
| Site abre mas o login dá erro de rede | O proxy está a apontar para a porta errada; tem de ser `127.0.0.1:${WEB_PORT}`. |
| Os links nos emails e nas faturas apontam para o sítio errado | `SITE_URL` / `API_URL` no `.env`. |
