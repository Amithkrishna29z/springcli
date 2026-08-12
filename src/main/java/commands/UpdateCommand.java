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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * {@code springcli update} — checks GitHub for a newer release and reports it. With {@code --download}
 * it fetches the installer for the current OS, runs it silently to upgrade in place, and deletes the
 * downloaded installer once it finishes.
 *
 * <p>On Windows a running executable is file-locked, so springcli cannot overwrite itself while it is
 * still running. We therefore hand the install off to a small detached helper script that waits for
 * this process to exit, runs the Inno Setup installer silently, then removes the installer and itself.
 * On macOS/Linux the running binary can be replaced in place, so the OS installer is invoked directly
 * and the file deleted afterwards.
 */
@Command(name = "update", description = "Check for a newer springcli release (optionally download and install it).")
public class UpdateCommand implements Callable<Integer> {

    @Option(names = "--download", description = "Download the installer for your OS, install it, and clean up.")
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
            return downloadAndInstall(updateService.downloadUrl(asset), asset);
        }
        System.out.println("\nRun " + Ansi.cyan("springcli update --download")
                + " to download and install it automatically.");
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

    private int downloadAndInstall(String url, String asset) {
        Ansi.info("Downloading " + asset + "...");
        Path file;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            file = Files.createTempDirectory("springcli-update").resolve(asset);
            HttpResponse<Path> response = client.send(
                    HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "springcli").GET().build(),
                    HttpResponse.BodyHandlers.ofFile(file));
            if (response.statusCode() >= 300) {
                Ansi.error("Download failed: HTTP " + response.statusCode());
                return 1;
            }
            Ansi.success("Downloaded to " + file);
        } catch (IOException e) {
            Ansi.error("Download failed: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Ansi.error("Download was interrupted.");
            return 1;
        }

        try {
            if (ProcessUtils.isWindows()) {
                return installWindows(file);
            }
            return installUnix(file, asset);
        } catch (IOException e) {
            Ansi.error("Could not start the installer: " + e.getMessage());
            openInstaller(file);
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Ansi.error("Install was interrupted.");
            return 1;
        }
    }

    /**
     * Launches a detached helper that waits for this springcli.exe to exit (releasing its file lock),
     * runs the Inno Setup installer silently, then deletes the installer and the helper itself.
     */
    private int installWindows(Path installer) throws IOException {
        String exe = "springcli.exe";
        Path script = Files.createTempFile("springcli-update", ".cmd");
        String batch = String.join("\r\n",
                "@echo off",
                ":wait",
                "tasklist /FI \"IMAGENAME eq " + exe + "\" | find /I \"" + exe + "\" >nul",
                "if not errorlevel 1 (",
                "  timeout /t 1 /nobreak >nul",
                "  goto wait",
                ")",
                "\"" + installer + "\" /VERYSILENT /SUPPRESSMSGBOXES /NORESTART",
                "del /f /q \"" + installer + "\"",
                "del /f /q \"%~f0\"",
                "");
        Files.writeString(script, batch, StandardCharsets.UTF_8);

        new ProcessBuilder("cmd.exe", "/c", "start", "", "/min", script.toString()).start();
        System.out.println("Installing " + Ansi.green("in the background") + " once springcli exits"
                + " (approve the UAC prompt if asked). springcli will now exit.");
        return 0;
    }

    /**
     * Runs the platform installer directly and waits for it to finish, then removes the file. Unix
     * allows replacing a running binary in place, so no detached helper is needed.
     */
    private int installUnix(Path installer, String asset) throws IOException, InterruptedException {
        List<String> command = asset.endsWith(".pkg")
                ? List.of("sudo", "installer", "-pkg", installer.toString(), "-target", "/")
                : List.of("sudo", "dpkg", "-i", installer.toString());

        Ansi.info("Installing (you may be prompted for your password)...");
        int code = new ProcessBuilder(command).inheritIO().start().waitFor();
        Files.deleteIfExists(installer);
        if (code != 0) {
            Ansi.error("Installer exited with code " + code + ".");
            return 1;
        }
        Ansi.success("Updated. Restart springcli to use the new version.");
        return 0;
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
