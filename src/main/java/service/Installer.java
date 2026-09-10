package service;

import util.ProcessUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Installs a downloaded springcli release. Each OS ships a different installer format and has
 * different rules about replacing a running binary, so each gets its own implementation;
 * {@link #forCurrentOs()} picks the right one.
 */
public interface Installer {

    /** @return the release asset this installer consumes, e.g. {@code springcli-setup.exe} */
    String assetName();

    /**
     * Installs {@code installerFile}, then deletes it.
     *
     * @return the exit code for the update command
     * @throws IOException if the installer can't be started
     */
    int install(Path installerFile) throws IOException, InterruptedException;

    /** @return the installer for the OS springcli is running on */
    static Installer forCurrentOs() {
        if (ProcessUtils.isWindows()) {
            return new WindowsInstaller();
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return UnixInstaller.macOsPkg();
        }
        return UnixInstaller.debian();
    }
}
