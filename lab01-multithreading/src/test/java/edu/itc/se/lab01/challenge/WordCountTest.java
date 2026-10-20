package edu.itc.se.lab01.challenge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WordCountTest {


    @TempDir
    Path tempDir;

    @Test
    void singleAndMultipleWorkersProduceSameCounts() throws Exception {
        Files.writeString(
                tempDir.resolve("first.txt"),
                "Java concurrency Java\n"
        );

        Files.writeString(
                tempDir.resolve("second.txt"),
                "Word count concurrency\n"
        );

        WordCount singleThreaded =
                new WordCount(tempDir, 1, false, 10, 30);

        singleThreaded.run();

        Map<String, Long> singleCounts =
                singleThreaded.countsSnapshot();

        WordCount parallel =
                new WordCount(tempDir, 4, false, 10, 30);

        parallel.run();

        Map<String, Long> parallelCounts =
                parallel.countsSnapshot();

        assertEquals(singleCounts, parallelCounts);
    }
    @Test
    void tokenizeSplitsWords() {
        List<String> words = WordCount.tokenize("Java is fun");

        assertEquals(List.of("java", "is", "fun"), words);
    }

    @Test
    void tokenizeConvertsWordsToLowercase() {
        List<String> words = WordCount.tokenize("JAVA Java java");

        assertEquals(List.of("java", "java", "java"), words);
    }

    @Test
    void tokenizeHandlesPunctuation() {
        List<String> words = WordCount.tokenize("Hello, world!");

        assertEquals(List.of("hello", "world"), words);
    }
}