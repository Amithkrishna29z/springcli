package support;

import service.InitializrClient;
import service.MetadataService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class SampleMetadata {

    private SampleMetadata() {
    }

    public static final String JSON = """
        {
          "bootVersion": { "default": "3.3.2", "values": [ {"id":"3.3.2","name":"3.3.2"}, {"id":"3.2.8","name":"3.2.8"} ] },
          "javaVersion": { "default": "21", "values": [ {"id":"17","name":"17"}, {"id":"21","name":"21"} ] },
          "language": { "default": "java", "values": [ {"id":"java","name":"Java"}, {"id":"kotlin","name":"Kotlin"}, {"id":"groovy","name":"Groovy"} ] },
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
                {"id":"web","name":"Spring Web","description":"Build web, including RESTful, applications using Spring MVC"}
              ]},
              { "name": "SQL", "values": [
                {"id":"data-jpa","name":"Spring Data JPA","description":"Persist data in SQL stores with Java Persistence API"},
                {"id":"postgresql","name":"PostgreSQL Driver","description":"A JDBC and R2DBC driver"}
              ]},
              { "name": "Developer Tools", "values": [
                {"id":"devtools","name":"Spring Boot DevTools","description":"Provides fast application restarts"},
                {"id":"lombok","name":"Lombok","description":"Java annotation library which helps reduce boilerplate"}
              ]},
              { "name": "Validation", "values": [
                {"id":"validation","name":"Validation","description":"Bean Validation with Hibernate validator"}
              ]}
            ]
          }
        }
        """;

    public static MetadataService service() {
        InitializrClient client = mock(InitializrClient.class);
        when(client.fetchMetadata()).thenReturn(JSON);
        return new MetadataService(client);
    }
}
