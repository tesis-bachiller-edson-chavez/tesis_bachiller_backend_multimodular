package org.grubhart.pucp.tesis.module_administration;

import org.grubhart.pucp.tesis.module_domain.Role;
import org.grubhart.pucp.tesis.module_domain.RoleName;
import org.grubhart.pucp.tesis.module_domain.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private final RoleRepository roleRepository;

    public DataInitializer(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        logger.info("Iniciando la inicializacion de data maestra");

        Arrays.stream(RoleName.values()).forEach(roleName -> {
            if (roleRepository.findByName(roleName).isEmpty()) { // .isEmpty() es más legible desde Java 11+
                logger.info("Creando rol que no existe: {}", roleName);
                roleRepository.save(new Role(roleName));
            }
        });
        logger.info("Finalizando la inicializacion de data maestra");
    }

}
