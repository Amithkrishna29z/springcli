package service;

import exception.SpringCliException;
import exception.ValidationException;
import model.ProjectRequest;
import util.Ansi;
import util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Orchestrates the end-to-end generation flow: download the starter archive, persist it to a
 * temporary ZIP, extract it into the destination folder, and clean the ZIP up afterwards.
 *
 * <p>This ties together {@link InitializrClient} and {@link ZipExtractor} while keeping the command
 * layer free of process logic. Progress is reported via {@link Ansi} so the user sees each stage.</p>
 */
public class ProjectGenerator {

    private final InitializrClient client;
    private final ZipExtractor zipExtractor;

    public ProjectGenerator(InitializrClient client, ZipExtractor zipExtractor) {
        this.client = client;
        this.zipExtractor = zipExtractor;
    }

    /**
     * Generates the project described by {@code request} into {@code targetDir}.
     *
     * @param request   the resolved project options
     * @param targetDir the destination directory; must not already contain files
     * @param force     when true, allows extracting into a non-empty existing directory
     * @throws ValidationException if the target exists and is non-empty (and {@code force} is false)
     * @throws SpringCliException  on download or extraction failure
     */
    public void generate(ProjectRequest request, Path targetDir, boolean force) {
        if (!force && FileUtils.isNonEmptyDirectory(targetDir)) {
            throw new ValidationException(
                    "Destination '" + targetDir + "' already exists and is not empty. "
                            + "Choose another name or pass --force to overwrite.");
        }

        Ansi.info("Downloading project...");
        byte[] zipBytes = client.downloadStarter(request);

        Path tempZip = null;
        try {
            tempZip = Files.createTempFile("springcli-", ".zip");
            Files.write(tempZip, zipBytes);

            Ansi.info("Extracting files...");
            zipExtractor.extract(tempZip, targetDir);
        } catch (IOException e) {
            // Roll back a partially-written target so we don't leave a half-baked project.
            FileUtils.deleteQuietly(targetDir);
            throw new SpringCliException("Failed while writing the project to disk.", e);
        } finally {
            // Delete the downloaded ZIP after extraction, as required.
            FileUtils.deleteQuietly(tempZip);
        }
    }
}
