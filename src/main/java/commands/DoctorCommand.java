package commands;

import util.Ansi;
import util.ProcessUtils;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.util.concurrent.Callable;

@Command(name = "doctor", description = "Check your environment (Java, Maven, Git).")
public class DoctorCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println(Ansi.bold("Environment check\n"));
        boolean ok = true;
        ok &= check("Java", "java", "-version");
        ok &= check("Maven", "mvn", "-v");

        check("Git", "git", "--version");

        System.out.println();
        if (ok) {
            Ansi.success("Core tooling looks good.");
            return 0;
        }
        Ansi.warn("Some required tools are missing; see above.");
        return 1;
    }

    private boolean check(String label, String... command) {
        try {
            Process p = new ProcessBuilder(ProcessUtils.platformCommand(command))
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes()).lines().findFirst().orElse("");
            int exit = p.waitFor();
            if (exit == 0) {
                Ansi.success(label + ": " + output.trim());
                return true;
            }
            Ansi.warn(label + ": found but exited with code " + exit);
            return false;
        } catch (IOException e) {
            Ansi.error(label + ": not found on PATH");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
