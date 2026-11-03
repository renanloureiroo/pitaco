# pg_dump e pg_restore da mesma versão do Postgres de produção, mais o rclone para o object
# storage. A imagem roda backup.sh em laço; restore.sh vai junto para o restore e o ensaio.
FROM postgres:16-alpine

RUN apk add --no-cache rclone \
    && mkdir -p /etc/rclone \
    && touch /etc/rclone/rclone.conf

# O remoto vem todo das variáveis RCLONE_CONFIG_*; o arquivo vazio só evita o aviso de
# configuração ausente a cada comando.
ENV RCLONE_CONFIG=/etc/rclone/rclone.conf

COPY backup.sh restore.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/restore.sh

ENTRYPOINT []
CMD ["backup.sh", "--loop"]
