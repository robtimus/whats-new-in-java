package com.github.robtimus.whatsnewinjava;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.github.robtimus.whatsnewinjava.domain.JavaVersion;

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
        PACKAGES_TO_IGNORE = Collections.unmodifiableSet(Pattern.compile("\\s*,\\s*").splitAsStream(properties.getProperty("ignore-packages"))
                .collect(Collectors.toSet()));
    }

    private Settings() {
        throw new Error("cannot create instances of " + getClass().getName());
    }

    public static JavaVersion getMinimalJavaVersion() {
        return MINIMAL_JAVA_VERSION;
    }

    public static Set<String> getPackagesToIgnore() {
        return PACKAGES_TO_IGNORE;
    }
}
