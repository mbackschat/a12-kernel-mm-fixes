package com.mgmtp.a12.kernel.mmdmjsons;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public class TestDmJsonsArtifacts {

    @Test void publicModelA12DmjsonsStayExtmpinternal() throws Exception {
        // CT_MODEL_HEADER-1 is in modelVersionsThatCanHavePublicArtifacts but NOT in the SME file.
        // For dmjsons, visibility is never PUBLIC — must stay extmpinternal.
        Path container = findPathOfJarThatContributesResourceToClasspath("extmpinternal/a12-ct_model_header-1.json");
        Set<String> actual = listJsonEntriesFromJar(container);
        var expected = List.of(
                pattern("^META-INF/.*$"),
                pattern("^extmpinternal/$"),
                pattern("^extmpinternal/a12-ct_model_header-1\\.json$"),
                pattern("^extmpinternal/a12-ct_model_header-1-\\d+\\.\\d+\\.json$"),
                pattern("^extmpinternal/a12-ct_model_header-1-\\d+\\.\\d+-previous\\.json$"),
                pattern("^extmpinternal/a12-ct_model_header-1-\\d+\\.\\d+-ER-\\d{2}\\.json$")
        );
        assertEqualWithPatterns("CT_MODEL_HEADER-1 a12 JAR", expected, actual);
    }

    @Test void smeModelA12VariantIsA12Internal() throws Exception {
        // MM_Field-1 is in the SME file but NOT in modelVersionsThatCanHavePublicArtifacts.
        // a12 variant → a12internal.
        Path container = findPathOfJarThatContributesResourceToClasspath("a12internal/a12-mm_field-1.json");
        Set<String> actual = listJsonEntriesFromJar(container);
        var expected = List.of(
                pattern("^META-INF/.*$"),
                pattern("^a12internal/$"),
                pattern("^a12internal/a12-mm_field-1\\.json$"),
                pattern("^a12internal/a12-mm_field-1-\\d+\\.\\d+\\.json$"),
                pattern("^a12internal/a12-mm_field-1-\\d+\\.\\d+-previous\\.json$"),
                pattern("^a12internal/a12-mm_field-1-\\d+\\.\\d+-ER-\\d{2}\\.json$")
        );
        assertEqualWithPatterns("MM_Field-1 a12 JAR", expected, actual);
    }

    @Test void smeModelNonA12VariantStaysExtmpinternal() throws Exception {
        // MM_Field-1 is in the SME file, but "none" variant is not in variantsForSmeDmjsons.
        // Must stay extmpinternal. Also: "none" variant has no "-previous" JSON.
        Path container = findPathOfJarThatContributesResourceToClasspath("extmpinternal/none-mm_field-1.json");
        Set<String> actual = listJsonEntriesFromJar(container);
        var expected = List.of(
                pattern("^META-INF/.*$"), // includes license files
                pattern("^extmpinternal/$"),
                pattern("^extmpinternal/none-mm_field-1\\.json$"),
                pattern("^extmpinternal/none-mm_field-1-\\d+\\.\\d+\\.json$"),
                pattern("^extmpinternal/none-mm_field-1-\\d+\\.\\d+-ER-\\d{2}\\.json$")
        );
        assertEqualWithPatterns("MM_Field-1 none JAR", expected, actual);
    }

    @Test void npmTgzContentsOfA12InternalDmjsons() throws Exception {
        String dirProp = System.getProperty("tgzDir.mmFieldA12");
        assertNotNull(dirProp, "tgzDir.mmFieldA12 system property must be set by Gradle test task");
        Path tgz = singleTgzFromDir(Paths.get(dirProp), List.of(".npmrc"));
        var actualEntries = new HashSet<>(npmTgzPaths(tgz));
        var expected = List.of(
                pattern("^package/package\\.json$"),
                pattern("^package/a12internal/a12-mm_field-1\\.json$"),
                pattern("^package/a12internal/a12-mm_field-1-\\d+\\.\\d+\\.json$"),
                pattern("^package/a12internal/a12-mm_field-1-\\d+\\.\\d+-previous\\.json$"),
                pattern("^package/a12internal/a12-mm_field-1-\\d+\\.\\d+-ER-\\d{2}\\.json$"),
                pattern("^package/LICENSE$"),
                pattern("^package/NOTICE$"),
                pattern("^package/THIRD_PARTY_NOTICES$"),
                pattern("^package/licenses/.*$")
        );
        assertEqualWithPatterns("MM_Field-1 a12 npm tgz", expected, actualEntries);
    }

    private static java.util.regex.Pattern pattern(String r) { return java.util.regex.Pattern.compile(r); }

    private static void assertEqualWithPatterns(String label, List<Pattern> expected, Set<String> actual) {
        var sortedActual = new TreeSet<>(actual);
        for (var p : expected) {
            boolean any = actual.stream().anyMatch(s -> p.matcher(s).matches());
            if (!any) fail(label + ": no entry matched regex '" + p.pattern() + "'\nGot:\n  " + String.join("\n  ", sortedActual));
        }
        var unexpected = actual.stream().filter(s -> expected.stream().noneMatch(p -> p.matcher(s).matches())).collect(Collectors.toCollection(TreeSet::new));
        if (!unexpected.isEmpty()) fail(label + ": unexpected entries:\n  " + String.join("\n  ", unexpected));
    }

    private static Path singleTgzFromDir(Path dir, List<String> toleratedExtraFiles) throws IOException {
        try (var s = Files.list(dir)) {
            var sl = s.filter(p -> !toleratedExtraFiles.contains(p.getFileName().toString())).toList();
            if(sl.size() != 1 || !sl.getFirst().toString().toLowerCase().endsWith(".tgz")) {
                fail(dir + " did not contain exactly one (.tgz) file, but " + sl + ".\nIf a stale old artifact is left over, try running gradle clean to clean it up.");
            }
            return sl.getFirst();
        }
    }

    private static Set<String> listJsonEntriesFromJar(Path jarPath) throws Exception {
        try (JarFile jf = new JarFile(jarPath.toFile())) {
            return jf.stream().map(ZipEntry::getName).collect(Collectors.toSet());
        }
    }

    private static Path findPathOfJarThatContributesResourceToClasspath(String resourcePath) throws Exception {
        Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources(resourcePath);
        if (!urls.hasMoreElements()) fail("Resource not found on classpath: " + resourcePath);
        URL url = urls.nextElement();
        if(!"jar".equals(url.getProtocol())) {
            throw new RuntimeException("resource " + resourcePath + " was expected to be found from jar but was found in something else instead, this is either a bug or the test code must be extended: " + url.getProtocol());
        }
        JarURLConnection conn = (JarURLConnection) url.openConnection();
        URL jarUrl = conn.getJarFileURL();
        return Paths.get(jarUrl.toURI());
    }

    private static List<String> npmTgzPaths(Path tgzFile) throws Exception {
        try (InputStream fis = Files.newInputStream(tgzFile);
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(fis);
             TarArchiveInputStream tis = new TarArchiveInputStream(gzis)) {
            List<String> entries = new ArrayList<>();
            TarArchiveEntry e;
            while ((e = tis.getNextEntry()) != null) {
                entries.add(e.getName());
            }
            return entries;
        }
    }
}
