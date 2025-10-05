package org.grubhart.pucp.tesis.module_domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class CommitParentTest {

    private Commit commitMock;
    private Commit parentMock;

    @BeforeEach
    void setUp() {
        commitMock = Mockito.mock(Commit.class);
        parentMock = Mockito.mock(Commit.class);
    }

    @Test
    @DisplayName("Test del constructor por defecto")
    void testDefaultConstructor() {
        CommitParent commitParent = new CommitParent();
        assertNotNull(commitParent, "El objeto no deberia ser nulo");
        assertNull(commitParent.getCommit(), "El commit deberia ser nulo");
        assertNull(commitParent.getParent(), "El parent deberia ser nulo");
    }

    @Test
    @DisplayName("Test del constructor con parametros")
    void testParameterizedConstructor() {
        CommitParent commitParent = new CommitParent(commitMock, parentMock);

        assertNotNull(commitParent, "El objeto no deberia ser nulo");
        assertEquals(commitMock, commitParent.getCommit(), "El commit no coincide");
        assertEquals(parentMock, commitParent.getParent(), "El parent no coincide");
    }

    @Test
    @DisplayName("Test para setId y getId")
    void testSetAndGetId() {
        CommitParent commitParent = new CommitParent();
        Long expectedId = 1L;
        commitParent.setId(expectedId);

        assertEquals(expectedId, commitParent.getId(), "El Id no coincide");
    }

    @Test
    @DisplayName("Test para setCommit y getCommit")
    void testSetAndGetCommit() {
        CommitParent commitParent = new CommitParent();
        commitParent.setCommit(commitMock);

        assertEquals(commitMock, commitParent.getCommit(), "El commit no coincide");
    }

    @Test
    @DisplayName("Test para setParent y getParent")
    void testSetAndGetParent() {
        CommitParent commitParent = new CommitParent();
        commitParent.setParent(parentMock);

        assertEquals(parentMock, commitParent.getParent(), "El parent no coincide");
    }
}
