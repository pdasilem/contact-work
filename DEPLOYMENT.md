# ContactWork Deployment Guide

This document covers deployment and server operations only.

## Prerequisites

- Linux VPS with Docker Engine and Docker Compose Plugin installed
- Open TCP port `8083` on the server firewall
- Open TCP port `5436` only if you want PostgreSQL reachable from outside Docker
- A Gmail account configured with:
  - `2-Step Verification` enabled
  - `App Password` created

## Deployment Flow

The deployment flow is intentionally simple:

1. Download the project on the server into `/opt` with `git clone`.
2. Create the `.env` file with the private values.
3. Start the full stack with Docker Compose.

## Deploy With Git Clone

```bash
cd /opt
git clone https://github.com/pdasilem/contact-work.git
cd contact-work
```

Create `.env` manually and fill it with real private values:

- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `GMAIL_USERNAME`
- `GMAIL_APP_PASSWORD`
- `APP_MAIL_FROM`

Then start the stack:

```bash
docker compose up -d --build
```

## Verify Deployment

Verify containers:

```bash
docker compose ps
```

Verify API:

```bash
curl http://127.0.0.1:8083/api/v1/health
curl http://127.0.0.1:8083/api/v1/health/mail
```

## Update Deployment

To update an existing deployment from the Git repository:

```bash
cd /opt/contact-work
git pull
docker compose up -d --build
```

This recreates the application container when the image or configuration changed.

## Data Persistence

PostgreSQL data is stored in the named volume:

- `contactwork_postgres_data`

## Main Runtime Ports

- `8083`: ContactWork API
- `5436`: PostgreSQL published from the container

ONLYOFFICE is internal-only and is not published to the host.

## Troubleshooting

If PDF generation fails:

- check `docker compose ps`
- verify `onlyoffice` is healthy

If mail sending fails:

- verify `GET /api/v1/health/mail`
- confirm Gmail app password is valid
- confirm sender mailbox matches the app password owner

If reply sync does not update statuses:

- run `POST /api/v1/inbox/sync`
- inspect the contact or history endpoints on port `8083`
