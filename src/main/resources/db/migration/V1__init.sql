create table contacts (
    id uuid primary key,
    organization_name text not null,
    country text,
    contact_name text not null,
    email text not null unique,
    preclinical_notes text,
    note text,
    status varchar(32) not null,
    outbound_message_id text unique,
    sent_at timestamptz,
    reply_received_at timestamptz,
    bounce_received_at timestamptz,
    last_error_at timestamptz,
    last_error_message text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table contact_messages (
    id uuid primary key,
    contact_id uuid not null references contacts(id) on delete cascade,
    direction varchar(16) not null,
    event_type varchar(16) not null,
    message_id text,
    related_message_id text,
    sender_email text,
    recipient_email text,
    subject text,
    body_text text,
    message_timestamp timestamptz not null,
    created_at timestamptz not null
);

create table mail_sync_state (
    id smallint primary key,
    last_processed_uid bigint not null,
    updated_at timestamptz not null
);

insert into mail_sync_state (id, last_processed_uid, updated_at)
values (1, 0, now())
on conflict (id) do nothing;

create index idx_contacts_status on contacts(status);
create index idx_contacts_sent_at on contacts(sent_at);
create unique index uq_contact_messages_message_id
    on contact_messages(message_id)
    where message_id is not null;
create index idx_contact_messages_contact_id_message_timestamp
    on contact_messages(contact_id, message_timestamp desc);
