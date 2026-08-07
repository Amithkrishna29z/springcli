package service;

import exception.SpringCliException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PomEditorTest {

    private final PomEditor editor = new PomEditor();

    private static final String POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-test</artifactId>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
            </project>
            """;

    @Test
    void readsAllDependencies() {
        List<String> keys = editor.dependencies(POM).stream().map(PomEditor.Dep::key).toList();
        assertEquals(List.of(
                "org.springframework.boot:spring-boot-starter-web",
                "org.springframework.boot:spring-boot-starter-test"), keys);
    }

    @Test
    void ignoresExclusionCoordinates() {
        String pom = """
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>com.acme</groupId>
                            <artifactId>widget</artifactId>
                            <exclusions>
                                <exclusion>
                                    <groupId>commons-logging</groupId>
                                    <artifactId>commons-logging</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                    </dependencies>
                </project>
                """;
        List<PomEditor.Dep> deps = editor.dependencies(pom);
        assertEquals(1, deps.size());
        assertEquals("com.acme:widget", deps.get(0).key());
    }

    @Test
    void addsDependencyBeforeClosingTagPreservingIndent() {
        PomEditor.Dep redis = new PomEditor.Dep(
                "org.springframework.boot", "spring-boot-starter-data-redis", null, false, null);

        String updated = editor.addDependencies(POM, List.of(redis));

        assertTrue(updated.contains("        <artifactId>spring-boot-starter-data-redis</artifactId>"));
        assertTrue(updated.contains("spring-boot-starter-web"), "existing deps preserved");
        assertEquals(3, editor.dependencies(updated).size());
        // still a single, well-formed dependencies section
        assertTrue(editor.hasSingleDependenciesSection(updated));
    }

    @Test
    void carriesScopeAndOptional() {
        PomEditor.Dep pg = new PomEditor.Dep("org.postgresql", "postgresql", "runtime", false, null);
        PomEditor.Dep lombok = new PomEditor.Dep("org.projectlombok", "lombok", null, true, null);

        String block = editor.renderBlock(POM, List.of(pg, lombok));

        assertTrue(block.contains("<scope>runtime</scope>"));
        assertTrue(block.contains("<optional>true</optional>"));
    }

    @Test
    void refusesToEditWhenDependencyManagementPresent() {
        String pom = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency><groupId>a</groupId><artifactId>b</artifactId></dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency><groupId>c</groupId><artifactId>d</artifactId></dependency>
                    </dependencies>
                </project>
                """;
        assertFalse(editor.hasSingleDependenciesSection(pom));
        assertThrows(SpringCliException.class, () -> editor.addDependencies(
                pom, List.of(new PomEditor.Dep("x", "y", null, false, null))));
    }
}
