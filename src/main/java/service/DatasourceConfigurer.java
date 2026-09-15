package service;

import exception.SpringCliException;
import model.Database;
import model.ProjectRequest;
import util.Ansi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a ready-to-run {@code spring.datasource.*} block into a freshly generated project when its
 * dependencies include a database driver {@link Database} knows about.
 *
 * <p>Spring Initializr adds the driver to the build file but leaves {@code application.properties}
 * unconfigured, so a project with, say, PostgreSQL selected fails to start until the datasource is
 * filled in by hand. This appends that block instead.</p>
 */
public class DatasourceConfigurer {

    private static final String JPA_DEPENDENCY = "data-jpa";

    /**
     * Appends the datasource configuration for the request's database to
     * {@code src/main/resources/application.properties}. A request without a known database driver
     * is a no-op; when more than one driver was selected only the first is configured, because a
     * single {@code spring.datasource} can only point at one server.
     *
     * @param request    the request the project was generated from
     * @param projectDir the extracted project root
     */
    public void configure(ProjectRequest request, Path projectDir) {
        List<Database> databases = Database.fromDependencies(request.dependencies());
        if (databases.isEmpty()) {
            return;
        }
        Database database = databases.get(0);
        if (databases.size() > 1) {
            Ansi.warn("Several database drivers were selected; configured " + database.displayName()
                    + " only. Add the others as extra datasources yourself.");
        }

        Path properties = projectDir
                .resolve("src").resolve("main").resolve("resources").resolve("application.properties");
        try {
            Files.createDirectories(properties.getParent());
            Files.writeString(properties, existing(properties) + block(request, database));
        } catch (IOException e) {
            throw new SpringCliException("Failed to write the datasource configuration to " + properties + ".", e);
        }

        Ansi.success("Configured " + database.displayName() + " in src/main/resources/application.properties");
    }

    /** @return what Initializr already wrote, normalised to end with a newline so the block starts clean. */
    private static String existing(Path properties) throws IOException {
        if (!Files.isRegularFile(properties)) {
            return "";
        }
        String content = Files.readString(properties);
        if (content.isBlank()) {
            return "";
        }
        return content.endsWith("\n") ? content : content + "\n";
    }

    /** Builds the block to append, separated by a blank line from whatever came before it. */
    private static String block(ProjectRequest request, Database database) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("# --- " + database.displayName() + " (configured by springcli) ---");
        lines.addAll(database.properties(databaseName(request)));
        if (request.dependencies().contains(JPA_DEPENDENCY)) {
            lines.add("");
            lines.add("# JPA / Hibernate");
            lines.addAll(Database.JPA_PROPERTIES);
        }
        return String.join("\n", lines) + "\n";
    }

    /** @return the artifact id reduced to a legal schema name, e.g. {@code my-app} to {@code my_app}. */
    private static String databaseName(ProjectRequest request) {
        String name = request.artifactId().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        return name.isBlank() ? "demo" : name;
    }
}
