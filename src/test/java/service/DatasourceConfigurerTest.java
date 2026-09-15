package service;

import model.ProjectRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasourceConfigurerTest {

    @TempDir
    Path tempDir;

    private final DatasourceConfigurer configurer = new DatasourceConfigurer();

    /** Creates the resources layout Initializr produces, and returns the project root. */
    private Path project(String initialProperties) throws IOException {
        Path resources = tempDir.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("application.properties"), initialProperties);
        return tempDir;
    }

    private ProjectRequest request(String artifactId, String... dependencies) {
        return ProjectRequest.builder()
                .bootVersion("3.3.2")
                .artifactId(artifactId)
                .dependencies(List.of(dependencies))
                .build();
    }

    private String properties() throws IOException {
        return Files.readString(tempDir.resolve("src/main/resources/application.properties"));
    }

    @Test
    void postgresGetsAUrlDriverAndEnvBackedCredentials() throws IOException {
        Path root = project("spring.application.name=shop\n");
        configurer.configure(request("shop", "web", "postgresql"), root);

        String result = properties();
        assertTrue(result.startsWith("spring.application.name=shop\n"), "existing properties are kept");
        assertTrue(result.contains("spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/shop"));
        assertTrue(result.contains("spring.datasource.driver-class-name=org.postgresql.Driver"));
        assertTrue(result.contains("spring.datasource.username=${DB_USERNAME:postgres}"));
        assertTrue(result.contains("spring.datasource.password=${DB_PASSWORD:postgres}"));
    }

    @Test
    void jpaPropertiesAreAddedOnlyWhenDataJpaIsSelected() throws IOException {
        Path root = project("");
        configurer.configure(request("demo", "mysql", "data-jpa"), root);

        String result = properties();
        assertTrue(result.contains("spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/demo"));
        assertTrue(result.contains("spring.jpa.hibernate.ddl-auto=update"));
        assertTrue(result.contains("spring.jpa.show-sql=true"));
    }

    @Test
    void aDriverWithoutJpaGetsTheDatasourceOnly() throws IOException {
        Path root = project("");
        configurer.configure(request("demo", "h2"), root);

        String result = properties();
        assertTrue(result.contains("spring.datasource.url=jdbc:h2:mem:demo"));
        assertTrue(result.contains("spring.h2.console.enabled=true"));
        assertFalse(result.contains("spring.jpa."));
    }

    @Test
    void artifactIdIsReducedToALegalSchemaName() throws IOException {
        Path root = project("");
        configurer.configure(request("My-Shop.API", "postgresql"), root);

        assertTrue(properties().contains("/my_shop_api"));
    }

    @Test
    void aProjectWithoutADatabaseDriverIsLeftUntouched() throws IOException {
        Path root = project("spring.application.name=demo\n");
        configurer.configure(request("demo", "web", "lombok"), root);

        assertEquals("spring.application.name=demo\n", properties());
    }

    @Test
    void onlyTheFirstDriverIsConfiguredWhenSeveralAreSelected() throws IOException {
        Path root = project("");
        configurer.configure(request("demo", "postgresql", "mysql"), root);

        String result = properties();
        assertTrue(result.contains("jdbc:postgresql://"));
        assertFalse(result.contains("jdbc:mysql://"));
    }

    @Test
    void aMissingPropertiesFileIsCreated() throws IOException {
        configurer.configure(request("demo", "mariadb"), tempDir);

        assertTrue(properties().contains("spring.datasource.driver-class-name=org.mariadb.jdbc.Driver"));
    }
}
