package commands;

import exception.SpringCliException;
import util.Strings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Parses the {@code <deps>} arguments of {@code add}/{@code remove}: space or comma separated, de-duplicated. */
final class DependencyArgs {

    private DependencyArgs() {
    }

    static List<String> parse(List<String> args, String commandName) {
        Set<String> ids = new LinkedHashSet<>();
        for (String arg : args) {
            ids.addAll(Strings.splitCsv(arg));
        }
        if (ids.isEmpty()) {
            throw new SpringCliException(
                    "No dependency ids given. Example: springcli " + commandName + " web data-jpa");
        }
        return new ArrayList<>(ids);
    }
}
