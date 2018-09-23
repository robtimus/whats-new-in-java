package com.github.robtimus.whatsnewinjava;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import com.github.robtimus.whatsnewinjava.domain.JavaAPI;
import com.github.robtimus.whatsnewinjava.parser.JavadocParser;

public final class JSONGenerator {

    private JSONGenerator() {
        throw new Error("cannot create instances of " + getClass().getName());
    }

    public static void main(String... args) throws IOException {

        if (args.length < 3) {
            throw new IllegalArgumentException("Invalid number of arguments");
        }

        Path rootFolder = Paths.get(args[0]);
        String javadocBaseURL = args[1];
        Path outputFile = Paths.get(args[2]);

        if (!Files.isDirectory(rootFolder)) {
            throw new IllegalArgumentException("Invalid root folder: " + args[0]);
        }

        Path outputDir = outputFile.getParent();
        if (outputDir != null) {
            Files.createDirectories(outputDir);
        }

        Set<String> packagesToIgnore = Settings.getPackagesToIgnore();

        JavaAPI javaAPI = new JavadocParser().parseJavadoc(rootFolder, packagesToIgnore, javadocBaseURL);

        try (Writer writer = Files.newBufferedWriter(outputFile)) {
            javaAPI.toJSON(writer);
        }
    }
}
