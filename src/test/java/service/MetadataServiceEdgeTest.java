package service;

import exception.SpringCliException;
import org.junit.jupiter.api.Test;
import support.SampleMetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetadataServiceEdgeTest {

    @Test
    void searchMatchesById() {
        assertEquals(1, SampleMetadata.service().searchDependencies("postgresql").size());
    }

    @Test
    void searchIsCaseInsensitive() {
        assertEquals(
                SampleMetadata.service().searchDependencies("web").size(),
                SampleMetadata.service().searchDependencies("WEB").size());
    }

    @Test
    void dependencyGroupsArePreserved() {
        assertEquals(4, SampleMetadata.service().dependencyGroups().size());
    }

    @Test
    void allDependenciesAreFlattened() {
        assertEquals(6, SampleMetadata.service().allDependencies().size());
    }

    @Test
    void findDependencyForUnknownIdIsEmpty() {
        assertTrue(SampleMetadata.service().findDependency("does-not-exist").isEmpty());
    }

    @Test
    void emptyMetadataDocumentIsRejected() {
        InitializrClient client = mock(InitializrClient.class);
        when(client.fetchMetadata()).thenReturn("{}");
        assertThrows(SpringCliException.class, () -> new MetadataService(client).getMetadata());
    }

    @Test
    void metadataMissingDependenciesIsRejected() {
        InitializrClient client = mock(InitializrClient.class);
        when(client.fetchMetadata()).thenReturn(
                "{ \"bootVersion\": { \"default\": \"3.3.2\", \"values\": [ {\"id\":\"3.3.2\",\"name\":\"3.3.2\"} ] } }");
        assertThrows(SpringCliException.class, () -> new MetadataService(client).getMetadata());
    }
}
