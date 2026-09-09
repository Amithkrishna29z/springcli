package service;

import exception.SpringCliException;
import model.Architecture;
import model.ProjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates the package skeleton for a {@link Architecture} inside a freshly generated project.
 *
 * <p>Each package is an empty directory holding a {@code .gitkeep}, so the layout survives a
 * {@code git add} and nothing is added that has to compile.</p>
 */
public class ArchitectureScaffolder {

    /**
     * Creates the architecture's package directories under the project's base package. A
     * {@link Architecture#NONE} request, or a project whose base package directory is missing, is a
     * no-op.
     *
     * @param request    the request the project was generated from
     * @param projectDir the extracted project root
     */
    public void scaffold(ProjectRequest request, Path projectDir) {
        Architecture architecture = request.architecture();
        if (architecture.packages().isEmpty()) {
            return;
        }

        Path basePackage = projectDir
                .resolve("src").resolve("main").resolve(request.language())
                .resolve(request.packageName().replace('.', '/'));
        if (!Files.isDirectory(basePackage)) {
            return;
        }

        try {
            for (String pkg : architecture.packages()) {
                Path dir = basePackage.resolve(pkg);
                Files.createDirectories(dir);
                Files.writeString(dir.resolve(".gitkeep"), "");
            }
        } catch (IOException e) {
            throw new SpringCliException(
                    "Failed to create the " + architecture.id() + " package structure in " + basePackage + ".", e);
        }
    }
}
