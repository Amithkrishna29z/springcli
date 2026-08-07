package prompts;

import model.ProjectRequest;
import model.UserConfig;
import org.junit.jupiter.api.Test;
import service.MetadataService;
import support.SampleMetadata;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractiveWizardTest {

    /** Runs the wizard with scripted input lines and a discarded output stream. */
    private ProjectRequest run(String defaultName, String... lines) {
        return run(new UserConfig(), defaultName, lines);
    }

    private ProjectRequest run(UserConfig config, String defaultName, String... lines) {
        MetadataService md = SampleMetadata.service();
        BufferedReader in = new BufferedReader(new StringReader(String.join("\n", lines) + "\n"));
        PrintStream out = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        return new InteractiveWizard(md, config, in, out).run(defaultName);
    }

    @Test
    void allDefaultsProduceExpectedRequestWithDefaultDependencies() {
        ProjectRequest r = run("myapp",
                "", "", "", "", "",   // name, group, artifact, package, description
                "", "", "", "", "",   // type, language, packaging, boot, java
                "",                    // finish dependency search
                "");                   // confirm -> default (yes)

        assertNotNull(r);
        assertEquals("myapp", r.name());
        assertEquals("com.example", r.groupId());
        assertEquals("myapp", r.artifactId());
        assertEquals("com.example.myapp", r.packageName());
        assertEquals("maven-project", r.type());
        assertEquals("java", r.language());
        assertEquals("jar", r.packaging());
        assertEquals("3.3.2", r.bootVersion());
        assertEquals("21", r.javaVersion());
        assertEquals(List.of("web", "data-jpa", "devtools", "validation", "lombok"), r.dependencies());
    }

    @Test
    void togglingRemovesAPreselectedDefault() {
        ProjectRequest r = run("myapp",
                "", "", "", "", "",
                "", "", "", "", "",
                "web",   // search
                "1",     // toggle "Spring Web" (was preselected) -> off
                "",      // finish
                "");     // confirm

        assertNotNull(r);
        assertFalse(r.dependencies().contains("web"));
        assertTrue(r.dependencies().containsAll(List.of("data-jpa", "devtools", "validation", "lombok")));
    }

    @Test
    void addingANonDefaultDependency() {
        ProjectRequest r = run("myapp",
                "", "", "", "", "",
                "", "", "", "", "",
                "postgres",  // matches postgresql
                "1",         // add it
                "",
                "");

        assertNotNull(r);
        assertTrue(r.dependencies().contains("postgresql"));
    }

    @Test
    void decliningConfirmationReturnsNull() {
        ProjectRequest r = run("myapp",
                "", "", "", "", "",
                "", "", "", "", "",
                "",    // finish deps
                "n");  // decline

        assertNull(r);
    }

    @Test
    void invalidChoiceRepromptsUntilValid() {
        ProjectRequest r = run("myapp",
                "",          // name
                "",          // group
                "",          // artifact
                "",          // package
                "",          // description
                "",          // type -> default
                "9", "2",    // language: out-of-range, then Kotlin
                "",          // packaging
                "",          // boot
                "",          // java
                "",          // finish deps
                "");         // confirm

        assertNotNull(r);
        assertEquals("kotlin", r.language());
    }

    @Test
    void savedConfigDefaultsAreUsedWhenUserAcceptsDefaults() {
        UserConfig config = new UserConfig();
        config.setGroupId("io.acme");
        config.setJavaVersion("17");
        config.setLanguage("kotlin");
        config.setDependencies(List.of("web", "validation"));

        ProjectRequest r = run(config, "myapp",
                "", "", "", "", "",   // name, group, artifact, package, description
                "", "", "", "", "",   // type, language, packaging, boot, java
                "",                    // finish deps
                "");                   // confirm

        assertNotNull(r);
        assertEquals("io.acme", r.groupId());
        assertEquals("17", r.javaVersion());
        assertEquals("kotlin", r.language());
        assertEquals(List.of("web", "validation"), r.dependencies());
    }

    @Test
    void customInputsAreUsed() {
        ProjectRequest r = run("myapp",
                "cool-app",         // name
                "io.acme",          // group
                "cool-app",         // artifact
                "io.acme.coolapp",  // package
                "My description",   // description
                "2",               // type -> gradle-project
                "",                // language -> default java
                "2",               // packaging -> war
                "2",               // boot -> 3.2.8
                "1",               // java -> 17
                "",                // finish deps
                "y");              // confirm

        assertNotNull(r);
        assertEquals("cool-app", r.name());
        assertEquals("io.acme", r.groupId());
        assertEquals("io.acme.coolapp", r.packageName());
        assertEquals("gradle-project", r.type());
        assertEquals("java", r.language());
        assertEquals("war", r.packaging());
        assertEquals("3.2.8", r.bootVersion());
        assertEquals("17", r.javaVersion());
    }
}
