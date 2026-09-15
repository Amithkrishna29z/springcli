package model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    @Test
    void driversAreMatchedByDependencyIdInSelectionOrder() {
        assertEquals(List.of(Database.MYSQL, Database.H2),
                Database.fromDependencies(List.of("web", "mysql", "lombok", "h2")));
    }

    @Test
    void dependenciesWithoutADriverMatchNothing() {
        assertTrue(Database.fromDependencies(List.of("web", "data-jpa")).isEmpty());
        assertTrue(Database.fromDependencies(null).isEmpty());
    }

    @Test
    void theDatabaseNamePlaceholderIsFilledIn() {
        assertTrue(Database.POSTGRESQL.properties("orders").contains(
                "spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/orders"));
        assertTrue(Database.SQLSERVER.properties("orders").stream()
                .anyMatch(line -> line.contains("databaseName=orders")));
    }

    @Test
    void everyDriverConfiguresADatasourceUrl() {
        for (Database db : Database.values()) {
            assertTrue(db.properties("demo").stream().anyMatch(l -> l.startsWith("spring.datasource.url=")),
                    db.dependencyId() + " should set a datasource url");
        }
    }
}
