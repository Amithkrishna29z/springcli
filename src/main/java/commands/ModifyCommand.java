package commands;

import service.UpdateService;
import util.Ansi;
import picocli.CommandLine.Command;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * {@code springcli modify} — manage this installation. It reports how springcli is installed
 * (version and executable location) and then runs the update-to-latest flow, delegating to
 * {@link UpdateCommand} so the check/confirm/install behaviour stays in one place.
 */
@Command(name = "modify", description = "Manage this installation (show info and update to the latest version).")
public class ModifyCommand implements Callable<Integer> {

    private final UpdateService updateService;

    public ModifyCommand() {
        this(new UpdateService(VersionCommand.VERSION));
    }

    public ModifyCommand(UpdateService updateService) {
        this.updateService = updateService;
    }

    @Override
    public Integer call() {
        System.out.println(Ansi.bold("springcli installation"));
        System.out.println("  Version:   " + updateService.currentVersion());
        installLocation().ifPresent(loc -> System.out.println("  Installed: " + loc));
        System.out.println();

        // The only management action is "update to latest" — reuse the update command's flow.
        return new UpdateCommand(updateService).call();
    }

    /** @return the executable that launched this process (the springcli launcher or java), if known. */
    private Optional<String> installLocation() {
        try {
            return ProcessHandle.current().info().command();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
