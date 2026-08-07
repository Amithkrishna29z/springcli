package commands;

import model.UserConfig;
import service.ConfigService;
import util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * {@code springcli config} — view or edit saved defaults used when creating new projects.
 *
 * <pre>
 *   springcli config                 show all saved defaults
 *   springcli config list            (same as above)
 *   springcli config path            print the config file location
 *   springcli config get &lt;key&gt;        print one value
 *   springcli config set &lt;key&gt; &lt;val&gt;   save a default
 *   springcli config unset &lt;key&gt;      clear a default
 * </pre>
 *
 * Keys: {@code groupId, javaVersion, language, packaging, type, dependencies}
 * ({@code dependencies} is a comma-separated list).
 */
@Command(name = "config", description = "View or edit saved defaults (~/.springcli/config.json).")
public class ConfigCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", paramLabel = "<action>",
            description = "list (default), get, set, unset or path.")
    private String action;

    @Parameters(index = "1", arity = "0..1", paramLabel = "<key>", description = "Config key.")
    private String key;

    @Parameters(index = "2", arity = "0..1", paramLabel = "<value>", description = "Value (for set).")
    private String value;

    private final ConfigService configService;

    public ConfigCommand() {
        this(new ConfigService());
    }

    public ConfigCommand(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public Integer call() {
        String act = (action == null ? "list" : action).toLowerCase();
        return switch (act) {
            case "list" -> list();
            case "path" -> path();
            case "get" -> get();
            case "set" -> set();
            case "unset" -> unset();
            default -> {
                Ansi.error("Unknown action '" + act + "'. Use list, get, set, unset or path.");
                yield 2;
            }
        };
    }

    private int list() {
        UserConfig config = configService.load();
        System.out.println(Ansi.bold("Saved defaults") + " (" + configService.path() + ")");
        boolean any = false;
        for (String k : UserConfig.KEYS) {
            String v = config.get(k);
            if (v != null) {
                System.out.printf("  %-14s %s%n", k, v);
                any = true;
            }
        }
        if (!any) {
            System.out.println("  (none set)");
        }
        return 0;
    }

    private int path() {
        System.out.println(configService.path());
        return 0;
    }

    private int get() {
        if (!requireValidKey()) {
            return 2;
        }
        String v = configService.load().get(key);
        System.out.println(v == null ? "(unset)" : v);
        return 0;
    }

    private int set() {
        if (!requireValidKey()) {
            return 2;
        }
        if (value == null) {
            Ansi.error("A value is required: springcli config set " + key + " <value>");
            return 2;
        }
        UserConfig config = configService.load();
        config.set(key, value);
        configService.save(config);
        Ansi.success("Set " + key + " = " + value);
        return 0;
    }

    private int unset() {
        if (!requireValidKey()) {
            return 2;
        }
        UserConfig config = configService.load();
        config.unset(key);
        configService.save(config);
        Ansi.success("Unset " + key);
        return 0;
    }

    private boolean requireValidKey() {
        if (key == null) {
            Ansi.error("A key is required. Valid keys: " + String.join(", ", UserConfig.KEYS));
            return false;
        }
        if (!UserConfig.isKey(key)) {
            Ansi.error("Unknown key '" + key + "'. Valid keys: " + String.join(", ", UserConfig.KEYS));
            return false;
        }
        return true;
    }
}
