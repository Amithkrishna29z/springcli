package commands;

import exception.SpringCliException;
import service.Installer;
import service.UpdateService;
import util.Ansi;
import util.Versions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * {@code springcli update} — checks GitHub for a newer release and reports it. With {@code --download}
 * (or after confirming the prompt) it downloads the installer for the current OS and hands it to that
 * OS's {@link Installer}, which upgrades in place and removes the download.
 */
@Command(name = "update", description = "Check for a newer springcli release (optionally download and install it).")
public class UpdateCommand implements Callable<Integer> {

    @Option(names = "--download", description = "Download the installer for your OS, install it, and clean up.")
    private boolean download;

    private final UpdateService updateService;
    private final Installer installer;

    public UpdateCommand(UpdateService updateService, Installer installer) {
        this.updateService = updateService;
        this.installer = installer;
    }

    @Override
    public Integer call() {
        Ansi.info("Checking for updates...");
        String current = updateService.currentVersion();
        String latest = updateService.latestVersion();

        if (!Versions.isNewer(latest, current)) {
            Ansi.success("You're on the latest version (" + current + ").");
            return 0;
        }

        System.out.println(Ansi.bold("A new version is available: ")
                + Ansi.green(latest) + "  (you have " + current + ")");
        System.out.println("Release notes: " + updateService.releaseUrl());
        System.out.println("Installer:     " + updateService.downloadUrl(installer.assetName()));

        if (download || confirmInstall()) {
            return downloadAndInstall();
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
            String line = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
            return line != null && line.trim().toLowerCase(Locale.ROOT).startsWith("y");
        } catch (IOException e) {
            return false;
        }
    }

    private int downloadAndInstall() {
        String asset = installer.assetName();
        Ansi.info("Downloading " + asset + "...");
        Path file;
        try {
            file = updateService.downloadInstaller(asset);
        } catch (SpringCliException e) {
            Ansi.error("Download failed: " + e.getMessage());
            return 1;
        }
        Ansi.success("Downloaded to " + file);

        try {
            return installer.install(file);
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
