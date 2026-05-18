# ContactWork Deployment Guide

This document covers VPS deployment and server operations.

Use placeholders in committed files:

- App public URL: `https://app.<DOMAIN>`
- Gmail OAuth redirect URI: `https://app.<DOMAIN>/callback`
- ONLYOFFICE public URL: `https://docs.<DOMAIN>`

## Prerequisites

- Linux VPS with Docker Engine and Docker Compose Plugin installed
- Reverse proxy on the host, such as nginx or Caddy
- Firewall open only for TCP ports `80` and `443`
- Cloudflare DNS records:
  - `A app -> <VPS_PUBLIC_IP>`
  - `A docs -> <VPS_PUBLIC_IP>`
- Google Cloud Console OAuth redirect URI exactly set to `https://app.<DOMAIN>/callback`
- A Gmail account configured with:
  - `2-Step Verification` enabled
  - `App Password` created if app-password sending is used

Do not open `8083`, `8084`, `5436`, or `11434` to the internet. Docker Compose binds those ports to `127.0.0.1`; public access should go through HTTPS on the reverse proxy.

## Cloudflare

For initial certificate issuance and deployment checks, set both `app` and `docs` DNS records to DNS only.

After HTTPS works, Cloudflare proxy can be enabled if desired:

- SSL/TLS mode must be `Full (strict)`
- WebSockets must stay enabled
- Cloudflare upload and request limits apply to large DOCX files

## Deployment Flow

First deployment:

1. Download the project on the server into `/opt` with `git clone`.
2. Copy `.env.example` to `.env`.
3. Fill `.env` with private values and real public deployment URLs.
4. Configure the reverse proxy for the app and docs subdomains.
5. Start the full stack with Docker Compose.
6. Verify local container endpoints, public HTTPS endpoints, OAuth, and ONLYOFFICE editing.

Subsequent deployments:

1. Pull the latest code in the existing checkout.
2. Review `.env.example` for new variables and update `.env` if needed.
3. Configure the reverse proxy for the app and docs subdomains.
4. Rebuild and restart the Compose services.
5. Run the verification checks.

## Deploy With Git Clone

```bash
cd /opt
git clone https://github.com/pdasilem/contact-work.git
cd contact-work
```

Create `.env` from the committed example and fill it with real private values:

```bash
cp .env.example .env
editor .env
```

Then start the stack:

```bash
docker compose up -d --build
```

## Reverse Proxy

The reverse proxy runs on the VPS host and forwards to Docker's localhost-bound ports.

### nginx

```nginx
server {
    listen 80;
    server_name app.<DOMAIN>;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name app.<DOMAIN>;

    client_max_body_size 100m;

    location / {
        proxy_pass http://127.0.0.1:8083;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}

server {
    listen 80;
    server_name docs.<DOMAIN>;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name docs.<DOMAIN>;

    client_max_body_size 100m;

    location / {
        proxy_pass http://127.0.0.1:8084;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
```

### Caddy

```caddyfile
app.<DOMAIN> {
    request_body {
        max_size 100MB
    }
    reverse_proxy 127.0.0.1:8083 {
        transport http {
            read_timeout 3600s
            write_timeout 3600s
        }
    }
}

docs.<DOMAIN> {
    request_body {
        max_size 100MB
    }
    reverse_proxy 127.0.0.1:8084 {
        transport http {
            read_timeout 3600s
            write_timeout 3600s
        }
    }
}
```

## Verify Deployment

Before start, confirm Compose sees the required deployment values:

```bash
docker compose config
```

Check for:

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `GOOGLE_REDIRECT_URI=https://app.<DOMAIN>/callback`
- `ONLYOFFICE_PUBLIC_BASE_URL=https://docs.<DOMAIN>`
- `ONLYOFFICE_DOCUMENT_BASE_URL=http://app:8083`
- `ONLYOFFICE_INTERNAL_DOWNLOAD_BASE_URL=http://onlyoffice`

Start and verify containers:

```bash
docker compose up -d --build
docker compose ps
```

Verify local VPS endpoints:

```bash
curl http://127.0.0.1:8083/api/v1/health
curl http://127.0.0.1:8084/healthcheck
```

Verify public endpoints:

```bash
curl https://app.<DOMAIN>/api/v1/health
curl https://docs.<DOMAIN>/healthcheck
curl -I https://docs.<DOMAIN>/web-apps/apps/api/documents/api.js
```

Verify UI workflows:

- Open `https://app.<DOMAIN>/app`
- Run Gmail sender alias sync and confirm Google redirects back to `/callback`
- Upload a DOCX letter template
- Edit it in ONLYOFFICE, save and close, then reopen or preview
- Confirm app logs show ONLYOFFICE callback status `2` or `6` with no callback download failure

## Update Deployment

Do not run `git clone` again for an existing deployment. Use the existing checkout:

```bash
cd /opt/contact-work
git pull
docker compose up -d --build
docker compose ps
```

If `.env.example` changed, merge the new variables into `.env` before restarting:

```bash
git diff HEAD@{1} -- .env.example
editor .env
docker compose up -d --build
```

This rebuilds changed images and recreates containers whose image or configuration changed.

## Data Persistence

PostgreSQL data is stored in the named volume:

- `contactwork_postgres_data`

Ollama model data is stored in the named volume:

- `contactwork_ollama_data`

## Main Runtime Ports

All runtime ports are localhost-bound by Compose:

- `127.0.0.1:8083`: ContactWork API and Vaadin UI
- `127.0.0.1:8084`: ONLYOFFICE Document Server
- `127.0.0.1:5436`: PostgreSQL
- `127.0.0.1:11434`: Ollama API

The browser reaches ONLYOFFICE through `https://docs.<DOMAIN>`. The app and ONLYOFFICE containers exchange document URLs and callbacks over the Docker network through `http://app:8083` and `http://onlyoffice`.

## Troubleshooting

If ONLYOFFICE editing fails:

- check `docker compose ps`
- verify `curl http://127.0.0.1:8084/healthcheck`
- verify `ONLYOFFICE_PUBLIC_BASE_URL` is the public docs subdomain
- verify `ONLYOFFICE_DOCUMENT_BASE_URL` is reachable from the ONLYOFFICE container
- inspect app logs for callback status `2` or `6` and callback download errors

If mail sending fails:

- verify `GET /api/v1/projects/{projectId}/health/mail`
- confirm Gmail app password is valid if app-password sending is used
- confirm sender mailbox matches the app password owner

If Google OAuth sender alias sync fails:

- confirm `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`
- confirm `GOOGLE_REDIRECT_URI=https://app.<DOMAIN>/callback`
- confirm the Google Cloud Console authorized redirect URI exactly matches the same URL

If reply sync does not update statuses:

- run `POST /api/v1/projects/{projectId}/inbox/sync`
- inspect the contact or history endpoints through `https://app.<DOMAIN>`