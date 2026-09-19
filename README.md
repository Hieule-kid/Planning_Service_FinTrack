# FinTrack :: Planning Service

Savings plan + milestone management, port 8090. Owns the `fintrack_planning` DB. Only *validates* JWTs (shared `FINTRACK_JWT_SECRET`); never issues tokens. Part of the FinTrack microservices system (formerly a module in the `Fintrack` monorepo, now split into its own repo).

## Layout

```
pom.xml               # aggregator (packaging=pom) — builds planning-service
planning-service/      # the actual application module
settings.xml           # Maven server credentials (from env vars) for GitHub Packages
Dockerfile             # multi-stage build
render.yaml            # Render deploy config for this repo
docker-compose.yml     # local dev: Postgres + this service
```

## The `core` dependency

This service depends on `com.fintrack:core` ([Core_Service_FinTrack](https://github.com/Hieule-kid/Core_Service_FinTrack)'s shared library — base entities, DTOs, exceptions, utilities), resolved as an ordinary Maven dependency from **GitHub Packages**. There's no git submodule and no vendored copy — this repo is self-contained and builds independently of the other service repos.

GitHub Packages requires authentication to resolve dependencies even from a public repo, so a GitHub Personal Access Token is needed at build time:

1. Create a classic PAT with the `read:packages` scope (GitHub → Settings → Developer settings → Personal access tokens).
2. Export it in your shell:
   ```bash
   export GITHUB_ACTOR=<your-github-username>
   export GITHUB_TOKEN=<your-pat>
   ```
3. Or put the same two values in `.env` — `docker compose build` picks them up as build args automatically.

`settings.xml` (committed, no secrets in it) wires `${env.GITHUB_ACTOR}`/`${env.GITHUB_TOKEN}` into Maven's `github` server credentials; every `mvnw` invocation below needs `-s settings.xml` to use it.

## Local dev

Requires `config-service` (Eureka) running and reachable — either from the `Fintrack` repo's own `docker-compose.yml`, or via `./mvnw spring-boot:run` in that repo.

```bash
cp .env.example .env   # fill in GITHUB_ACTOR/GITHUB_TOKEN and FINTRACK_JWT_SECRET (must match auth-service and be a real secret)
docker compose up -d
```

Or run directly with Maven (after exporting `GITHUB_ACTOR`/`GITHUB_TOKEN`):

```bash
./mvnw -s settings.xml clean install -DskipTests
./mvnw -pl planning-service spring-boot:run
```

## Tests

```bash
./mvnw -s settings.xml -pl planning-service test
```

## Deployment

Render (`render.yaml`), Docker web service, `singapore` region, deployed straight from GitHub on commit to `main`. Requires `FINTRACK_JWT_SECRET` to be identical to the value set on `auth-service` and `Eureka` credentials to match `config-service`.
