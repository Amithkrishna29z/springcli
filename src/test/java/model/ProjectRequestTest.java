package model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectRequestTest {

    @Test
    void buildRequiresBootVersion() {
        assertThrows(NullPointerException.class, () -> ProjectRequest.builder().build());
    }

    @Test
    void derivesPackageNameFromGroupAndSanitizedArtifact() {
        ProjectRequest r = ProjectRequest.builder()
                .bootVersion("3.3.2").groupId("com.acme").artifactId("my-app").build();
        assertEquals("com.acme.myapp", r.packageName());
    }

    @Test
    void explicitPackageNameIsKept() {
        ProjectRequest r = ProjectRequest.builder()
                .bootVersion("3.3.2").groupId("com.acme").artifactId("demo").packageName("com.custom.pkg").build();
        assertEquals("com.custom.pkg", r.packageName());
    }

    @Test
    void blankPackageNameFallsBackToDerived() {
        ProjectRequest r = ProjectRequest.builder()
                .bootVersion("3.3.2").groupId("com.acme").artifactId("demo").packageName("   ").build();
        assertEquals("com.acme.demo", r.packageName());
    }

    @Test
    void buildDefaultsAreApplied() {
        ProjectRequest r = ProjectRequest.builder().bootVersion("3.3.2").build();
        assertEquals("maven-project", r.type());
        assertEquals("java", r.language());
        assertEquals("jar", r.packaging());
        assertEquals("21", r.javaVersion());
        assertEquals("com.example", r.groupId());
        assertEquals("demo", r.artifactId());
    }

    @Test
    void nullDependenciesBecomeEmpty() {
        ProjectRequest r = ProjectRequest.builder().bootVersion("3.3.2").dependencies(null).build();
        assertTrue(r.dependencies().isEmpty());
    }

    @Test
    void dependenciesListIsImmutable() {
        ProjectRequest r = ProjectRequest.builder()
                .bootVersion("3.3.2").dependencies(new ArrayList<>(List.of("web"))).build();
        assertThrows(UnsupportedOperationException.class, () -> r.dependencies().add("x"));
    }

    @Test
    void mutatingSourceListDoesNotAffectRequest() {
        List<String> src = new ArrayList<>(List.of("web"));
        ProjectRequest r = ProjectRequest.builder().bootVersion("3.3.2").dependencies(src).build();
        src.add("data-jpa");
        assertEquals(List.of("web"), r.dependencies());
    }
}
