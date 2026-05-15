create table projects (
    id uuid primary key,
    name text not null,
    description text,
    status varchar(32) not null,
    letter_template text not null,
    pitch_deck text not null,
    mail_subject text not null,
    mail_body text not null,
    letter_attachment_filename text not null,
    pitch_deck_attachment_filename text not null,
    mail_from text,
    send_delay_ms bigint not null,
    inbox_sync_cron text not null,
    gmail_username text,
    gmail_app_password text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

insert into projects (
    id,
    name,
    description,
    status,
    letter_template,
    pitch_deck,
    mail_subject,
    mail_body,
    letter_attachment_filename,
    pitch_deck_attachment_filename,
    mail_from,
    send_delay_ms,
    inbox_sync_cron,
    gmail_username,
    gmail_app_password,
    created_at,
    updated_at
)
values (
    '00000000-0000-0000-0000-000000000001',
    'Default Project',
    'Migrated default project for pre-project data.',
    'ACTIVE',
    'classpath:data/Letter.docx',
    'classpath:data/Pitch_deck_en.pdf',
    'Partnership Opportunity: Preclinical Validation of Patented Nano-cartilage Tech',
    'Please find attached a consortium proposal for EIC Pathfinder 2026 regarding preclinical validation of our patented Nano-cartilage technology. We are seeking a lead research partner for TRL 4/5 and have included a detailed letter and scientific pitch deck for your review.',
    'Consortium_Proposal.pdf',
    'Pitch_deck_en.pdf',
    null,
    3000,
    '0 */30 * * * *',
    null,
    null,
    now(),
    now()
);

alter table contacts add column project_id uuid;
update contacts set project_id = '00000000-0000-0000-0000-000000000001';
alter table contacts alter column project_id set not null;
alter table contacts
    add constraint fk_contacts_project
    foreign key (project_id) references projects(id) on delete cascade;
alter table contacts drop constraint if exists contacts_email_key;
create unique index uq_contacts_project_email on contacts(project_id, email);
create index idx_contacts_project_status on contacts(project_id, status);

alter table contact_messages add column project_id uuid;
update contact_messages message
set project_id = contact.project_id
from contacts contact
where message.contact_id = contact.id;
alter table contact_messages alter column project_id set not null;
alter table contact_messages
    add constraint fk_contact_messages_project
    foreign key (project_id) references projects(id) on delete cascade;
create index idx_contact_messages_project_contact_timestamp
    on contact_messages(project_id, contact_id, message_timestamp desc);

alter table mail_sync_state add column project_id uuid;
update mail_sync_state set project_id = '00000000-0000-0000-0000-000000000001';
alter table mail_sync_state alter column project_id set not null;
alter table mail_sync_state drop constraint if exists mail_sync_state_pkey;
alter table mail_sync_state drop column id;
alter table mail_sync_state
    add constraint pk_mail_sync_state primary key (project_id);
alter table mail_sync_state
    add constraint fk_mail_sync_state_project
    foreign key (project_id) references projects(id) on delete cascade;
