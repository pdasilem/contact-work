create table app_users (
    id uuid primary key,
    name text not null,
    login text not null unique,
    password_hash text not null,
    email text,
    role varchar(16) not null,
    active boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table app_user_projects (
    user_id uuid not null,
    project_id uuid not null,
    primary key (user_id, project_id),
    constraint fk_app_user_projects_user
        foreign key (user_id) references app_users(id) on delete cascade,
    constraint fk_app_user_projects_project
        foreign key (project_id) references projects(id) on delete cascade
);

create index idx_app_user_projects_project
    on app_user_projects(project_id);
