# ContactWork

ContactWork is a Spring Boot service for controlled, project-scoped email outreach. It imports contacts from CSV into PostgreSQL, generates a personalized PDF letter from a DOCX template, sends email with two PDF attachments through Gmail, and tracks replies or bounces back into the database.

## Main Features

- Idempotent CSV import into PostgreSQL
- Contact lookup by UUID or email
- Personalized PDF letter generation through ONLYOFFICE
- Outbound email sending through Gmail SMTP
- Reply and bounce synchronization through Gmail IMAP
- Message history storage for outbound and inbound emails
- Manual note field on each contact
- Project-scoped REST API for Postman or Bruno workflows
- Vaadin project management UI at `/app`

## Stack

- Java 25
- Spring Boot 3.5.13
- Spring Data JPA
- Vaadin Flow
- PostgreSQL 17
- Flyway
- Docker Compose
- ONLYOFFICE Document Server

## Runtime Model

The service is designed to run through `docker compose`.

Containers:

- `app`: ContactWork API and Vaadin UI
- `postgres`: PostgreSQL database
- `onlyoffice`: ONLYOFFICE Document Server for PDF conversion and DOCX editing
- `ollama`: local AI model runtime
- `ollama-pull`: one-shot model download before `app` starts

Local host bindings:

- `8083`: ContactWork API and Vaadin UI
- `5436`: PostgreSQL
- `11434`: Ollama API
- `8084`: ONLYOFFICE Document Server

All Compose host ports are bound to `127.0.0.1`. On a VPS, public traffic should reach the app through a reverse proxy:

- `https://app.<DOMAIN>` -> `http://127.0.0.1:8083`
- `https://docs.<DOMAIN>` -> `http://127.0.0.1:8084`

The browser-facing ONLYOFFICE editor loads from the public docs subdomain, while server-to-server document and callback traffic stays on the Docker network.

## Configuration

Non-secret application settings are stored in:

- `src/main/resources/application.yml`

Private values must be stored in:

- `.env`

Required private variables:

- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `GMAIL_USERNAME`
- `GMAIL_APP_PASSWORD`
- `APP_MAIL_FROM`

Optional AI variables:

- `LOCAL_AI_MODEL`, default `gemma4:e2b`
- `GOOGLE_API_KEY`, required only when using the Google GenAI provider profile
- `GOOGLE_GENAI_MODEL`, default `gemini-2.0-flash`
- `GOOGLE_GENAI_TEMPERATURE`, default `0.2`

Google OAuth variables for Gmail sender alias sync:

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `GOOGLE_REDIRECT_URI`, for VPS use `https://app.<DOMAIN>/callback`

ONLYOFFICE variables:

- `ONLYOFFICE_PUBLIC_BASE_URL`, for VPS use `https://docs.<DOMAIN>`
- `ONLYOFFICE_DOCUMENT_BASE_URL`, for Compose use `http://app:8083`
- `ONLYOFFICE_INTERNAL_DOWNLOAD_BASE_URL`, for Compose use `http://onlyoffice`

On first `docker compose up`, `ollama-pull` downloads the configured local model into the `contactwork_ollama_data` volume before `app` starts.

Initial project defaults still come from `application.yml`, but runtime outreach settings are stored per project:

- `app.mail.subject`
- `app.mail.body`
- `app.mail.letter-attachment-filename`
- `app.mail.pitch-deck-attachment-filename`
- `app.mail.inbox-sync-cron`

Project-specific settings include:

- Gmail username and app password
- sender address
- subject and body
- letter template
- pitch deck attachment
- attachment filenames
- send delay
- inbox sync behavior

## API Base URL

```text
http://localhost:8083
```

For VPS use:

```text
https://app.<DOMAIN>
```

## Web Interface

```text
http://localhost:8083/app
```

The Vaadin interface manages projects and project mailbox settings. Operational contact, send, inbox, and history APIs require an explicit `{projectId}` path segment.

## Contact Statuses

- `NEW`: imported and not sent yet
- `IN_PROGRESS`: currently being sent
- `SENT`: accepted by SMTP
- `SEND_FAILED`: sending failed
- `REPLIED`: reply detected
- `BOUNCED`: delivery failure detected

## Typical Workflow

### 1. Check service health

```bash
curl http://localhost:8083/api/v1/health
curl http://localhost:8083/api/v1/projects/{projectId}/health/mail
```

### 2. Import contacts from CSV

```bash
curl -F 'file=@contacts.csv;type=text/csv' http://localhost:8083/api/v1/projects/{projectId}/contacts/import
```

Import behavior:

- existing emails are skipped
- only missing contacts are inserted

### 3. List contacts

```bash
curl 'http://localhost:8083/api/v1/projects/{projectId}/contacts'
curl 'http://localhost:8083/api/v1/projects/{projectId}/contacts?status=NEW'
curl 'http://localhost:8083/api/v1/projects/{projectId}/contacts?email=user@example.com'
curl 'http://localhost:8083/api/v1/projects/{projectId}/contacts?organization=research'
```

Supported filters:

- `status`
- `email`
- `organization`

For human-readable terminal output, use table format:

```bash
curl 'http://localhost:8083/api/v1/projects/{projectId}/contacts?format=table'
curl 'http://localhost:8083/api/v1/projects/{projectId}/contacts?status=NEW&format=table'
curl 'http://localhost:8083/api/v1/projects/{projectId}/contacts?organization=research&format=table'
```

### 4. Read one contact by UUID or email

```bash
curl http://localhost:8083/api/v1/projects/{projectId}/contacts/{contactId-or-email}
```

### 5. Generate a PDF preview

```bash
curl http://localhost:8083/api/v1/projects/{projectId}/letters/{contactId-or-email}/pdf --output preview.pdf
```

### 6. Send one message

```bash
curl -X POST http://localhost:8083/api/v1/projects/{projectId}/send/contact/{contactId-or-email}
```

### 7. Start batch sending

```bash
curl -X POST http://localhost:8083/api/v1/projects/{projectId}/send/start
```

Batch behavior:

- only contacts in `NEW` are sent
- `SENT`, `REPLIED`, and `BOUNCED` are not resent

### 8. Check batch status

```bash
curl http://localhost:8083/api/v1/projects/{projectId}/send/status
```

Returned operational fields include:

- whether a batch is running
- the explicit batch selection rule
- eligible contact count
- aggregated counts by contact status

### 9. Sync replies and bounces

```bash
curl -X POST http://localhost:8083/api/v1/projects/{projectId}/inbox/sync
```

### 10. Update a manual contact note

```bash
curl -X PATCH \
  -H 'Content-Type: application/json' \
  -d '{"note":"Needs manual follow-up next week"}' \
  http://localhost:8083/api/v1/projects/{projectId}/contacts/{contactId-or-email}/note
```

### 11. Read message history

```bash
curl http://localhost:8083/api/v1/projects/{projectId}/history/{contactId-or-email}
```

## Reply Tracking Rules

Reply matching is strict.

- the service links replies using email thread headers
- a completely new unrelated inbound message is not treated as a reply automatically
- inbox synchronization is safe to run repeatedly because processed IMAP position is tracked in the database

## Outbound Attachments

Each outbound email includes two PDF attachments:

- the generated personalized letter
- the pitch deck

## Templates and Resources

Project resources:

- `src/main/resources/data/Letter.docx`
- `src/main/resources/data/Pitch_deck_en.pdf`

The DOCX template must contain the placeholder:

- `{{contact_name}}`

## Deployment

Deployment instructions are in:

- [DEPLOYMENT.md](DEPLOYMENT.md)