package org.grubhart.pucp.tesis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Habilita la detección y ejecución de tareas programadas (@Scheduled)
public class TesisBachillerBackendMultimodularApplication {

    public static void main(String[] args) {
        SpringApplication.run(TesisBachillerBackendMultimodularApplication.class, args);
    }

}
