package com.github.robtimus.whatsnewinjava.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.robtimus.whatsnewinjava.domain.JavaAPI;
import com.github.robtimus.whatsnewinjava.domain.JavaVersion;

public final class JavadocParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavadocParser.class);

    public JavaAPI parseJavadoc(Path rootFolder, JavaVersion minimalJavaVersion, Set<String> packagesToIgnore) throws IOException {
        Parser parser = new Parser(rootFolder, minimalJavaVersion, packagesToIgnore);
        parser.parse();
        return parser.javaAPI;
    }

    private static final class Parser {

        private static final Pattern COMMA_SPLIT_PATTERN = Pattern.compile("\\s*,\\s*");
        private static final Pattern SINCE_PATTERN = Pattern.compile("^(?:(?:JDK|J2SE|JSE)\\s*)?([\\d.u]+)$");

        private final Path rootFolder;
        private final JavaVersion minimalJavaVersion;
        private final Set<String> packagesToIgnore;

        private final JavaAPI javaAPI = new JavaAPI();

        private Parser(Path rootFolder, JavaVersion minimalJavaVersion, Set<String> packagesToIgnore) {
            this.rootFolder = rootFolder;
            this.minimalJavaVersion = minimalJavaVersion;
            this.packagesToIgnore = packagesToIgnore;
        }

        private void parse() throws IOException {
            Files.walk(rootFolder)
                    .filter(Files::isRegularFile)
                    .filter(this::isNonIgnoredPackageSummaryFile)
                    .forEach(this::handlePackageFile);
        }

        private void handlePackageFile(Path file) {
            parsePackageFile(file);
            Path packageDir = Objects.requireNonNull(file.getParent());
            try {
                Files.walk(packageDir, 1)
                        .filter(Files::isRegularFile)
                        .filter(this::isClassFile)
                        .forEach(this::handleClassFile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void parsePackageFile(Path file) {
            String packageName = extractPackageName(file);
            LOGGER.info("Processing package {}", packageName);

            LOGGER.debug("Parsing package file {}", file);

            try (InputStream input = Files.newInputStream(file)) {
                Document document = Jsoup.parse(input, "UTF-8", "");
                Element sinceTagElement = document.selectFirst("div.contentContainer > section[role=region] > dl > dt > span:contains(Since:)");
                if (sinceTagElement != null) {
                    String sinceString = extractSinceString(sinceTagElement);
                    JavaVersion since = extractJavaVersion(sinceString);
                    if (since == null) {
                        LOGGER.warn("Package {}: unexpected since: {}", packageName, sinceString);
                    } else if (since.compareTo(minimalJavaVersion) >= 0) {
                        javaAPI.addPackage(packageName, since);
                    } else {
                        LOGGER.debug("Package {}: ignoring since because it's too old: {}", packageName, sinceString);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void handleClassFile(Path file) {
            parseClassFile(file);
        }

        private void parseClassFile(Path file) {
            LOGGER.debug("Parsing class file {}", file);

            String packageName = extractPackageName(file);
            String className = extractClassName(file);

            try (InputStream input = Files.newInputStream(file)) {
                Document document = Jsoup.parse(input, "UTF-8", "");
                parseClassVersion(document, packageName, className);
                parseFieldVersions(document, packageName, className);
                parseConstructorVersions(document, packageName, className);
                parseMethodVersions(document, packageName, className);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void parseClassVersion(Document document, String packageName, String className) {
            Element sinceTagElement = document.selectFirst("div.contentContainer > div.description dl > dt > span:contains(Since:)");
            if (sinceTagElement != null) {
                String sinceString = extractSinceString(sinceTagElement);
                JavaVersion since = extractJavaVersion(sinceString);
                if (since == null) {
                    LOGGER.warn("Class {}.{}: unexpected since: {}", packageName, className, sinceString);
                } else if (since.compareTo(minimalJavaVersion) >= 0) {
                    javaAPI.addClass(packageName, className, since);
                } else {
                    LOGGER.debug("Class {}.{}: ignoring since because it's too old: {}", packageName, className, sinceString);
                }
            }
        }

        private void parseFieldVersions(Document document, String packageName, String className) {
            parseMemberVersions(document, packageName, className, "Field");
        }

        private void parseConstructorVersions(Document document, String packageName, String className) {
            parseMemberVersions(document, packageName, className, "Constructor");
        }

        private void parseMethodVersions(Document document, String packageName, String className) {
            parseMemberVersions(document, packageName, className, "Method");
        }

        private void parseMemberVersions(Document document, String packageName, String className, String memberType) {
            Element memberDetailElement = document.selectFirst("div.contentContainer > div.details section[role=region] h3:contains(" + memberType + " Detail)");
            if (memberDetailElement == null) {
                LOGGER.debug("No members of type {} defined for class {}.{}", memberType, packageName, className);
                return;
            }
            // go one level up to find the <ul> with the members
            Elements memberElements = memberDetailElement.parent().select("> ul");
            for (Element memberElement : memberElements) {
                String signature = memberElement.previousElementSibling().attr("id");
                Element sinceTagElement = memberElement.selectFirst("li dl > dt > span:contains(Since:)");
                if (sinceTagElement != null) {
                    String sinceString = extractSinceString(sinceTagElement);
                    JavaVersion since = extractJavaVersion(sinceString);
                    if (since == null) {
                        LOGGER.warn("Class {}.{}, member {}: unexpected since: {}", packageName, className, signature, sinceString);
                    } else if (since.compareTo(minimalJavaVersion) >= 0) {
                        javaAPI.addMember(packageName, className, signature, since);
                    } else {
                        LOGGER.debug("Class {}.{}, member {}: ignoring since because it's too old: {}", packageName, className, signature, sinceString);
                    }
                }
            }
        }

        private String extractPackageName(Path file) {
            Path parent = Objects.requireNonNull(file.getParent());
            return rootFolder.relativize(parent).toString().replace('\\', '/').replace('/', '.');
        }

        private String extractClassName(Path file) {
            Path fileName = Objects.requireNonNull(file.getFileName());
            return fileName.toString().replaceAll("\\.html$", "");
        }

        private String extractSinceString(Element sinceTagElement) {
            // the since tag element is the span inside the dt; we want the dt's sibling (the dd)
            Element sinceValueElement = sinceTagElement.parent().nextElementSibling();
            return sinceValueElement.text();
        }

        private JavaVersion extractJavaVersion(String since) {
            return COMMA_SPLIT_PATTERN.splitAsStream(since)
                    .map(this::safeExtractJavaVersion)
                    .filter(Objects::nonNull)
                    .findAny()
                    .orElse(null);
        }

        private JavaVersion safeExtractJavaVersion(String since) {
            Matcher matcher = SINCE_PATTERN.matcher(since);
            return matcher.matches() ? JavaVersion.parse(matcher.group(1)) : null;
        }

        private boolean isNonIgnoredPackageSummaryFile(Path file) {
            Path fileName = file.getFileName();
            return fileName != null && "package-summary.html".equals(fileName.toString()) && isNonIgnoredPackageFile(file);
        }

        private boolean isNonIgnoredPackageFile(Path file) {
            String packageName = extractPackageName(file);

            for (String packageToIgnore : packagesToIgnore) {
                if (packageName.equals(packageToIgnore) || packageName.startsWith(packageToIgnore + ".")) {
                    return false;
                }
            }

            return true;
        }

        private boolean isClassFile(Path file) {
            Path fileName = file.getFileName();
            return fileName != null && isClassFile(fileName.toString());
        }

        private boolean isClassFile(String fileName) {
            return !fileName.startsWith("package-");
        }
    }
}
