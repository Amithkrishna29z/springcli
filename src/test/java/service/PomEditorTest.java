package service;

import exception.SpringCliException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    private static final String PARENT_POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.8</version>
                </parent>
                <artifactId>demo</artifactId>
            </project>
            """;

    private static final String PINNED_POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <properties>
                    <jjwt.version>0.11.5</jjwt.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>1.18.30</version>
                    </dependency>
                    <dependency>
                        <groupId>io.jsonwebtoken</groupId>
                        <artifactId>jjwt-api</artifactId>
                        <version>${jjwt.version}</version>
                    </dependency>
                </dependencies>
            </project>
            """;

    @Test
    void setsADependencyVersionInPlace() {
        String updated = editor.setDependencyVersion(PINNED_POM, "org.projectlombok:lombok", "1.18.30", "1.18.38");
        assertEquals(PINNED_POM.replace("<version>1.18.30</version>", "<version>1.18.38</version>"), updated);
        assertNull(editor.setDependencyVersion(PINNED_POM, "org.projectlombok:lombok", "1.0.0", "1.18.38"));
    }

    @Test
    void readsAndSetsAProperty() {
        assertEquals("0.11.5", editor.property(PINNED_POM, "jjwt.version"));
        assertNull(editor.property(PINNED_POM, "project.version"));
        assertEquals(PINNED_POM.replace("0.11.5", "0.12.6"), editor.setProperty(PINNED_POM, "jjwt.version", "0.12.6"));
        assertNull(editor.setProperty(PINNED_POM, "missing.version", "1.0.0"));
    }

    @Test
    void setsParentVersionInPlaceLeavingEverythingElseIntact() {
        String updated = editor.setSpringBootParentVersion(PARENT_POM, "3.3.2");
        assertEquals("3.3.2", editor.springBootParentVersion(updated));
        // Surgical: only the version changed, structure/indentation preserved.
        assertEquals(PARENT_POM.replace("<version>3.2.8</version>", "<version>3.3.2</version>"), updated);
    }

    @Test
    void setParentVersionReturnsNullWhenNoSpringBootParent() {
        assertNull(editor.setSpringBootParentVersion(POM, "3.3.2"));
    }

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
    void removesMatchingDependencyPreservingTheRest() {
        String updated = editor.removeDependencies(POM,
                Set.of("org.springframework.boot:spring-boot-starter-test"));

        List<String> keys = editor.dependencies(updated).stream().map(PomEditor.Dep::key).toList();
        assertEquals(List.of("org.springframework.boot:spring-boot-starter-web"), keys);
        assertFalse(updated.contains("spring-boot-starter-test"));
        assertTrue(editor.hasSingleDependenciesSection(updated));
    }

    @Test
    void removeIgnoresDependenciesNotPresent() {
        String updated = editor.removeDependencies(POM, Set.of("com.acme:absent"));
        assertEquals(POM, updated);
    }

    @Test
    void readsSpringBootParentVersion() {
        String pom = """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.2.8</version>
                    </parent>
                </project>
                """;
        assertEquals("3.2.8", editor.springBootParentVersion(pom));
    }

    @Test
    void springBootParentVersionIsNullWhenAbsent() {
        assertNull(editor.springBootParentVersion(POM));
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
