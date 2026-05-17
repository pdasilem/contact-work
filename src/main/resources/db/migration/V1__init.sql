create table projects (
    id uuid primary key,
    name text not null,
    description text,
    status varchar(32) not null,
    letter_template text,
    mail_subject text,
    mail_body text,
    letter_attachment_filename text,
    mail_from text,
    mail_from_name text,
    send_delay_ms bigint not null,
    max_messages_per_batch integer,
    inbox_sync_cron text not null,
    gmail_username text,
    gmail_app_password text,
    ai_system_prompt text,
    last_mail_sync_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table app_ai_settings (
    id integer primary key,
    provider varchar(32) not null,
    model text not null,
    temperature double precision not null,
    updated_at timestamptz not null
);

create table contacts (
    id uuid primary key,
    project_id uuid not null,
    organization_name text not null,
    contact_name text not null,
    email text not null,
    note text,
    status varchar(32) not null,
    outbound_message_id text,
    sent_at timestamptz,
    reply_received_at timestamptz,
    bounce_received_at timestamptz,
    last_error_at timestamptz,
    last_error_message text,
    deleted_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_contacts_project
        foreign key (project_id) references projects(id) on delete cascade
);

create table contact_messages (
    id uuid primary key,
    project_id uuid not null,
    contact_id uuid not null,
    direction varchar(16) not null,
    event_type varchar(16) not null,
    message_id text,
    related_message_id text,
    sender_email text,
    recipient_email text,
    subject text,
    body_text text,
    message_timestamp timestamptz not null,
    created_at timestamptz not null,
    constraint fk_contact_messages_project
        foreign key (project_id) references projects(id) on delete cascade,
    constraint fk_contact_messages_contact
        foreign key (contact_id) references contacts(id) on delete cascade
);

create table mail_sync_state (
    project_id uuid primary key,
    last_processed_uid bigint not null,
    updated_at timestamptz not null,
    constraint fk_mail_sync_state_project
        foreign key (project_id) references projects(id) on delete cascade
);

create table mailbox_messages (
    id uuid primary key,
    project_id uuid not null,
    contact_id uuid not null,
    folder varchar(16) not null,
    direction varchar(16) not null,
    service_date timestamptz not null,
    normalized_message_id text,
    sender_email text,
    recipient_emails text,
    cc_emails text,
    subject text,
    body_text text,
    content_hash text not null,
    created_at timestamptz not null,
    constraint fk_mailbox_messages_project
        foreign key (project_id) references projects(id) on delete cascade,
    constraint fk_mailbox_messages_contact
        foreign key (contact_id) references contacts(id) on delete cascade
);

create table contact_conversation_summaries (
    id uuid primary key,
    contact_id uuid not null,
    summary_text text not null,
    provider varchar(32),
    model text not null,
    generated_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_contact_conversation_summaries_contact
        foreign key (contact_id) references contacts(id) on delete cascade
);

create table ai_chat_sessions (
    id uuid primary key,
    scope varchar(16) not null,
    project_id uuid,
    contact_id uuid,
    title text,
    provider varchar(32),
    model text not null,
    archived_at timestamptz,
    summary text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_ai_chat_sessions_project
        foreign key (project_id) references projects(id) on delete cascade,
    constraint fk_ai_chat_sessions_contact
        foreign key (contact_id) references contacts(id) on delete cascade
);

create table ai_chat_messages (
    id uuid primary key,
    session_id uuid not null,
    role varchar(16) not null,
    content text not null,
    provider varchar(32),
    model text,
    created_at timestamptz not null,
    constraint fk_ai_chat_messages_session
        foreign key (session_id) references ai_chat_sessions(id) on delete cascade
);

create table project_assets (
    id uuid primary key,
    project_id uuid not null,
    type varchar(32) not null,
    original_filename text not null,
    stored_path text not null,
    content_type text,
    size_bytes bigint not null,
    active boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_project_assets_project
        foreign key (project_id) references projects(id) on delete cascade
);

create table contact_custom_fields (
    id uuid primary key,
    project_id uuid not null,
    contact_id uuid not null,
    field_key text not null,
    field_value text,
    constraint fk_contact_custom_fields_project
        foreign key (project_id) references projects(id) on delete cascade,
    constraint fk_contact_custom_fields_contact
        foreign key (contact_id) references contacts(id) on delete cascade,
    constraint uq_contact_custom_fields_contact_key
        unique (contact_id, field_key)
);

create table project_contact_columns (
    id uuid primary key,
    project_id uuid not null,
    column_key text not null,
    display_label text not null,
    source_type varchar(16) not null,
    column_order integer not null,
    visible boolean not null,
    constraint fk_project_contact_columns_project
        foreign key (project_id) references projects(id) on delete cascade,
    constraint uq_project_contact_columns_project_key
        unique (project_id, column_key)
);

create unique index uq_contacts_project_email
    on contacts(project_id, email);

create unique index uq_contacts_project_outbound_message_id
    on contacts(project_id, outbound_message_id)
    where outbound_message_id is not null;

create index idx_contacts_project_status
    on contacts(project_id, status);

create index idx_contacts_project_deleted
    on contacts(project_id, deleted_at);

create unique index uq_contact_messages_message_id
    on contact_messages(message_id)
    where message_id is not null;

create index idx_contact_messages_project_contact_timestamp
    on contact_messages(project_id, contact_id, message_timestamp desc);

create unique index uq_mailbox_messages_project_message_id
    on mailbox_messages(project_id, normalized_message_id)
    where normalized_message_id is not null;

create unique index uq_mailbox_messages_project_content_hash
    on mailbox_messages(project_id, content_hash)
    where normalized_message_id is null;

create index idx_mailbox_messages_project_contact_service_date
    on mailbox_messages(project_id, contact_id, service_date asc);

create unique index uq_contact_conversation_summaries_contact
    on contact_conversation_summaries(contact_id);

create index idx_ai_chat_sessions_scope_project
    on ai_chat_sessions(scope, project_id, updated_at desc);

create index idx_ai_chat_sessions_scope_contact
    on ai_chat_sessions(scope, contact_id, updated_at desc);

create index idx_ai_chat_messages_session_created
    on ai_chat_messages(session_id, created_at asc);

create index idx_project_assets_project_type_active_created
    on project_assets(project_id, type, active, created_at);

create index idx_contact_custom_fields_project_contact
    on contact_custom_fields(project_id, contact_id);

create index idx_project_contact_columns_project_order
    on project_contact_columns(project_id, column_order);
