package service;

import exception.SpringCliException;
import exception.ValidationException;
import model.Metadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataServiceTest {

    /** Minimal but structurally faithful metadata document. */
    private static final String SAMPLE_JSON = """
        {
          "bootVersion": { "default": "3.3.2", "values": [ {"id":"3.3.2","name":"3.3.2"}, {"id":"3.2.8","name":"3.2.8"} ] },
          "javaVersion": { "default": "21", "values": [ {"id":"17","name":"17"}, {"id":"21","name":"21"} ] },
          "language": { "default": "java", "values": [ {"id":"java","name":"Java"}, {"id":"kotlin","name":"Kotlin"} ] },
          "packaging": { "default": "jar", "values": [ {"id":"jar","name":"Jar"}, {"id":"war","name":"War"} ] },
          "type": { "default": "maven-project", "values": [ {"id":"maven-project","name":"Maven"}, {"id":"gradle-project","name":"Gradle"} ] },
          "groupId": { "default": "com.example" },
          "artifactId": { "default": "demo" },
          "name": { "default": "demo" },
          "description": { "default": "Demo project for Spring Boot" },
          "packageName": { "default": "com.example.demo" },
          "version": { "default": "0.0.1-SNAPSHOT" },
          "dependencies": {
            "values": [
              { "name": "Web", "values": [
                  {"id":"web","name":"Spring Web","description":"Build web, including RESTful, applications"},
                  {"id":"websocket","name":"WebSocket","description":"WebSocket support"}
              ]},
              { "name": "SQL", "values": [
                  {"id":"data-jpa","name":"Spring Data JPA","description":"Persist data with JPA"}
              ]}
            ]
          }
        }
        """;

    private MetadataService serviceReturning(String json) {
        InitializrClient client = mock(InitializrClient.class);
        when(client.fetchMetadata()).thenReturn(json);
        return new MetadataService(client);
    }

    @Test
    void parsesAllTopLevelFields() {
        Metadata md = serviceReturning(SAMPLE_JSON).getMetadata();
        assertEquals("3.3.2", md.bootVersion().defaultValue());
        assertEquals("21", md.javaVersion().defaultValue());
        assertEquals("com.example", md.groupId().defaultValue());
        assertEquals(3, md.dependencies().values().stream().mapToInt(g -> g.values().size()).sum());
    }

    @Test
    void cachesMetadataAcrossCalls() {
        InitializrClient client = mock(InitializrClient.class);
        when(client.fetchMetadata()).thenReturn(SAMPLE_JSON);
        MetadataService service = new MetadataService(client);

        service.getMetadata();
        service.getMetadata();

        verify(client, times(1)).fetchMetadata();
    }

    @Test
    void searchMatchesNameIdAndDescription() {
        MetadataService service = serviceReturning(SAMPLE_JSON);
        List<Metadata.Dependency> byName = service.searchDependencies("web");
        assertEquals(2, byName.size()); // Spring Web + WebSocket
        List<Metadata.Dependency> byDescription = service.searchDependencies("persist");
        assertEquals(1, byDescription.size());
        assertEquals("data-jpa", byDescription.get(0).id());
    }

    @Test
    void blankSearchReturnsEverything() {
        MetadataService service = serviceReturning(SAMPLE_JSON);
        assertEquals(3, service.searchDependencies("  ").size());
    }

    @Test
    void validationRejectsUnknownValues() {
        MetadataService service = serviceReturning(SAMPLE_JSON);
        assertThrows(ValidationException.class, () -> service.validateBootVersion("9.9.9"));
        assertThrows(ValidationException.class, () -> service.validateJavaVersion("8"));
        assertThrows(ValidationException.class, () -> service.validateDependency("nope"));
    }

    @Test
    void validationAcceptsKnownValues() {
        MetadataService service = serviceReturning(SAMPLE_JSON);
        service.validateBootVersion("3.2.8");
        service.validateJavaVersion("17");
        service.validateDependency("web");
        assertTrue(service.findDependency("web").isPresent());
    }

    @Test
    void malformedJsonRaisesSpringCliException() {
        MetadataService service = serviceReturning("{ not json");
        assertThrows(SpringCliException.class, service::getMetadata);
    }
}
