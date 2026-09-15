package model;

import java.util.ArrayList;
import java.util.List;

/**
 * A SQL database whose Initializr driver dependency the CLI knows how to configure. Selecting one
 * of these when creating a project is enough for {@code service.DatasourceConfigurer} to write a
 * working {@code spring.datasource.*} block into {@code application.properties}.
 *
 * <p>Credentials are written as {@code ${ENV_VAR:default}} placeholders so a generated project runs
 * against a local server out of the box, while a real deployment only has to set the environment
 * variables. {@code {db}} in a property line is replaced with the project's database name.</p>
 */
public enum Database {

    H2("h2", "H2", List.of(
            "spring.datasource.url=jdbc:h2:mem:{db}",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=${DB_USERNAME:sa}",
            "spring.datasource.password=${DB_PASSWORD:}",
            "spring.h2.console.enabled=true")),

    HSQL("hsql", "HSQLDB", List.of(
            "spring.datasource.url=jdbc:hsqldb:mem:{db}",
            "spring.datasource.driver-class-name=org.hsqldb.jdbc.JDBCDriver",
            "spring.datasource.username=${DB_USERNAME:sa}",
            "spring.datasource.password=${DB_PASSWORD:}")),

    DERBY("derby", "Apache Derby", List.of(
            "spring.datasource.url=jdbc:derby:memory:{db};create=true",
            "spring.datasource.driver-class-name=org.apache.derby.jdbc.EmbeddedDriver")),

    MYSQL("mysql", "MySQL", List.of(
            "spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/{db}",
            "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
            "spring.datasource.username=${DB_USERNAME:root}",
            "spring.datasource.password=${DB_PASSWORD:root}")),

    MARIADB("mariadb", "MariaDB", List.of(
            "spring.datasource.url=jdbc:mariadb://${DB_HOST:localhost}:${DB_PORT:3306}/{db}",
            "spring.datasource.driver-class-name=org.mariadb.jdbc.Driver",
            "spring.datasource.username=${DB_USERNAME:root}",
            "spring.datasource.password=${DB_PASSWORD:root}")),

    POSTGRESQL("postgresql", "PostgreSQL", List.of(
            "spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/{db}",
            "spring.datasource.driver-class-name=org.postgresql.Driver",
            "spring.datasource.username=${DB_USERNAME:postgres}",
            "spring.datasource.password=${DB_PASSWORD:postgres}")),

    SQLSERVER("sqlserver", "Microsoft SQL Server", List.of(
            "spring.datasource.url=jdbc:sqlserver://${DB_HOST:localhost}:${DB_PORT:1433};databaseName={db};encrypt=false",
            "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "spring.datasource.username=${DB_USERNAME:sa}",
            "spring.datasource.password=${DB_PASSWORD:Password!23}")),

    ORACLE("oracle", "Oracle", List.of(
            "spring.datasource.url=jdbc:oracle:thin:@${DB_HOST:localhost}:${DB_PORT:1521}/${DB_SERVICE:XEPDB1}",
            "spring.datasource.driver-class-name=oracle.jdbc.OracleDriver",
            "spring.datasource.username=${DB_USERNAME:system}",
            "spring.datasource.password=${DB_PASSWORD:oracle}")),

    DB2("db2", "IBM Db2", List.of(
            "spring.datasource.url=jdbc:db2://${DB_HOST:localhost}:${DB_PORT:50000}/{db}",
            "spring.datasource.driver-class-name=com.ibm.db2.jcc.DB2Driver",
            "spring.datasource.username=${DB_USERNAME:db2inst1}",
            "spring.datasource.password=${DB_PASSWORD:password}"));

    /** Properties added on top of the datasource when the project also uses Spring Data JPA. */
    public static final List<String> JPA_PROPERTIES = List.of(
            "spring.jpa.hibernate.ddl-auto=update",
            "spring.jpa.show-sql=true",
            "spring.jpa.properties.hibernate.format_sql=true");

    private final String dependencyId;
    private final String displayName;
    private final List<String> properties;

    Database(String dependencyId, String displayName, List<String> properties) {
        this.dependencyId = dependencyId;
        this.displayName = displayName;
        this.properties = properties;
    }

    /** @return the Initializr dependency id that selects this database's driver. */
    public String dependencyId() {
        return dependencyId;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * @param databaseName the schema/database name to point the datasource at
     * @return the {@code application.properties} lines for this database
     */
    public List<String> properties(String databaseName) {
        return properties.stream().map(line -> line.replace("{db}", databaseName)).toList();
    }

    /**
     * @param dependencyIds the dependency ids a project was generated with
     * @return the known databases among them, in the order they were selected
     */
    public static List<Database> fromDependencies(List<String> dependencyIds) {
        List<Database> found = new ArrayList<>();
        if (dependencyIds == null) {
            return found;
        }
        for (String id : dependencyIds) {
            for (Database db : values()) {
                if (db.dependencyId.equalsIgnoreCase(id)) {
                    found.add(db);
                }
            }
        }
        return found;
    }
}
