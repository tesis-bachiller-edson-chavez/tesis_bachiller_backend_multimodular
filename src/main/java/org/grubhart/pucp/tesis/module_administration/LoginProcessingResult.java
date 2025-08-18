package org.grubhart.pucp.tesis.module_administration;

import org.grubhart.pucp.tesis.module_domain.User;

/**
 * Encapsula el resultado del procesamiento de un login, incluyendo el usuario y si es el primer administrador.
 */
public record LoginProcessingResult(User user, boolean isFirstAdmin) {}