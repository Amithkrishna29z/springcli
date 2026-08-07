package prompts;

import config.Defaults;
import model.Metadata;
import model.ProjectRequest;
import service.MetadataService;
import util.Ansi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class InteractiveWizard {

    private final MetadataService metadataService;
    private final BufferedReader in;
    private final PrintStream out;

    public InteractiveWizard(MetadataService metadataService) {
        this(metadataService,
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                System.out);
    }

    public InteractiveWizard(MetadataService metadataService, BufferedReader in, PrintStream out) {
        this.metadataService = metadataService;
        this.in = in;
        this.out = out;
    }

    public ProjectRequest run(String defaultName) {
        Metadata md = metadataService.getMetadata();

        out.println(Ansi.bold("\nConfigure your Spring Boot project\n"));

        String name = ask("Project name", defaultName != null ? defaultName : md.name().defaultValue());
        String groupId = ask("Group ID", md.groupId().defaultValue());
        String artifactId = ask("Artifact ID", name != null && !name.isBlank() ? name : md.artifactId().defaultValue());
        String defaultPkg = (groupId + "." + artifactId.replaceAll("[^a-zA-Z0-9]", "")).toLowerCase();
        String packageName = ask("Package name", defaultPkg);
        String description = ask("Description", md.description().defaultValue());

        String type = chooseType(md);
        String language = choose("Language", md.language(), md.language().defaultValue());
        String packaging = choose("Packaging", md.packaging(), md.packaging().defaultValue());
        String bootVersion = choose("Spring Boot version", md.bootVersion(), md.bootVersion().defaultValue());
        String javaVersion = choose("Java version", md.javaVersion(), md.javaVersion().defaultValue());

        List<String> dependencies = selectDependencies();

        ProjectRequest request = ProjectRequest.builder()
                .type(type)
                .language(language)
                .packaging(packaging)
                .bootVersion(bootVersion)
                .javaVersion(javaVersion)
                .groupId(groupId)
                .artifactId(artifactId)
                .name(name)
                .description(description)
                .packageName(packageName)
                .dependencies(dependencies)
                .build();

        printSummary(request);
        if (!confirm("Generate this project?", true)) {
            out.println(Ansi.yellow("Cancelled."));
            return null;
        }
        return request;
    }

    private String ask(String label, String def) {
        out.print(Ansi.cyan(label) + (def != null ? " (" + def + ")" : "") + ": ");
        out.flush();
        String line = readLine();
        return (line == null || line.isBlank()) ? def : line.trim();
    }

    private boolean confirm(String label, boolean def) {
        String hint = def ? "[Y/n]" : "[y/N]";
        out.print(Ansi.cyan(label) + " " + hint + ": ");
        out.flush();
        String line = readLine();
        if (line == null || line.isBlank()) {
            return def;
        }
        return line.trim().toLowerCase().startsWith("y");
    }

    private String chooseType(Metadata md) {
        return choose("Build tool", md.type(), md.type().defaultValue());
    }

    private String choose(String label, Metadata.SingleSelect field, String defaultId) {
        List<Metadata.Option> options = field.values();
        out.println("\n" + Ansi.bold(label) + ":");
        int defaultIndex = 1;
        for (int i = 0; i < options.size(); i++) {
            Metadata.Option o = options.get(i);
            boolean isDefault = o.id().equals(defaultId);
            if (isDefault) {
                defaultIndex = i + 1;
            }
            out.printf("  %2d) %s%s%n", i + 1, o.name(), isDefault ? Ansi.green("  (default)") : "");
        }
        while (true) {
            out.print(Ansi.cyan("Select 1-" + options.size()) + " (" + defaultIndex + "): ");
            out.flush();
            String line = readLine();
            if (line == null || line.isBlank()) {
                return options.get(defaultIndex - 1).id();
            }
            try {
                int idx = Integer.parseInt(line.trim());
                if (idx >= 1 && idx <= options.size()) {
                    return options.get(idx - 1).id();
                }
            } catch (NumberFormatException ignored) {

            }
            Ansi.warn("Please enter a number between 1 and " + options.size() + ".");
        }
    }

    private List<String> selectDependencies() {
        out.println("\n" + Ansi.bold("Dependencies"));
        out.println("Type a search term to find dependencies, select a number to toggle it on/off, "
                + "then press Enter on an empty line to finish.");

        Set<String> selected = new LinkedHashSet<>();
        for (String id : Defaults.DEPENDENCIES) {
            metadataService.findDependency(id).ifPresent(d -> selected.add(d.id()));
        }
        if (!selected.isEmpty()) {
            String names = selected.stream()
                    .map(id -> metadataService.findDependency(id).map(Metadata.Dependency::name).orElse(id))
                    .collect(java.util.stream.Collectors.joining(", "));
            out.println(Ansi.green("Pre-selected defaults: ") + names);
        }

        while (true) {
            out.print(Ansi.cyan("\nSearch dependency") + " (empty to finish): ");
            out.flush();
            String query = readLine();
            if (query == null || query.isBlank()) {
                break;
            }
            List<Metadata.Dependency> results = metadataService.searchDependencies(query.trim());
            if (results.isEmpty()) {
                Ansi.warn("No dependencies match '" + query.trim() + "'.");
                continue;
            }
            int limit = Math.min(results.size(), 15);
            out.println();
            for (int i = 0; i < limit; i++) {
                Metadata.Dependency d = results.get(i);
                String mark = selected.contains(d.id()) ? Ansi.green("✔ ") : "  ";
                out.printf("%s%2d) %s %s%n", mark, i + 1, Ansi.bold(d.name()),
                        d.description() != null ? "- " + d.description() : "");
            }
            if (results.size() > limit) {
                out.println("  ... " + (results.size() - limit) + " more. Refine your search to narrow results.");
            }
            out.print(Ansi.cyan("Toggle which numbers?") + " (comma-separated, empty to skip): ");
            out.flush();
            String picks = readLine();
            if (picks == null || picks.isBlank()) {
                continue;
            }
            for (String token : picks.split(",")) {
                try {
                    int idx = Integer.parseInt(token.trim());
                    if (idx >= 1 && idx <= limit) {
                        Metadata.Dependency d = results.get(idx - 1);
                        if (selected.remove(d.id())) {
                            Ansi.warn("Removed " + d.name());
                        } else {
                            selected.add(d.id());
                            Ansi.success("Added " + d.name());
                        }
                    }
                } catch (NumberFormatException ignored) {

                }
            }
        }
        return new ArrayList<>(selected);
    }

    private void printSummary(ProjectRequest r) {
        out.println("\n" + Ansi.bold("Project summary"));
        out.println("  Name:        " + r.name());
        out.println("  Group:       " + r.groupId());
        out.println("  Artifact:    " + r.artifactId());
        out.println("  Package:     " + r.packageName());
        out.println("  Build/Type:  " + r.type());
        out.println("  Language:    " + r.language());
        out.println("  Packaging:   " + r.packaging());
        out.println("  Boot:        " + r.bootVersion());
        out.println("  Java:        " + r.javaVersion());
        out.println("  Dependencies:" + (r.dependencies().isEmpty() ? " (none)" : " " + String.join(", ", r.dependencies())));
        out.println();
    }

    private String readLine() {
        try {
            return in.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read input.", e);
        }
    }
}
