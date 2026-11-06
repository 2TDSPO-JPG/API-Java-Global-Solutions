/* =====================================================================================
   Saneia · Consultas para comprovar o CRUD direto no banco (vídeo de DevOps)
   Faça a operação pela API (Swagger ou Postman) e rode o SELECT correspondente no Query editor do Azure SQL.
   ===================================================================================== */

/* ---------- Tabela 1: sn_sensors (CRUD do administrador em /admin/sensors) ---------- */
-- Depois do POST /admin/sensors (CREATE):
SELECT id, prefix, sensor_name, neighborhood, capacity_liters, limit_liters, radius_meters, current_status, active
FROM sn_sensors ORDER BY id DESC;
-- Depois do PATCH /admin/sensors/{id} (UPDATE): confira o nome/raio alterados
SELECT id, prefix, sensor_name, radius_meters, updated_at FROM sn_sensors WHERE prefix = 'RES-21';
-- Depois do DELETE /admin/sensors/{id} (exclusão lógica: some do catálogo, preserva o histórico)
SELECT id, prefix, active, deleted_at FROM sn_sensors WHERE prefix = 'RES-21';

/* ---------- Tabela 2: sn_reports (CRUD do morador em /reports) ---------- */
-- Depois do POST /reports (CREATE): protocolo e data gerados no servidor
SELECT TOP 5 id, protocol, owner_id, category, report_status, sensor_id, created_at FROM sn_reports ORDER BY id DESC;
-- Depois do PATCH /reports/{id} (UPDATE do morador) e do PATCH /reports/{id}/status (operadora)
SELECT id, protocol, description, report_status, progress_note, resolution, closed_at FROM sn_reports WHERE id = 1;
-- Depois do DELETE /reports/{id} (exclusão lógica: mantém dados mínimos para a regra de 20 dias)
SELECT id, protocol, report_status, deleted_at FROM sn_reports WHERE deleted_at IS NOT NULL;

/* ---------- Tabela 3: sn_users (POST /auth/signup) ---------- */
SELECT id, full_name, email, user_role, created_at FROM sn_users ORDER BY id;

/* ---------- Leituras e eventos gravados pelo consumidor do RabbitMQ ---------- */
SELECT TOP 10 r.id, s.prefix, r.volume_liters, r.reading_status, r.measured_at
FROM sn_sensor_readings r JOIN sn_sensors s ON s.id = r.sensor_id ORDER BY r.id DESC;
SELECT TOP 10 e.id, s.prefix, e.event_kind, e.severity, e.started_at, e.ended_at, e.event_message
FROM sn_sensor_events e JOIN sn_sensors s ON s.id = e.sensor_id ORDER BY e.id DESC;
SELECT TOP 10 id, recipient_user_id, recipient_role, notification_type, title, created_at
FROM sn_notifications ORDER BY id DESC;

/* ---------- Alertas de risco máximo (painel /api/painel/alertas) ---------- */
SELECT id, full_name, email, active FROM sn_alert_recipients ORDER BY id;
SELECT TOP 10 id, recipient, subject, delivery_status, error_message, created_at FROM sn_email_logs ORDER BY id DESC;
