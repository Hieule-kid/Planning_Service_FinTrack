# FinTrack :: Planning Service

Savings plan + milestone management, port 8090. Owns the `fintrack_planning` DB. Only *validates* JWTs (shared `FINTRACK_JWT_SECRET`); never issues tokens. Part of the FinTrack microservices system (formerly a module in the `Fintrack` monorepo, now split into its own repo).

## Layout

```
pom.xml               # aggregator (packaging=pom) — reactor-builds core + planning-service together
core/                  # git submodule -> https://github.com/Hieule-kid/Core_Service_FinTrack
planning-service/      # the actual application module
Dockerfile             # multi-stage build, reactor build with -am (also-make core)
render.yaml            # Render deploy config for this repo
docker-compose.yml     # local dev: Postgres + this service
```

## Working with the `core` submodule

This repo depends on [Core_Service_FinTrack](https://github.com/Hieule-kid/Core_Service_FinTrack) via a git submodule at `core/`. When cloning fresh:

```bash
git clone --recurse-submodules https://github.com/Hieule-kid/Planning_Service_FinTrack.git
# or, if already cloned without it:
git submodule update --init --recursive
```

To pick up a new `core` change:

```bash
cd core && git pull origin main && cd ..
git add core && git commit -m "chore: bump core submodule"
```

## Local dev

Requires `config-service` (Eureka) running and reachable — either from the `Fintrack` repo's own `docker-compose.yml`, or via `./mvnw spring-boot:run` in that repo.

```bash
cp .env.example .env   # fill in FINTRACK_JWT_SECRET (must match auth-service and be a real secret)
docker compose up -d
```

Or run directly with Maven (after `git submodule update --init`):

```bash
./mvnw clean install -DskipTests   # builds core + planning-service
./mvnw -pl planning-service spring-boot:run
```

## Tests

```bash
./mvnw -pl planning-service test
```

## Deployment

Render (`render.yaml`), Docker web service, `singapore` region, deployed straight from GitHub on commit to `main`. Requires `FINTRACK_JWT_SECRET` to be identical to the value set on `auth-service` and `Eureka` credentials to match `config-service`.
