-- Verificar estructura real de las tablas

-- 1. Estructura de la tabla deployment
DESCRIBE deployment;

-- 2. Estructura de la tabla change_lead_time
DESCRIBE change_lead_time;

-- 3. Estructura de la tabla commit
DESCRIBE commit;

-- 4. Ver todas las columnas de deployment
SHOW COLUMNS FROM deployment;

-- 5. Ver todas las Foreign Keys de deployment
SELECT
    TABLE_NAME,
    COLUMN_NAME,
    CONSTRAINT_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_NAME = 'deployment'
  AND REFERENCED_TABLE_NAME IS NOT NULL;

-- 6. Ver un deployment de ejemplo para saber qué columnas tiene
SELECT * FROM deployment LIMIT 1;
