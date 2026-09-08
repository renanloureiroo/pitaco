-- O composto atende recorte e ordenação da listagem sem sort em memória, e cobre tudo que o
-- índice de application_id sozinho cobria — é a coluna líder do novo.
create index idx_api_keys_application_id_created_at
    on api_keys (application_id, created_at desc, id desc);

drop index idx_api_keys_application_id;
