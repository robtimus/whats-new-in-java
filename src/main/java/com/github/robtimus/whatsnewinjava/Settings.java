package com.github.robtimus.whatsnewinjava;

import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toSet;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import com.github.robtimus.whatsnewinjava.parser.model.JavaVersion;

@SuppressWarnings({ "nls", "javadoc" })
public final class Settings {

    private static final JavaVersion MINIMAL_JAVA_VERSION;
    private static final Set<String> PACKAGES_TO_IGNORE;

    static {
        Properties properties = new Properties();
        try (InputStream input = Settings.class.getResourceAsStream("/settings.properties")) {
            properties.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        MINIMAL_JAVA_VERSION = JavaVersion.parse(properties.getProperty("minimal-java-version"));
        PACKAGES_TO_IGNORE = unmodifiableSet(Pattern.compile("\\s*,\\s*").splitAsStream(properties.getProperty("ignore-packages"))
                .collect(toSet()));
    }

    private Settings() {
        throw new IllegalStateException("cannot create instances of " + getClass().getName());
    }

    public static JavaVersion getMinimalJavaVersion() {
        return MINIMAL_JAVA_VERSION;
    }

    public static Set<String> getPackagesToIgnore() {
        return PACKAGES_TO_IGNORE;
    }
}
