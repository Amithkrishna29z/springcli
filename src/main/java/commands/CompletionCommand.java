package commands;

import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

/**
 * {@code springcli completion} — prints a bash/zsh tab-completion script for springcli.
 *
 * <p>The script is generated from the live command model by picocli's {@link AutoComplete}, so it
 * always reflects the current subcommands and options. Enable it for the current shell with
 * {@code source <(springcli completion)}, or install it permanently by writing the output to your
 * shell's completion directory.</p>
 */
@Command(
        name = "completion",
        description = {
                "Print a bash/zsh tab-completion script.",
                "",
                "Enable for the current shell:",
                "  source <(springcli completion)          # bash",
                "  autoload -U +X compinit && compinit; \\",
                "  autoload -U +X bashcompinit && bashcompinit; \\",
                "  source <(springcli completion)          # zsh"
        }
)
public class CompletionCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        CommandLine root = spec.root().commandLine();
        System.out.println(AutoComplete.bash(root.getCommandName(), root));
        return 0;
    }
}
