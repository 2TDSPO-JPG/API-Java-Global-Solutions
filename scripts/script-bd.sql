/* =====================================================================================
   Saneia · DDL do banco em nuvem (Azure SQL Database / SQL Server)
   Global Solution FIAP 2026/2 · CitizenTech

   11 tabelas (9 do domínio + 2 de alertas por e-mail):
     sn_users ─┬─< sn_reports >── sn_sensors ─┬─< sn_sensor_readings
               │       │  └── sn_files         └─< sn_sensor_events
               │       ├─< sn_report_updates
               │       └─< sn_report_history (auditoria: status, edição, prioridade, notas internas)
               └─< sn_notifications (por usuário ou por papel)
     sn_alert_recipients (quem recebe o e-mail de risco máximo) · sn_email_logs (registro de cada envio)

   A API usa Hibernate com ddl-auto=update: se este script já tiver sido executado,
   as tabelas são reaproveitadas; se não, a API cria as mesmas estruturas na primeira subida.
   Execução: Query editor do portal Azure, Azure Data Studio ou
     sqlcmd -S <servidor>.database.windows.net -d saneiadb -U <usuario> -P <senha> -i scripts/script-bd.sql
   ===================================================================================== */

IF OBJECT_ID('dbo.sn_email_logs', 'U') IS NOT NULL DROP TABLE dbo.sn_email_logs;
IF OBJECT_ID('dbo.sn_alert_recipients', 'U') IS NOT NULL DROP TABLE dbo.sn_alert_recipients;
IF OBJECT_ID('dbo.sn_notifications', 'U') IS NOT NULL DROP TABLE dbo.sn_notifications;
IF OBJECT_ID('dbo.sn_report_history', 'U') IS NOT NULL DROP TABLE dbo.sn_report_history;
IF OBJECT_ID('dbo.sn_report_updates', 'U') IS NOT NULL DROP TABLE dbo.sn_report_updates;
IF OBJECT_ID('dbo.sn_reports', 'U') IS NOT NULL DROP TABLE dbo.sn_reports;
IF OBJECT_ID('dbo.sn_files', 'U') IS NOT NULL DROP TABLE dbo.sn_files;
IF OBJECT_ID('dbo.sn_sensor_events', 'U') IS NOT NULL DROP TABLE dbo.sn_sensor_events;
IF OBJECT_ID('dbo.sn_sensor_readings', 'U') IS NOT NULL DROP TABLE dbo.sn_sensor_readings;
IF OBJECT_ID('dbo.sn_sensors', 'U') IS NOT NULL DROP TABLE dbo.sn_sensors;
IF OBJECT_ID('dbo.sn_users', 'U') IS NOT NULL DROP TABLE dbo.sn_users;
GO

/* Usuários e perfis: resident (morador), operator (operadora) e admin */
CREATE TABLE dbo.sn_users (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    full_name       NVARCHAR(120)        NOT NULL,
    email           NVARCHAR(160)        NOT NULL,
    password_hash   NVARCHAR(100)        NOT NULL,
    user_role       NVARCHAR(20)         NOT NULL,
    created_at      DATETIMEOFFSET(6)    NOT NULL,
    CONSTRAINT pk_sn_users PRIMARY KEY (id),
    CONSTRAINT uk_sn_users_email UNIQUE (email),
    CONSTRAINT ck_sn_users_role CHECK (user_role IN ('RESIDENT', 'OPERATOR', 'ADMIN'))
);

