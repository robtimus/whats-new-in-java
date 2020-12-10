package com.github.robtimus.whatsnewinjava.parser.model;

import static java.util.Comparator.comparing;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({ "nls", "javadoc" })
public final class JavaVersion implements Comparable<JavaVersion> {

    private static final Comparator<JavaVersion> COMPARATOR = comparing(JavaVersion::getMajor)
            .thenComparing(JavaVersion::getMinor)
            .thenComparing(JavaVersion::getPatch);

    private static final Map<String, JavaVersion> INSTANCE_CACHE = new HashMap<>();

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:[\\.u](\\d+))?\\.?");

    private final int major;
    private final int minor;
    private final int patch;
    private final String displayValue;

    private JavaVersion(int major, int minor, int patch, String displayValue) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.displayValue = displayValue;
    }

    public boolean introducedModules() {
        return major == 9 && minor == 0 && patch == 0;
    }

    public boolean hasModules() {
        return major >= 9;
    }

    private int getMajor() {
        return major;
    }

    private int getMinor() {
        return minor;
    }

    private int getPatch() {
        return patch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        JavaVersion other = (JavaVersion) o;
        return displayValue.equals(other.displayValue);
    }

    @Override
    public int hashCode() {
        return displayValue.hashCode();
    }

    @Override
    public String toString() {
        return displayValue;
    }

    @Override
    public int compareTo(JavaVersion o) {
        return COMPARATOR.compare(this, o);
    }

    public static JavaVersion parse(String since) {
        String mappedSince = since
                // a few methods were reported with version 7.0 in Java 7 and 8, change it into 7
                .replaceFirst("^7\\.0$", "7")
                .replaceFirst("^1\\.5", "5.0")
                .replaceFirst("^1\\.6", "6")
                .replaceFirst("^1\\.7", "7")
                .replaceFirst("^1\\.8", "8")
                ;

        return INSTANCE_CACHE.computeIfAbsent(mappedSince, JavaVersion::parseVersion);
    }

    private static JavaVersion parseVersion(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (matcher.matches()) {
            String majorString = matcher.group(1);
            String minorString = matcher.group(2);
            String patchString = matcher.group(3);

            int major = Integer.parseInt(majorString);
            int minor = minorString == null ? 0 : Integer.parseInt(minorString);
            int patch = patchString == null ? 0 : Integer.parseInt(patchString);

            if ((major + ".0" + minor).equals(version)) {
                // map 1.02 to 1.0.2
                patch = minor;
                minor = 0;
            }

            String displayVersion = version;
            if (major > 1) {
                // map 8.40 to 8u40 and 6.0.18 to 6u18
                if (minor != 0 && patch == 0) {
                    displayVersion = major + "u" + minor;
                } else if (minor == 0 && patch != 0) {
                    displayVersion = major + "u" + patch;
                }
            }

            return new JavaVersion(major, minor, patch, displayVersion);
        }

        throw new IllegalStateException("Unsupported since value: " + version);
    }
}
