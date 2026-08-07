package model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataModelTest {

    @Test
    void textFieldOrDefaultHandlesNullAndBlank() {
        Metadata.TextField f = new Metadata.TextField("com.example");
        assertEquals("com.example", f.orDefault(null));
        assertEquals("com.example", f.orDefault("   "));
        assertEquals("io.acme", f.orDefault("io.acme"));
    }

    @Test
    void singleSelectValidatesMembership() {
        Metadata.SingleSelect s = new Metadata.SingleSelect("jar",
                List.of(new Metadata.Option("jar", "Jar"), new Metadata.Option("war", "War")));
        assertTrue(s.isValid("war"));
        assertFalse(s.isValid("ear"));
    }

    @Test
    void singleSelectNullValuesBecomeEmptyAndInvalid() {
        Metadata.SingleSelect s = new Metadata.SingleSelect("x", null);
        assertTrue(s.values().isEmpty());
        assertFalse(s.isValid("x"));
    }

    @Test
    void dependencyContainersTreatNullValuesAsEmpty() {
        assertTrue(new Metadata.DependencyGroup("group", null).values().isEmpty());
        assertTrue(new Metadata.DependencyGroupContainer(null).values().isEmpty());
    }

    @Test
    void optionExposesIdAndName() {
        Metadata.Option o = new Metadata.Option("web", "Spring Web");
        assertEquals("web", o.id());
        assertEquals("Spring Web", o.name());
    }
}
