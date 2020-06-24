package com.github.robtimus.whatsnewinjava;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import com.github.robtimus.whatsnewinjava.parser.JavadocParser;
import com.github.robtimus.whatsnewinjava.parser.model.JavaAPI;

public final class JSONGenerator {

    private JSONGenerator() {
        throw new IllegalStateException("cannot create instances of " + getClass().getName());
    }

    public static void main(String... args) throws IOException {

        if (args.length < 3) {
            throw new IllegalArgumentException("Invalid number of arguments");
        }

        Path rootFolder = Paths.get(args[0]);
        String javadocBaseURL = args[1];
        Path outputDir = Paths.get(args[2]);
        int javaVersion = Integer.parseInt(args[3]);

        if (!Files.isDirectory(rootFolder)) {
            throw new IllegalArgumentException("Invalid root folder: " + args[0]);
        }

        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("java-" + javaVersion + ".json");

        Set<String> packagesToIgnore = Settings.getPackagesToIgnore();

        JavaAPI javaAPI = new JavadocParser().parseJavadoc(rootFolder, packagesToIgnore, javadocBaseURL, javaVersion);

        try (Writer writer = Files.newBufferedWriter(outputFile)) {
            javaAPI.toJSON(writer);
        }
    }
}
