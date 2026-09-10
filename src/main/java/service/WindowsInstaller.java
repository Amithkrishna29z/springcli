package service;

import util.Ansi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Windows file-locks a running executable, so springcli can't overwrite itself while it's running.
 * The install is handed to a small detached helper script that waits for springcli.exe to exit
 * (releasing its lock), runs the Inno Setup installer silently, then deletes the installer and itself.
 */
final class WindowsInstaller implements Installer {

    private static final String EXE = "springcli.exe";

    @Override
    public String assetName() {
        return "springcli-setup.exe";
    }

    @Override
    public int install(Path installer) throws IOException {
        Path script = Files.createTempFile("springcli-update", ".cmd");
        String batch = String.join("\r\n",
                "@echo off",
                ":wait",
                "tasklist /FI \"IMAGENAME eq " + EXE + "\" | find /I \"" + EXE + "\" >nul",
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
}
