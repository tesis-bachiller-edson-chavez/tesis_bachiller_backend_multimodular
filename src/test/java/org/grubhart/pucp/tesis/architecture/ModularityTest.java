package org.grubhart.pucp.tesis.architecture;

import org.grubhart.pucp.tesis.TesisBachillerBackendMultimodularApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTest {

    // Carga la definición de los módulos de tu aplicación a partir de la clase principal.
    // Spring Modulith analiza la estructura de paquetes para identificar los módulos.
    ApplicationModules modules = ApplicationModules.of(TesisBachillerBackendMultimodularApplication.class);

    @Test
    @DisplayName("Verifica la estructura modular y las dependencias permitidas")
    void verifyModularity() {
        // Este es el test principal.
        // El metodo .verify() comprueba que no haya dependencias prohibidas
        // (ej. un módulo accediendo a clases internas de otro) o ciclos de dependencia.
        // Si tu código rompe la arquitectura, este test fallará.
        modules.verify();
    }

    @Test
    @DisplayName("Genera la documentación de la arquitectura")
    void createDocumentation() {
        // Esta es una herramienta fantástica para la documentación.
        // Genera diagramas de la arquitectura (componentes, dependencias) en la carpeta target/spring-modulith-docs
        new Documenter(modules).writeDocumentation();
    }
}