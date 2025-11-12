-- ================================================
-- DEBUG: Investigar Lead Time del 2 de Noviembre (MySQL)
-- ================================================
-- NOTA: Basado en las entidades JPA del proyecto
-- ChangeLeadTime usa commit_sha (FK a commit.sha, NO commit.id)

-- 1. Ver todos los deployments del 2 de noviembre de 2025
SELECT
    d.id AS deployment_id,
    d.created_at,
    DATE(d.created_at) AS deployment_date,
    d.service_name,
    d.sha,
    r.repository_url,
    SUBSTRING_INDEX(SUBSTRING_INDEX(r.repository_url, '/', -1), '.', 1) AS repo_name
FROM deployment d
JOIN repository_config r ON d.repository_id = r.id
WHERE DATE(d.created_at) = '2025-11-02'
ORDER BY d.created_at;

-- 2. Ver ChangeLeadTime records que se agrupan en 2 de noviembre
-- (deployment createdAt es 2 de noviembre)
SELECT
    clt.id AS change_lead_time_id,
    clt.lead_time_in_seconds,
    clt.lead_time_in_seconds / 3600.0 AS lead_time_hours,
    d.id AS deployment_id,
    d.created_at AS deployment_created_at,
    DATE(d.created_at) AS deployment_date,
    c.sha AS commit_sha,
    c.date AS commit_date,
    c.author AS commit_author
FROM change_lead_time clt
JOIN deployment d ON clt.deployment_id = d.id
JOIN commit c ON clt.commit_sha = c.sha  -- ¡IMPORTANTE: usa commit_sha, no commit_id!
WHERE DATE(d.created_at) = '2025-11-02'
ORDER BY d.created_at;

-- 3. Ver ChangeLeadTime con lead time de aproximadamente 268 horas
-- (268 horas = 964800 segundos, buscar en rango ±10%)
SELECT
    clt.id AS change_lead_time_id,
    clt.lead_time_in_seconds,
    clt.lead_time_in_seconds / 3600.0 AS lead_time_hours,
    d.id AS deployment_id,
    d.created_at AS deployment_created_at,
    DATE(d.created_at) AS deployment_date,
    c.sha AS commit_sha,
    c.date AS commit_date,
    c.author AS commit_author,
    r.repository_url,
    SUBSTRING_INDEX(SUBSTRING_INDEX(r.repository_url, '/', -1), '.', 1) AS repo_name
FROM change_lead_time clt
JOIN deployment d ON clt.deployment_id = d.id
JOIN commit c ON clt.commit_sha = c.sha  -- ¡IMPORTANTE: usa commit_sha, no commit_id!
JOIN repository_config r ON d.repository_id = r.id
WHERE clt.lead_time_in_seconds BETWEEN 867120 AND 1062480  -- 268h ± 10%
ORDER BY clt.lead_time_in_seconds DESC;

-- 4. Ver deployments en rango de fechas alrededor del 2 de noviembre
SELECT
    d.id AS deployment_id,
    d.created_at,
    DATE(d.created_at) AS deployment_date,
    COUNT(clt.id) AS change_lead_time_count,
    AVG(clt.lead_time_in_seconds / 3600.0) AS avg_lead_time_hours
FROM deployment d
LEFT JOIN change_lead_time clt ON d.id = clt.deployment_id
WHERE DATE(d.created_at) BETWEEN '2025-11-01' AND '2025-11-03'
GROUP BY d.id, d.created_at
ORDER BY d.created_at;

-- 5. Ver zona horaria del servidor MySQL
SELECT @@session.time_zone AS session_timezone,
       @@global.time_zone AS global_timezone,
       NOW() AS current_time,
       UTC_TIMESTAMP() AS utc_time;

-- 6. Ver ChangeLeadTime para el usuario específico (reemplaza 'Grubhart' con tu username)
SELECT
    clt.id,
    c.author,
    c.date AS commit_date,
    d.created_at AS deployment_date,
    DATE(d.created_at) AS deployment_date_only,
    clt.lead_time_in_seconds / 3600.0 AS lead_time_hours,
    r.repository_url,
    SUBSTRING_INDEX(SUBSTRING_INDEX(r.repository_url, '/', -1), '.', 1) AS repo_name
FROM change_lead_time clt
JOIN commit c ON clt.commit_sha = c.sha  -- ¡IMPORTANTE: usa commit_sha, no commit_id!
JOIN deployment d ON clt.deployment_id = d.id
JOIN repository_config r ON d.repository_id = r.id
WHERE LOWER(c.author) = LOWER('Grubhart')  -- Reemplaza con tu username
  AND DATE(d.created_at) BETWEEN '2025-11-01' AND '2025-11-03'
ORDER BY d.created_at;
