package service;

import util.Ansi;
import util.ProcessUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * macOS and Linux allow replacing a running binary in place, so the platform installer is run
 * directly (under sudo) and the downloaded file is deleted once it finishes.
 */
final class UnixInstaller implements Installer {

    private final String assetName;
    private final Function<Path, String[]> command;

    private UnixInstaller(String assetName, Function<Path, String[]> command) {
        this.assetName = assetName;
        this.command = command;
    }

    static UnixInstaller macOsPkg() {
        return new UnixInstaller("springcli.pkg",
                file -> new String[]{"sudo", "installer", "-pkg", file.toString(), "-target", "/"});
    }

    static UnixInstaller debian() {
        return new UnixInstaller("springcli-amd64.deb",
                file -> new String[]{"sudo", "dpkg", "-i", file.toString()});
    }

    @Override
    public String assetName() {
        return assetName;
    }

    @Override
    public int install(Path installer) throws IOException, InterruptedException {
        Ansi.info("Installing (you may be prompted for your password)...");
        int code = ProcessUtils.runInteractive(null, command.apply(installer));
        Files.deleteIfExists(installer);
        if (code != 0) {
            Ansi.error("Installer exited with code " + code + ".");
            return 1;
        }
        Ansi.success("Updated. Restart springcli to use the new version.");
        return 0;
    }
}
