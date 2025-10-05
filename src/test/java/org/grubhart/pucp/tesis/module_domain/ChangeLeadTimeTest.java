package org.grubhart.pucp.tesis.module_domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class ChangeLeadTimeTest {

    private Commit commitMock;
    private Deployment deploymentMock;

    @BeforeEach
    void setUp() {
        commitMock = Mockito.mock(Commit.class);
        deploymentMock = Mockito.mock(Deployment.class);
    }

    @Test
    @DisplayName("Constructor debe asignar correctamente Commit, Deployment y LeadTime")
    void constructor_ShouldAssignPropertiesCorrectly() {
        // Arrange
        long expectedLeadTime = 12345L;

        // Act
        ChangeLeadTime changeLeadTime = new ChangeLeadTime(commitMock, deploymentMock, expectedLeadTime);

        // Assert
        assertNotNull(changeLeadTime, "El objeto no debería ser nulo");
        assertEquals(commitMock, changeLeadTime.getCommit(), "El commit no coincide");
        assertEquals(deploymentMock, changeLeadTime.getDeployment(), "El deployment no coincide");
        assertEquals(expectedLeadTime, changeLeadTime.getLeadTimeInSeconds(), "El lead time no coincide");
    }

    @Test
    @DisplayName("Debe establecer y obtener el Id correctamente")
    void shouldSetAndGetId() {
        // Arrange
        ChangeLeadTime changeLeadTime = new ChangeLeadTime();
        Long expectedId = 1L;

        // Act
        changeLeadTime.setId(expectedId);
        Long actualId = changeLeadTime.getId();

        // Assert
        assertEquals(expectedId, actualId);
    }

    @Test
    @DisplayName("Debe establecer y obtener el Commit correctamente")
    void shouldSetAndGetCommit() {
        // Arrange
        ChangeLeadTime changeLeadTime = new ChangeLeadTime();

        // Act
        changeLeadTime.setCommit(commitMock);
        Commit actualCommit = changeLeadTime.getCommit();

        // Assert
        assertEquals(commitMock, actualCommit);
    }

    @Test
    @DisplayName("Debe establecer y obtener el Deployment correctamente")
    void shouldSetAndGetDeployment() {
        // Arrange
        ChangeLeadTime changeLeadTime = new ChangeLeadTime();

        // Act
        changeLeadTime.setDeployment(deploymentMock);
        Deployment actualDeployment = changeLeadTime.getDeployment();

        // Assert
        assertEquals(deploymentMock, actualDeployment);
    }

    @Test
    @DisplayName("Debe establecer y obtener el LeadTimeInSeconds correctamente")
    void shouldSetAndGetLeadTimeInSeconds() {
        // Arrange
        ChangeLeadTime changeLeadTime = new ChangeLeadTime();
        long expectedLeadTime = 98765L;

        // Act
        changeLeadTime.setLeadTimeInSeconds(expectedLeadTime);
        long actualLeadTime = changeLeadTime.getLeadTimeInSeconds();

        // Assert
        assertEquals(expectedLeadTime, actualLeadTime);
    }

    @Test
    @DisplayName("Equals y HashCode deben ser consistentes y basados en el Id")
    void equalsAndHashCode_ShouldBeConsistentAndBasedOnId() {
        // Arrange
        ChangeLeadTime clt1 = new ChangeLeadTime(commitMock, deploymentMock, 100L);
        clt1.setId(1L);

        ChangeLeadTime clt2 = new ChangeLeadTime(commitMock, deploymentMock, 100L);
        clt2.setId(1L);

        ChangeLeadTime clt3 = new ChangeLeadTime(commitMock, deploymentMock, 200L);
        clt3.setId(2L);

        // Assert
        assertEquals(clt1, clt2, "Dos objetos con el mismo Id deben ser iguales");
        assertNotEquals(clt1, clt3, "Dos objetos con diferente Id no deben ser iguales");
        assertNotEquals(clt1, null, "Un objeto no debe ser igual a nulo");
        assertNotEquals(clt1, new Object(), "Un objeto no debe ser igual a un objeto de otra clase");

        assertEquals(clt1.hashCode(), clt2.hashCode(), "Hashcodes de objetos iguales deben ser iguales");
        assertNotEquals(clt1.hashCode(), clt3.hashCode(), "Hashcodes de objetos diferentes deberían ser diferentes");
    }

    @Test
    @DisplayName("Equals debe devolver verdadero para la misma instancia")
    void equals_givenSameInstance_shouldReturnTrue() {
        // Arrange
        ChangeLeadTime changeLeadTime = new ChangeLeadTime(commitMock, deploymentMock, 100L);
        changeLeadTime.setId(1L);

        // Act & Assert
        assertTrue(changeLeadTime.equals(changeLeadTime), "Un objeto debe ser igual a sí mismo.");
    }
}
