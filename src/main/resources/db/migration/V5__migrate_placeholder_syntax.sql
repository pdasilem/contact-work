update projects
set mail_subject = regexp_replace(mail_subject, '\{([^{}]+)\}', '{{\1}}', 'g')
where mail_subject is not null
  and mail_subject ~ '\{[^{}]+\}'
  and mail_subject not like '%{{%';

update projects
set mail_body = regexp_replace(mail_body, '\{([^{}]+)\}', '{{\1}}', 'g')
where mail_body is not null
  and mail_body ~ '\{[^{}]+\}'
  and mail_body not like '%{{%';
