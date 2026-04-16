# ContactWork

ContactWork is a backend-only Spring Boot service for controlled email outreach. It imports contacts from CSV into PostgreSQL, generates a personalized PDF letter from a DOCX template, sends email with two PDF attachments through Gmail, and tracks replies or bounces back into the database.

## Main Features

- Idempotent CSV import into PostgreSQL
- Contact lookup by UUID or email
- Personalized PDF letter generation through ONLYOFFICE
- Outbound email sending through Gmail SMTP
- Reply and bounce synchronization through Gmail IMAP
- Message history storage for outbound and inbound emails
- Manual note field on each contact
- Backend API for Postman or Bruno workflows

## Stack

- Java 25
- Spring Boot 3.5.13
- Spring Data JPA
- PostgreSQL 17
- Flyway
- Docker Compose
- ONLYOFFICE Document Server

## Runtime Model

The service is designed to run through `docker compose`.

Containers:

- `app`: ContactWork API
- `postgres`: PostgreSQL database
- `onlyoffice`: internal PDF conversion service

Published host ports:

- `8083`: ContactWork API
- `5436`: PostgreSQL

`ONLYOFFICE` is internal-only and is not exposed on the host.

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

Mail settings configured in `application.yml`:

- `app.mail.subject`
- `app.mail.body`
- `app.mail.letter-attachment-filename`
- `app.mail.pitch-deck-attachment-filename`
- `app.mail.inbox-sync-cron`

## API Base URL

```text
http://localhost:8083
```

For a remote deployment, replace `localhost` with the server host or IP address.

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
curl http://localhost:8083/api/v1/health/mail
```

### 2. Import contacts from CSV

```bash
curl -F 'file=@contacts.csv;type=text/csv' http://localhost:8083/api/v1/contacts/import
```

Import behavior:

- existing emails are skipped
- only missing contacts are inserted

### 3. List contacts

```bash
curl 'http://localhost:8083/api/v1/contacts'
curl 'http://localhost:8083/api/v1/contacts?status=NEW'
curl 'http://localhost:8083/api/v1/contacts?email=user@example.com'
curl 'http://localhost:8083/api/v1/contacts?organization=research'
```

Supported filters:

- `status`
- `email`
- `organization`

For human-readable terminal output, use table format:

```bash
curl 'http://localhost:8083/api/v1/contacts?format=table'
curl 'http://localhost:8083/api/v1/contacts?status=NEW&format=table'
curl 'http://localhost:8083/api/v1/contacts?organization=research&format=table'
```

### 4. Read one contact by UUID or email

```bash
curl http://localhost:8083/api/v1/contacts/{contactId-or-email}
```

### 5. Generate a PDF preview

```bash
curl http://localhost:8083/api/v1/letters/{contactId-or-email}/pdf --output preview.pdf
```

### 6. Send one message

```bash
curl -X POST http://localhost:8083/api/v1/send/contact/{contactId-or-email}
```

### 7. Start batch sending

```bash
curl -X POST http://localhost:8083/api/v1/send/start
```

Batch behavior:

- only contacts in `NEW` are sent
- `SENT`, `REPLIED`, and `BOUNCED` are not resent

### 8. Check batch status

```bash
curl http://localhost:8083/api/v1/send/status
```

Returned operational fields include:

- whether a batch is running
- the explicit batch selection rule
- eligible contact count
- aggregated counts by contact status

### 9. Sync replies and bounces

```bash
curl -X POST http://localhost:8083/api/v1/inbox/sync
```

### 10. Update a manual contact note

```bash
curl -X PATCH \
  -H 'Content-Type: application/json' \
  -d '{"note":"Needs manual follow-up next week"}' \
  http://localhost:8083/api/v1/contacts/{contactId-or-email}/note
```

### 11. Read message history

```bash
curl http://localhost:8083/api/v1/history/{contactId-or-email}
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
