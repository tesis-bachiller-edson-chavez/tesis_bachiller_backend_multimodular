package org.grubhart.pucp.tesis.module_api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Inicializa el esquema de Spring Session JDBC manualmente.
 * Necesario porque con ddl-auto=create-drop, Spring Boot no ejecuta schema.sql
 */
@Component
@Order(1) // Ejecutar antes que otros CommandLineRunner
public class SpringSessionSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SpringSessionSchemaInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public SpringSessionSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("Iniciando creación de tablas de Spring Session...");

        try {
            // Crear tabla SPRING_SESSION
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS SPRING_SESSION (" +
                "  PRIMARY_ID CHAR(36) NOT NULL," +
                "  SESSION_ID CHAR(36) NOT NULL," +
                "  CREATION_TIME BIGINT NOT NULL," +
                "  LAST_ACCESS_TIME BIGINT NOT NULL," +
                "  MAX_INACTIVE_INTERVAL INT NOT NULL," +
                "  EXPIRY_TIME BIGINT NOT NULL," +
                "  PRINCIPAL_NAME VARCHAR(100)," +
                "  CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)" +
                ") ENGINE=InnoDB ROW_FORMAT=DYNAMIC"
            );
            log.info("Tabla SPRING_SESSION creada exitosamente");

            // Crear índices para SPRING_SESSION verificando si ya existen
            createIndexIfNotExists("SPRING_SESSION_IX1",
                "CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID)");

            createIndexIfNotExists("SPRING_SESSION_IX2",
                "CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME)");

            createIndexIfNotExists("SPRING_SESSION_IX3",
                "CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME)");

            // Crear tabla SPRING_SESSION_ATTRIBUTES
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (" +
                "  SESSION_PRIMARY_ID CHAR(36) NOT NULL," +
                "  ATTRIBUTE_NAME VARCHAR(200) NOT NULL," +
                "  ATTRIBUTE_BYTES BLOB NOT NULL," +
                "  CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME)," +
                "  CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) " +
                "    REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE" +
                ") ENGINE=InnoDB ROW_FORMAT=DYNAMIC"
            );
            log.info("Tabla SPRING_SESSION_ATTRIBUTES creada exitosamente");

            log.info("Esquema de Spring Session JDBC inicializado correctamente");

        } catch (Exception e) {
            log.error("Error al crear el esquema de Spring Session", e);
            throw new RuntimeException("No se pudo inicializar el esquema de Spring Session", e);
        }
    }

    private void createIndexIfNotExists(String indexName, String createIndexSql) {
        try {
            // Verificar si el índice ya existe
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics " +
                "WHERE table_schema = DATABASE() " +
                "AND table_name = 'SPRING_SESSION' " +
                "AND index_name = ?",
                Integer.class,
                indexName
            );

            if (count != null && count > 0) {
                log.info("Índice {} ya existe, omitiendo creación", indexName);
            } else {
                jdbcTemplate.execute(createIndexSql);
                log.info("Índice {} creado exitosamente", indexName);
            }
        } catch (Exception e) {
            log.error("Error al crear el índice {}", indexName, e);
            throw e;
        }
    }
}
