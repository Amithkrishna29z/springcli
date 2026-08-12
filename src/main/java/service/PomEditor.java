package service;

import exception.SpringCliException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and edits a Maven {@code pom.xml}. Dependencies are <em>read</em> with a DOM parser (robust)
 * but <em>written</em> with a targeted text splice, so the rest of the file — comments, ordering and
 * indentation — is left untouched.
 *
 * <p>Only the standard generated layout is edited: a single project-level {@code <dependencies>}
 * block. If a {@code <dependencyManagement>} section (or any second {@code <dependencies>}) is
 * present, {@link #addDependencies} refuses to guess and throws, so the caller can fall back to
 * printing a copy-paste snippet instead of corrupting the file.
 */
public class PomEditor {

    private static final Pattern FIRST_DEPENDENCY_INDENT =
            Pattern.compile("\\n([ \\t]*)<dependency>");

    /** A whole {@code <dependency>…</dependency>} element, its leading indent and trailing newline. */
    private static final Pattern DEPENDENCY_BLOCK =
            Pattern.compile("(?m)^[ \\t]*<dependency>[\\s\\S]*?</dependency>[ \\t]*\\r?\\n?");

    public record Dep(String groupId, String artifactId, String scope, boolean optional, String version) {
        public String key() {
            return groupId + ":" + artifactId;
        }
    }

    /** Every {@code <dependency>} declared anywhere in the pom (order preserved). */
    public List<Dep> dependencies(String pomXml) {
        Document doc = parse(pomXml);
        NodeList nodes = doc.getElementsByTagName("dependency");
        List<Dep> deps = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            String groupId = childText(e, "groupId");
            String artifactId = childText(e, "artifactId");
            if (groupId == null || artifactId == null) {
                continue;
            }
            deps.add(new Dep(groupId, artifactId, childText(e, "scope"),
                    "true".equals(childText(e, "optional")), childText(e, "version")));
        }
        return deps;
    }

    /**
     * The Spring Boot version declared in the {@code <parent>} (the
     * {@code spring-boot-starter-parent} that manages dependency versions), or {@code null} if this
     * pom doesn't use it.
     */
    public String springBootParentVersion(String pomXml) {
        Document doc = parse(pomXml);
        NodeList parents = doc.getElementsByTagName("parent");
        for (int i = 0; i < parents.getLength(); i++) {
            Element e = (Element) parents.item(i);
            if ("spring-boot-starter-parent".equals(childText(e, "artifactId"))) {
                return childText(e, "version");
            }
        }
        return null;
    }

    /** True when the pom has exactly one {@code <dependencies>} block and can be edited safely. */
    public boolean hasSingleDependenciesSection(String pomXml) {
        return count(pomXml, "<dependencies>") == 1 && pomXml.contains("</dependencies>");
    }

    /**
     * Returns a copy of {@code pomXml} with {@code toAdd} inserted just before the closing
     * {@code </dependencies>} tag, matching the file's existing indentation.
     */
    public String addDependencies(String pomXml, List<Dep> toAdd) {
        if (!hasSingleDependenciesSection(pomXml)) {
            throw new SpringCliException(
                    "Could not safely locate a single <dependencies> section in pom.xml. "
                            + "Add the dependencies manually.");
        }
        String block = renderBlock(pomXml, toAdd);
        int close = pomXml.indexOf("</dependencies>");
        int lineStart = startOfLine(pomXml, close);
        return pomXml.substring(0, lineStart) + block + pomXml.substring(lineStart);
    }

    /**
     * Returns a copy of {@code pomXml} with every {@code <dependency>} whose {@code groupId:artifactId}
     * is in {@code keys} deleted (block, indentation and trailing newline). Everything else — comments,
     * ordering, other dependencies — is left byte-for-byte intact.
     */
    public String removeDependencies(String pomXml, Set<String> keys) {
        Matcher m = DEPENDENCY_BLOCK.matcher(pomXml);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String block = m.group();
            String key = keyOf(block);
            m.appendReplacement(sb,
                    key != null && keys.contains(key) ? "" : Matcher.quoteReplacement(block));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** {@code groupId:artifactId} of a single dependency block, or {@code null} if either is absent. */
    private static String keyOf(String block) {
        String group = firstTag(block, "groupId");
        String artifact = firstTag(block, "artifactId");
        return group == null || artifact == null ? null : group + ":" + artifact;
    }

    private static String firstTag(String xml, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">\\s*(.*?)\\s*</" + tag + ">", Pattern.DOTALL).matcher(xml);
        return m.find() ? m.group(1).trim() : null;
    }

    /** Renders {@code toAdd} as {@code <dependency>} XML, indented to match the given pom. */
    public String renderBlock(String pomXml, List<Dep> toAdd) {
        String childIndent = detectChildIndent(pomXml);
        String unit = childIndent.contains("\t") ? "\t" : "    ";
        String grandIndent = childIndent + unit;

        StringBuilder sb = new StringBuilder();
        for (Dep d : toAdd) {
            sb.append(childIndent).append("<dependency>\n");
            sb.append(grandIndent).append("<groupId>").append(d.groupId()).append("</groupId>\n");
            sb.append(grandIndent).append("<artifactId>").append(d.artifactId()).append("</artifactId>\n");
            if (d.version() != null) {
                sb.append(grandIndent).append("<version>").append(d.version()).append("</version>\n");
            }
            if (d.scope() != null) {
                sb.append(grandIndent).append("<scope>").append(d.scope()).append("</scope>\n");
            }
            if (d.optional()) {
                sb.append(grandIndent).append("<optional>true</optional>\n");
            }
            sb.append(childIndent).append("</dependency>\n");
        }
        return sb.toString();
    }

    private static String detectChildIndent(String pomXml) {
        Matcher m = FIRST_DEPENDENCY_INDENT.matcher(pomXml);
        if (m.find()) {
            return m.group(1);
        }
        int close = pomXml.indexOf("</dependencies>");
        String closeIndent = close < 0 ? "" : pomXml.substring(startOfLine(pomXml, close), close);
        String unit = closeIndent.contains("\t") ? "\t" : "    ";
        return closeIndent + unit;
    }

    private static int startOfLine(String text, int index) {
        int i = index;
        while (i > 0 && text.charAt(i - 1) != '\n') {
            i--;
        }
        return i;
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    /** Text of the first direct child element named {@code tag}, trimmed; {@code null} if absent. */
    private static String childText(Element parent, String tag) {
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(tag)) {
                String text = n.getTextContent();
                return text == null ? null : text.trim();
            }
        }
        return null;
    }

    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new SpringCliException("Could not parse pom.xml as XML: " + e.getMessage(), e);
        }
    }
}
