-- Dois índices porque são duas consultas de formas diferentes: a listagem sem filtro ordena
-- direto por created_at, e a filtrada precisa de status como coluna líder para não varrer o
-- índice inteiro. O id no fim é o que torna a paginação determinística entre criadas no mesmo
-- instante.
create index idx_applications_created_at
    on applications (created_at desc, id desc);

create index idx_applications_status_created_at
    on applications (status, created_at desc, id desc);
