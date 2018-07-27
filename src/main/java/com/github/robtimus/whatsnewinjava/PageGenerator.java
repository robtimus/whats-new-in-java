package com.github.robtimus.whatsnewinjava;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import com.github.robtimus.whatsnewinjava.domain.JavaAPI;
import com.github.robtimus.whatsnewinjava.domain.JavaVersion;
import com.github.robtimus.whatsnewinjava.parser.JavadocParser;
import com.github.robtimus.whatsnewinjava.renderer.PageRenderer;

public final class PageGenerator {

    private PageGenerator() {
        throw new Error("cannot create instances of " + getClass().getName());
    }

    public static void main(String... args) throws IOException {

        if (args.length < 2) {
            throw new IllegalArgumentException("Invalid number of arguments");
        }

        Path rootFolder = Paths.get(args[0]);
        Path outputFile = Paths.get(args[1]);

        if (!Files.isDirectory(rootFolder)) {
            throw new IllegalArgumentException("Invalid root folder: " + args[0]);
        }

        Path outputDir = outputFile.getParent();
        if (outputDir != null) {
            Files.createDirectories(outputDir);
        }

        JavaVersion minimalJavaVersion = Settings.getMinimalJavaVersion();
        Set<String> packagesToIgnore = Settings.getPackagesToIgnore();
        String javadocBaseURL = Settings.getJavadocBaseURL();

        JavaAPI javaAPI = new JavadocParser().parseJavadoc(rootFolder, minimalJavaVersion, packagesToIgnore);

        String page = new PageRenderer(javadocBaseURL).renderPage(javaAPI);

        try (Writer writer = Files.newBufferedWriter(outputFile)) {
            writer.write(page);
        }
    }
}
