package org.grubhart.pucp.tesis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

@SpringBootApplication
@EnableScheduling // Habilita la detección y ejecución de tareas programadas (@Scheduled)
@EnableJdbcHttpSession // Habilita Spring Session JDBC para sesiones compartidas entre instancias
public class TesisBachillerBackendMultimodularApplication {

    public static void main(String[] args) {
        SpringApplication.run(TesisBachillerBackendMultimodularApplication.class, args);
    }

}
