package com.github.robtimus.whatsnewinjava;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NavigableMap;
import com.github.robtimus.whatsnewinjava.domain.JavaAPI;
import com.github.robtimus.whatsnewinjava.domain.JavaVersion;
import com.github.robtimus.whatsnewinjava.renderer.PageRenderer;

public final class PageGenerator {

    private PageGenerator() {
        throw new Error("cannot create instances of " + getClass().getName());
    }

    public static void main(String... args) throws IOException {

        if (args.length < 3) {
            throw new IllegalArgumentException("Invalid number of arguments");
        }

        Path rootFolder = Paths.get(args[0]);
        Path newOutputFile = Paths.get(args[1]);
        Path deprecatedOutputFile = Paths.get(args[2]);
        Path removedOutputFile = Paths.get(args[3]);

        if (!Files.isDirectory(rootFolder)) {
            throw new IllegalArgumentException("Invalid root folder: " + args[0]);
        }

        createParentDirectories(newOutputFile);
        createParentDirectories(deprecatedOutputFile);
        createParentDirectories(removedOutputFile);

        NavigableMap<JavaVersion, JavaAPI> javaAPIs = JavaAPI.allFromJSON(rootFolder);

        PageRenderer pageRenderer = new PageRenderer();
        JavaVersion minimalJavaVersion = Settings.getMinimalJavaVersion();

        String deprecatedPage = pageRenderer.renderDeprecatedPage(javaAPIs);
        try (Writer writer = Files.newBufferedWriter(deprecatedOutputFile)) {
            writer.write(deprecatedPage);
        }

        String removedPage = pageRenderer.renderRemovedPage(javaAPIs);
        try (Writer writer = Files.newBufferedWriter(removedOutputFile)) {
            writer.write(removedPage);
        }

        JavaAPI javaAPI = javaAPIs.lastEntry().getValue();
        javaAPI.retainSince(minimalJavaVersion);

        String newPage = pageRenderer.renderNewPage(javaAPI);
        try (Writer writer = Files.newBufferedWriter(newOutputFile)) {
            writer.write(newPage);
        }
    }

    private static void createParentDirectories(Path outputFile) throws IOException {
        Path outputDir = outputFile.getParent();
        if (outputDir != null) {
            Files.createDirectories(outputDir);
        }
    }
}
