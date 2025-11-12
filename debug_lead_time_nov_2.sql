-- ================================================
-- DEBUG: Investigar Lead Time del 2 de Noviembre
-- ================================================

-- 1. Ver todos los deployments del 2 de noviembre de 2025
SELECT
    d.id AS deployment_id,
    d.created_at,
    d.created_at::date AS deployment_date,
    d.service_name,
    d.sha,
    r.repo_name
FROM deployment d
JOIN repository_config r ON d.repository_id = r.id
WHERE d.created_at::date = '2025-11-02'
ORDER BY d.created_at;

-- 2. Ver ChangeLeadTime records que se agrupan en 2 de noviembre
-- (deployment createdAt es 2 de noviembre)
SELECT
    clt.id AS change_lead_time_id,
    clt.lead_time_in_seconds,
    clt.lead_time_in_seconds / 3600.0 AS lead_time_hours,
    d.id AS deployment_id,
    d.created_at AS deployment_created_at,
    d.created_at::date AS deployment_date,
    c.sha AS commit_sha,
    c.date AS commit_date,
    c.author AS commit_author
FROM change_lead_time clt
JOIN deployment d ON clt.deployment_id = d.id
JOIN commit c ON clt.commit_id = c.id
WHERE d.created_at::date = '2025-11-02'
ORDER BY d.created_at;

-- 3. Ver ChangeLeadTime con lead time de aproximadamente 268 horas
-- (268 horas = 964800 segundos, buscar en rango ±10%)
SELECT
    clt.id AS change_lead_time_id,
    clt.lead_time_in_seconds,
    clt.lead_time_in_seconds / 3600.0 AS lead_time_hours,
    d.id AS deployment_id,
    d.created_at AS deployment_created_at,
    d.created_at::date AS deployment_date,
    c.sha AS commit_sha,
    c.date AS commit_date,
    c.author AS commit_author,
    r.repo_name
FROM change_lead_time clt
JOIN deployment d ON clt.deployment_id = d.id
JOIN commit c ON clt.commit_id = c.id
JOIN repository_config r ON d.repository_id = r.id
WHERE clt.lead_time_in_seconds BETWEEN 867120 AND 1062480  -- 268h ± 10%
ORDER BY clt.lead_time_in_seconds DESC;

-- 4. Ver deployments en rango de fechas alrededor del 2 de noviembre
SELECT
    d.id AS deployment_id,
    d.created_at,
    d.created_at::date AS deployment_date,
    COUNT(clt.id) AS change_lead_time_count,
    AVG(clt.lead_time_in_seconds / 3600.0) AS avg_lead_time_hours
FROM deployment d
LEFT JOIN change_lead_time clt ON d.id = clt.deployment_id
WHERE d.created_at::date BETWEEN '2025-11-01' AND '2025-11-03'
GROUP BY d.id, d.created_at
ORDER BY d.created_at;

-- 5. Ver zona horaria del servidor PostgreSQL
SHOW timezone;

-- 6. Ver ChangeLeadTime para el usuario específico (reemplaza 'Grubhart' con tu username)
SELECT
    clt.id,
    c.author,
    c.date AS commit_date,
    d.created_at AS deployment_date,
    d.created_at::date AS deployment_date_only,
    clt.lead_time_in_seconds / 3600.0 AS lead_time_hours,
    r.repo_name
FROM change_lead_time clt
JOIN commit c ON clt.commit_id = c.id
JOIN deployment d ON clt.deployment_id = d.id
JOIN repository_config r ON d.repository_id = r.id
WHERE LOWER(c.author) = LOWER('Grubhart')  -- Reemplaza con tu username
  AND d.created_at::date BETWEEN '2025-11-01' AND '2025-11-03'
ORDER BY d.created_at;
