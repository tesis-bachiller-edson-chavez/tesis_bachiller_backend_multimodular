package org.grubhart.pucp.tesis.module_domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    @DisplayName("El constructor por defecto debe crear un objeto no nulo")
    void defaultConstructor_shouldCreateNonNullObject() {
        Role role = new Role();
        assertThat(role).isNotNull();
    }

    @Test
    @DisplayName("Dos roles con el mismo nombre deben ser iguales")
    void equals_whenSameName_shouldBeTrue() {
        Role role1 = new Role(RoleName.ADMIN);
        Role role2 = new Role(RoleName.ADMIN);
        assertThat(role1).isEqualTo(role2);
    }

    @Test
    @DisplayName("Dos roles con diferente nombre no deben ser iguales")
    void equals_whenDifferentName_shouldBeFalse() {
        Role role1 = new Role(RoleName.ADMIN);
        Role role2 = new Role(RoleName.DEVELOPER);
        assertThat(role1).isNotEqualTo(role2);
    }

    @Test
    @DisplayName("Un rol no debe ser igual a un objeto de otro tipo")
    void equals_whenDifferentType_shouldBeFalse() {
        Role role1 = new Role(RoleName.ADMIN);
        Object otherObject = new Object();
        assertThat(role1).isNotEqualTo(otherObject);
    }

    @Test
    @DisplayName("Un rol no debe ser igual a nulo")
    void equals_whenNull_shouldBeFalse() {
        Role role1 = new Role(RoleName.ADMIN);
        assertThat(role1).isNotEqualTo(null);
    }

    @Test
    @DisplayName("Un rol debe ser igual a sí mismo")
    void equals_whenSameInstance_shouldBeTrue() {
        Role role1 = new Role(RoleName.ADMIN);
        assertThat(role1).isEqualTo(role1);
    }

    @Test
    @DisplayName("El hashCode de dos roles con el mismo nombre debe ser igual")
    void hashCode_whenSameName_shouldBeEqual() {
        Role role1 = new Role(RoleName.ADMIN);
        Role role2 = new Role(RoleName.ADMIN);
        assertThat(role1.hashCode()).isEqualTo(role2.hashCode());
    }

    @Test
    @DisplayName("Getters y Setters deben funcionar correctamente")
    void gettersAndSetters_shouldWorkCorrectly() {
        // GIVEN
        Role role = new Role();

        // WHEN
        role.setId(1L);
        role.setName(RoleName.TECH_LEAD);

        // THEN
        assertThat(role.getId()).isEqualTo(1L);
        assertThat(role.getName()).isEqualTo(RoleName.TECH_LEAD);
    }
}