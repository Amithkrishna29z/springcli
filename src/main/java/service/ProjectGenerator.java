package service;

import exception.SpringCliException;
import exception.ValidationException;
import model.ProjectRequest;
import util.Ansi;
import util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProjectGenerator {

    private final InitializrClient client;
    private final ZipExtractor zipExtractor;

    public ProjectGenerator(InitializrClient client, ZipExtractor zipExtractor) {
        this.client = client;
        this.zipExtractor = zipExtractor;
    }

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

            FileUtils.deleteQuietly(targetDir);
            throw new SpringCliException("Failed while writing the project to disk.", e);
        } finally {

            FileUtils.deleteQuietly(tempZip);
        }
    }
}
