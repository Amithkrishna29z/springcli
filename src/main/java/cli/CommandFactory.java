package cli;

import commands.AddCommand;
import commands.AuditCommand;
import commands.ConfigCommand;
import commands.ListCommand;
import commands.ModifyCommand;
import commands.NewCommand;
import commands.OutdatedCommand;
import commands.RemoveCommand;
import commands.SearchCommand;
import commands.UpdateCommand;
import commands.UpgradeCommand;
import picocli.CommandLine;
import service.Installer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Picocli {@link CommandLine.IFactory} that constructor-injects services from a
 * {@link ServiceFactory}, so commands receive their dependencies instead of building them. Anything
 * not registered here (dependency-free commands, mixins, picocli internals) falls back to picocli's
 * default factory.
 */
public class CommandFactory implements CommandLine.IFactory {

    private final Map<Class<?>, Supplier<?>> constructors = new HashMap<>();

    public CommandFactory(ServiceFactory services) {
        Installer installer = Installer.forCurrentOs();

        register(NewCommand.class, () -> new NewCommand(
                services.metadataService(), services.projectGenerator(), services.configService()));
        register(AddCommand.class, () -> new AddCommand(services.dependencyResolver()));
        register(RemoveCommand.class, () -> new RemoveCommand(services.dependencyResolver()));
        register(OutdatedCommand.class, () -> new OutdatedCommand(services.metadataService()));
        register(UpgradeCommand.class, () -> new UpgradeCommand(
                services.metadataService(), services.dependencyUpgrader()));
        register(AuditCommand.class, () -> new AuditCommand(services.auditor()));
        register(SearchCommand.class, () -> new SearchCommand(services.metadataService()));
        register(ListCommand.class, () -> new ListCommand(services.metadataService()));
        register(ConfigCommand.class, () -> new ConfigCommand(services.configService()));
        register(UpdateCommand.class, () -> new UpdateCommand(services.updateService(), installer));
        register(ModifyCommand.class, () -> new ModifyCommand(services.updateService(), installer));
    }

    private <T> void register(Class<T> type, Supplier<T> constructor) {
        constructors.put(type, constructor);
    }

    @Override
    public <K> K create(Class<K> cls) throws Exception {
        Supplier<?> constructor = constructors.get(cls);
        return constructor != null ? cls.cast(constructor.get()) : CommandLine.defaultFactory().create(cls);
    }
}
