package org.grubhart.pucp.tesis.module_domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("El constructor por defecto debe crear un objeto no nulo")
    void defaultConstructor_shouldCreateNonNullObject() {
        User user = new User();
        assertThat(user).isNotNull();
    }

    @Test
    @DisplayName("Dos usuarios con el mismo githubId deben ser iguales")
    void equals_whenSameGithubId_shouldBeTrue() {
        User user1 = new User(123L, "user-a", "a@test.com");
        User user2 = new User(123L, "user-b", "b@test.com"); // Different username/email
        assertThat(user1).isEqualTo(user2);
    }

    @Test
    @DisplayName("Dos usuarios con diferente githubId no deben ser iguales")
    void equals_whenDifferentGithubId_shouldBeFalse() {
        User user1 = new User(123L, "user", "test@test.com");
        User user2 = new User(456L, "user", "test@test.com");
        assertThat(user1).isNotEqualTo(user2);
    }

    @Test
    @DisplayName("Un usuario no debe ser igual a un objeto de otro tipo")
    void equals_whenDifferentType_shouldBeFalse() {
        User user1 = new User(123L, "user", "test@test.com");
        Object otherObject = new Object();
        assertThat(user1).isNotEqualTo(otherObject);
    }

    @Test
    @DisplayName("Un usuario no debe ser igual a nulo")
    void equals_whenNull_shouldBeFalse() {
        User user1 = new User(123L, "user", "test@test.com");
        assertThat(user1).isNotEqualTo(null);
    }

    @Test
    @DisplayName("Un usuario debe ser igual a sí mismo")
    void equals_whenSameInstance_shouldBeTrue() {
        User user1 = new User(123L, "user", "test@test.com");
        assertThat(user1).isEqualTo(user1);
    }

    @Test
    @DisplayName("El hashCode de dos usuarios con el mismo githubId debe ser igual")
    void hashCode_whenSameGithubId_shouldBeEqual() {
        User user1 = new User(123L, "user-a", "a@test.com");
        User user2 = new User(123L, "user-b", "b@test.com");
        assertThat(user1.hashCode()).isEqualTo(user2.hashCode());
    }

    @Test
    @DisplayName("Getters y Setters deben funcionar correctamente")
    void gettersAndSetters_shouldWorkCorrectly() {
        // GIVEN
        User user = new User();
        Set<Role> roles = new HashSet<>();
        roles.add(new Role(RoleName.ADMIN));

        // WHEN
        user.setId(1L);
        user.setGithubId(123L);
        user.setGithubUsername("test-user");
        user.setEmail("test@email.com");
        user.setRoles(roles);

        // THEN
        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getGithubId()).isEqualTo(123L);
        assertThat(user.getGithubUsername()).isEqualTo("test-user");
        assertThat(user.getEmail()).isEqualTo("test@email.com");
        assertThat(user.getRoles()).isEqualTo(roles);
    }
}