/* Catálogo dos equipamentos + estado atual consolidado */
CREATE TABLE dbo.sn_sensors (
    id                     BIGINT IDENTITY(1,1) NOT NULL,
    prefix                 NVARCHAR(30)         NOT NULL,
    sensor_name            NVARCHAR(100)        NOT NULL,
    equipment_type         NVARCHAR(100)        NOT NULL,
    address                NVARCHAR(200)        NOT NULL,
    neighborhood           NVARCHAR(80)         NOT NULL,
    latitude               FLOAT(53)            NOT NULL,
    longitude              FLOAT(53)            NOT NULL,
    capacity_liters        FLOAT(53)            NOT NULL,
    limit_liters           FLOAT(53)            NOT NULL,
    radius_meters          FLOAT(53)            NOT NULL,
    expected_interval_min  INT                  NOT NULL,
    current_status         NVARCHAR(20)         NOT NULL,
    current_volume         FLOAT(53)            NULL,
    current_level_cm       FLOAT(53)            NULL,
    last_reading_at        DATETIMEOFFSET(6)    NULL,
    status_reason          NVARCHAR(255)        NULL,
    normal_streak          INT                  NOT NULL,
    active                 BIT                  NOT NULL,
    created_at             DATETIMEOFFSET(6)    NOT NULL,
    updated_at             DATETIMEOFFSET(6)    NOT NULL,
    deleted_at             DATETIMEOFFSET(6)    NULL,
    CONSTRAINT pk_sn_sensors PRIMARY KEY (id),
    CONSTRAINT ck_sn_sensors_status CHECK (current_status IN ('NORMAL', 'WARNING', 'ALARM', 'OFFLINE')),
    CONSTRAINT ck_sn_sensors_limit CHECK (limit_liters > 0 AND limit_liters <= capacity_liters),
    CONSTRAINT ck_sn_sensors_radius CHECK (radius_meters > 0),
    CONSTRAINT ck_sn_sensors_coord CHECK (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
);
CREATE INDEX ix_sn_sensors_prefix ON dbo.sn_sensors (prefix);

/* Histórico de medições: uma linha por leitura válida (separado do cadastro) */
CREATE TABLE dbo.sn_sensor_readings (
    id               BIGINT IDENTITY(1,1) NOT NULL,
    sensor_id        BIGINT               NOT NULL,
    measured_at      DATETIMEOFFSET(6)    NOT NULL,
    received_at      DATETIMEOFFSET(6)    NOT NULL,
    volume_liters    FLOAT(53)            NOT NULL,
    level_cm         FLOAT(53)            NULL,
    capacity_liters  FLOAT(53)            NOT NULL,
    limit_liters     FLOAT(53)            NOT NULL,
    reading_status   NVARCHAR(20)         NOT NULL,
    reading_source   NVARCHAR(60)         NULL,
    note             NVARCHAR(255)        NULL,
    CONSTRAINT pk_sn_sensor_readings PRIMARY KEY (id),
    CONSTRAINT fk_sn_readings_sensor FOREIGN KEY (sensor_id) REFERENCES dbo.sn_sensors (id),
    CONSTRAINT ck_sn_readings_status CHECK (reading_status IN ('NORMAL', 'WARNING', 'ALARM', 'OFFLINE')),
    CONSTRAINT ck_sn_readings_volume CHECK (volume_liters >= 0)
);
CREATE INDEX ix_sn_readings_sensor_time ON dbo.sn_sensor_readings (sensor_id, measured_at);

/* Eventos: alarme/alerta de nível e perda de comunicação, com início e fim */
CREATE TABLE dbo.sn_sensor_events (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    sensor_id      BIGINT               NOT NULL,
    event_kind     NVARCHAR(20)         NOT NULL,
    severity       NVARCHAR(20)         NOT NULL,
    started_at     DATETIMEOFFSET(6)    NOT NULL,
    ended_at       DATETIMEOFFSET(6)    NULL,
    event_message  NVARCHAR(255)        NOT NULL,
    peak_volume    FLOAT(53)            NULL,
    CONSTRAINT pk_sn_sensor_events PRIMARY KEY (id),
    CONSTRAINT fk_sn_events_sensor FOREIGN KEY (sensor_id) REFERENCES dbo.sn_sensors (id),
    CONSTRAINT ck_sn_events_kind CHECK (event_kind IN ('LEVEL', 'COMMUNICATION')),
    CONSTRAINT ck_sn_events_severity CHECK (severity IN ('NORMAL', 'WARNING', 'ALARM', 'OFFLINE'))
);
CREATE INDEX ix_sn_events_sensor_start ON dbo.sn_sensor_events (sensor_id, started_at);

/* Fotos dos relatos e complementos (entregues por URL assinada) */
CREATE TABLE dbo.sn_files (
    id            BIGINT IDENTITY(1,1) NOT NULL,
    content_type  NVARCHAR(100)        NOT NULL,
    file_name     NVARCHAR(160)        NOT NULL,
    size_bytes    BIGINT               NOT NULL,
    content       VARBINARY(MAX)       NOT NULL,
    created_at    DATETIMEOFFSET(6)    NOT NULL,
    CONSTRAINT pk_sn_files PRIMARY KEY (id)
);

/* Relatos dos moradores (protocolos) */
CREATE TABLE dbo.sn_reports (
    id                       BIGINT IDENTITY(1,1) NOT NULL,
    protocol                 NVARCHAR(20)         NOT NULL,
    owner_id                 BIGINT               NOT NULL,
    category                 NVARCHAR(40)         NOT NULL,
    description              NVARCHAR(1000)       NOT NULL,
    address                  NVARCHAR(200)        NOT NULL,
    neighborhood             NVARCHAR(80)         NOT NULL,
    latitude                 FLOAT(53)            NOT NULL,
    longitude                FLOAT(53)            NOT NULL,
    report_status            NVARCHAR(20)         NOT NULL,
    sensor_id                BIGINT               NULL,
    photo_file_id            BIGINT               NULL,
    previous_report_id       BIGINT               NULL,
    resolution               NVARCHAR(600)        NULL,
    progress_note            NVARCHAR(600)        NULL,
    assessment_note          NVARCHAR(600)        NULL,
    assessment_requested_at  DATETIMEOFFSET(6)    NULL,
    created_at               DATETIMEOFFSET(6)    NOT NULL,
    updated_at               DATETIMEOFFSET(6)    NOT NULL,
    closed_at                DATETIMEOFFSET(6)    NULL,
    deleted_at               DATETIMEOFFSET(6)    NULL,
    priority_level           NVARCHAR(20)         NULL,
    due_at                   DATETIMEOFFSET(6)    NULL,
    assignee_id              BIGINT               NULL,
    closing_outcome          NVARCHAR(20)         NULL,
    reopen_count             INT                  NULL,
    sensor_locked            BIT                  NULL,
    CONSTRAINT pk_sn_reports PRIMARY KEY (id),
    CONSTRAINT fk_sn_reports_assignee FOREIGN KEY (assignee_id) REFERENCES dbo.sn_users (id),
    CONSTRAINT ck_sn_reports_priority CHECK (priority_level IS NULL OR priority_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_sn_reports_outcome CHECK (closing_outcome IS NULL OR closing_outcome IN ('RESOLVED', 'STABILIZED', 'UNFOUNDED', 'FORWARDED')),
    CONSTRAINT uk_sn_reports_protocol UNIQUE (protocol),
    CONSTRAINT fk_sn_reports_owner FOREIGN KEY (owner_id) REFERENCES dbo.sn_users (id),
    CONSTRAINT fk_sn_reports_sensor FOREIGN KEY (sensor_id) REFERENCES dbo.sn_sensors (id),
    CONSTRAINT fk_sn_reports_photo FOREIGN KEY (photo_file_id) REFERENCES dbo.sn_files (id),
    CONSTRAINT fk_sn_reports_previous FOREIGN KEY (previous_report_id) REFERENCES dbo.sn_reports (id),
    CONSTRAINT ck_sn_reports_status CHECK (report_status IN ('RECEIVED', 'IN_PROGRESS', 'CLOSED'))
);
CREATE INDEX ix_sn_reports_owner ON dbo.sn_reports (owner_id, created_at);
CREATE INDEX ix_sn_reports_status ON dbo.sn_reports (report_status);

/* Complementos aditivos do morador */
CREATE TABLE dbo.sn_report_updates (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    report_id      BIGINT               NOT NULL,
    author_id      BIGINT               NOT NULL,
    note           NVARCHAR(600)        NULL,
    photo_file_id  BIGINT               NULL,
    created_at     DATETIMEOFFSET(6)    NOT NULL,
    CONSTRAINT pk_sn_report_updates PRIMARY KEY (id),
    CONSTRAINT fk_sn_updates_report FOREIGN KEY (report_id) REFERENCES dbo.sn_reports (id),
    CONSTRAINT fk_sn_updates_author FOREIGN KEY (author_id) REFERENCES dbo.sn_users (id),
    CONSTRAINT fk_sn_updates_photo FOREIGN KEY (photo_file_id) REFERENCES dbo.sn_files (id)
);

/* Trilha de auditoria dos chamados (inclui notas internas da equipe) */
CREATE TABLE dbo.sn_report_history (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    report_id      BIGINT               NOT NULL,
    entry_type     NVARCHAR(30)         NOT NULL,
    actor_name     NVARCHAR(120)        NOT NULL,
    from_value     NVARCHAR(80)         NULL,
    to_value       NVARCHAR(80)         NULL,
    message        NVARCHAR(1000)       NULL,
    internal_only  BIT                  NOT NULL,
    created_at     DATETIMEOFFSET(6)    NOT NULL,
    CONSTRAINT pk_sn_report_history PRIMARY KEY (id),
    CONSTRAINT fk_sn_history_report FOREIGN KEY (report_id) REFERENCES dbo.sn_reports (id)
);
CREATE INDEX ix_sn_history_report ON dbo.sn_report_history (report_id, created_at);

/* Notificações geradas pelo consumidor da fila saneia.notifications */
CREATE TABLE dbo.sn_notifications (
    id                 BIGINT IDENTITY(1,1) NOT NULL,
    recipient_user_id  BIGINT               NULL,
    recipient_role     NVARCHAR(20)         NULL,
    notification_type  NVARCHAR(40)         NOT NULL,
    title              NVARCHAR(120)        NOT NULL,
    body               NVARCHAR(600)        NOT NULL,
    reference_id       NVARCHAR(40)         NULL,
    created_at         DATETIMEOFFSET(6)    NOT NULL,
    read_at            DATETIMEOFFSET(6)    NULL,
    CONSTRAINT pk_sn_notifications PRIMARY KEY (id),
    CONSTRAINT ck_sn_notif_role CHECK (recipient_role IS NULL OR recipient_role IN ('RESIDENT', 'OPERATOR', 'ADMIN'))
);
CREATE INDEX ix_sn_notif_user ON dbo.sn_notifications (recipient_user_id, created_at);
CREATE INDEX ix_sn_notif_role ON dbo.sn_notifications (recipient_role, created_at);

/* Destinatários do e-mail de risco máximo (cadastrados pelo admin no painel) */
CREATE TABLE dbo.sn_alert_recipients (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    full_name   NVARCHAR(120)        NOT NULL,
    email       NVARCHAR(160)        NOT NULL,
    active      BIT                  NOT NULL,
    created_at  DATETIMEOFFSET(6)    NOT NULL,
    CONSTRAINT pk_sn_alert_recipients PRIMARY KEY (id),
    CONSTRAINT uk_sn_alert_recipients_email UNIQUE (email)
);

/* Registro de cada e-mail de alerta (SENT, FAILED ou SKIPPED) */
CREATE TABLE dbo.sn_email_logs (
    id               BIGINT IDENTITY(1,1) NOT NULL,
    recipient        NVARCHAR(160)        NULL,
    subject          NVARCHAR(200)        NOT NULL,
    event_type       NVARCHAR(40)         NOT NULL,
    reference_id     NVARCHAR(40)         NULL,
    delivery_status  NVARCHAR(20)         NOT NULL,
    error_message    NVARCHAR(500)        NULL,
    created_at       DATETIMEOFFSET(6)    NOT NULL,
    CONSTRAINT pk_sn_email_logs PRIMARY KEY (id),
    CONSTRAINT ck_sn_email_logs_status CHECK (delivery_status IN ('SENT', 'FAILED', 'SKIPPED'))
);
CREATE INDEX ix_sn_email_logs_created ON dbo.sn_email_logs (created_at);
GO
