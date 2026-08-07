package commands;

import service.UpdateService;
import util.Ansi;
import util.ProcessUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * {@code springcli update} — checks GitHub for a newer release and reports it. With {@code --download}
 * it fetches the installer for the current OS and opens it; the native installer then upgrades the
 * existing install in place (a running executable cannot reliably overwrite itself, so we hand off
 * to the OS installer rather than patching files directly).
 */
@Command(name = "update", description = "Check for a newer springcli release (optionally download it).")
public class UpdateCommand implements Callable<Integer> {

    @Option(names = "--download", description = "Download the installer for your OS and open it.")
    private boolean download;

    private final UpdateService updateService;

    public UpdateCommand() {
        this(new UpdateService(VersionCommand.VERSION));
    }

    public UpdateCommand(UpdateService updateService) {
        this.updateService = updateService;
    }

    @Override
    public Integer call() {
        Ansi.info("Checking for updates...");
        String current = updateService.currentVersion();
        String latest = updateService.latestVersion();

        if (!UpdateService.isNewer(latest, current)) {
            Ansi.success("You're on the latest version (" + current + ").");
            return 0;
        }

        System.out.println(Ansi.bold("A new version is available: ")
                + Ansi.green(latest) + "  (you have " + current + ")");
        String asset = assetForOs();
        System.out.println("Release notes: " + updateService.releaseUrl());
        System.out.println("Installer:     " + updateService.downloadUrl(asset));

        if (download || confirmInstall()) {
            return downloadAndOpen(updateService.downloadUrl(asset), asset);
        }
        System.out.println("\nRun " + Ansi.cyan("springcli update --download")
                + " to download and launch the installer.");
        return 0;
    }

    /** Prompts to install now when running interactively; returns false for non-TTY sessions. */
    private boolean confirmInstall() {
        if (System.console() == null) {
            return false;
        }
        System.out.print("\nDownload and install now? [y/N]: ");
        System.out.flush();
        try {
            String line = new java.io.BufferedReader(
                    new java.io.InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8)).readLine();
            return line != null && line.trim().toLowerCase(Locale.ROOT).startsWith("y");
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /** Picks the stable installer asset name for the current platform. */
    private String assetForOs() {
        if (ProcessUtils.isWindows()) {
            return "springcli-setup.exe";
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return "springcli.pkg";
        }
        return "springcli-amd64.deb";
    }

    private int downloadAndOpen(String url, String asset) {
        Ansi.info("Downloading " + asset + "...");
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            Path dir = Path.of(System.getProperty("user.home"), "Downloads");
            if (!Files.isDirectory(dir)) {
                dir = Files.createTempDirectory("springcli-update");
            }
            Path file = dir.resolve(asset);

            HttpResponse<Path> response = client.send(
                    HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "springcli").GET().build(),
                    HttpResponse.BodyHandlers.ofFile(file));
            if (response.statusCode() >= 300) {
                Ansi.error("Download failed: HTTP " + response.statusCode());
                return 1;
            }
            Ansi.success("Downloaded to " + file);
            openInstaller(file);
            System.out.println("Complete the installer to finish updating. springcli will now exit.");
            return 0;
        } catch (IOException e) {
            Ansi.error("Download failed: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Ansi.error("Download was interrupted.");
            return 1;
        }
    }

    private void openInstaller(Path file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.toFile());
                return;
            }
        } catch (Exception ignored) {
            // fall through to manual instruction
        }
        System.out.println("Open the installer manually: " + file);
    }
}
