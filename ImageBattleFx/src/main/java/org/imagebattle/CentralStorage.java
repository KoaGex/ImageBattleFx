package org.imagebattle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author KoaGex
 * 
 *         Why? Assume having two battles: A = /X/Y/Z , B = /X/Y then each
 *         should use the results of the other.
 * 
 *         How to save it? - SQLITE? only way to not always write the complete
 *         file? - CSV? might be good for export and import - SERIALIZED GRAPH?
 *         is binary, not an open format. always hold the whole graph in memory?
 *         - other graph saving format? might be helpful for compression. Or
 *         simpler: just zip.
 *
 */
class CentralStorage {

    private static Logger log = LogManager.getLogger();

    private static final String GRAPH_FILE = "mediaBattleGraph.csv";
    private static final String IGNORE_FILE = "mediaBattleIgnore.csv";

    static void save(TransitiveDiGraph2 graph, Set<File> ignoredFiles) {
	/*
	 * Idea: use observable list or observable set? then only add changes to
	 * the file.
	 * 
	 * Why not? Unecessary optimisation. Think about it again when
	 * performance issues arise.
	 */

	File graphCsv = getGraphFile();

	Set<String> oldFileContent = readGraphCsv();

	Stream<String> edgesOfCurrentGraphStream = graph.edgeSet().stream()//
		.map(edge -> {
		    String edgeSource = graph.getEdgeSource(edge).getAbsolutePath();
		    String edgeTarget = graph.getEdgeTarget(edge).getAbsolutePath();
		    return edgeSource + ";" + edgeTarget;
		});

	String graphCsvContent = Stream.concat(oldFileContent.stream(), edgesOfCurrentGraphStream)//
		.distinct()//
		.sorted()//
		.collect(Collectors.joining("\n"));

	log.debug("file lines before save: {}    current edge count: {} ", oldFileContent.size(),
		graph.edgeSet().size());

	writeStringIntoFile(graphCsvContent, graphCsv);

	saveIgnoreFile(ignoredFiles);
    }

    /**
     * @param newContent
     *            Replaces the old content of the file.
     * @param targetFile
     *            File will be created if necessary.
     */
    private static void writeStringIntoFile(String newContent, File targetFile) {
	// Try-with automatically closes it which should also trigger flush().
	try (FileWriter fileWriter = new FileWriter(targetFile)) {
	    fileWriter.write(newContent);
	} catch (IOException e) {
	    throw new UncheckedIOException(e);
	}
    }

    static TransitiveDiGraph2 readGraph(File chosenDirectory, Predicate<? super File> matchesFileRegex,
	    Boolean recursive) {

	Predicate<File> containedRecursively = file -> {
	    return file.getAbsolutePath().startsWith(chosenDirectory.getAbsolutePath());
	};
	Predicate<File> containedDirectly = file -> {
	    List<File> directorFiles = Arrays.asList(chosenDirectory.listFiles());
	    return directorFiles.contains(file);
	};
	Predicate<File> matchesChosenDirectory = recursive ? containedRecursively : containedDirectly;

	Predicate<File> acceptFile = matchesChosenDirectory.and(matchesFileRegex);

	Set<String> oldFileContent = readGraphCsv();
	TransitiveDiGraph2 graph = new TransitiveDiGraph2();
	oldFileContent.forEach(line -> {
	    String[] split = line.split(";");
	    String fromString = split[0];
	    String toString = split[1];
	    File from = new File(fromString);
	    File to = new File(toString);

	    // Adding vertexes will not create duplicates.
	    if (acceptFile.test(from) && acceptFile.test(to)) {
		graph.addVertex(from);
		graph.addVertex(to);

		graph.addEdge(from, to);
	    }
	});

	log.info("node count: {}    edge count:", graph.vertexSet().size(), graph.edgeSet().size());

	return graph;
    }

    /**
     * TODO To remove files from the ignore list a new method will be added.
     * 
     * @param ignoredFiles
     *            Each of the given files should be ignored in all coming media
     *            battles.
     */
    private static void saveIgnoreFile(Set<File> ignoredFiles) {
	File ignoreFile = getIgnoreFile();
	Set<String> oldIgnoredFiles = readFile(ignoreFile);

	Stream<String> currentIgnoredAbsolutePathsStream = ignoredFiles.stream().map(File::getAbsolutePath);

	String newIgnoredFiles = Stream.concat(oldIgnoredFiles.stream(), currentIgnoredAbsolutePathsStream)//
		.distinct()//
		.sorted()//
		.collect(Collectors.joining("\n"));

	log.trace(newIgnoredFiles);
	writeStringIntoFile(newIgnoredFiles, ignoreFile);

    }

    static Set<File> readIgnoreFile(File chosenDirectory, String fileRegex, Boolean recursive) {
	// FIXME use the parameters
	File file = getIgnoreFile();
	Set<String> readFile = readFile(file);
	return readFile.stream().map(File::new).collect(Collectors.toSet());
    }

    static Set<File> readIgnoreFile() {
	File file = getIgnoreFile();
	Set<String> readFile = readFile(file);
	return readFile.stream().map(File::new).collect(Collectors.toSet());
    }

    private static File getIgnoreFile() {
	String child = IGNORE_FILE;
	File file = getFile(child);
	return file;
    }

    private static File getFile(String fileName) {
	String userHome = System.getProperty("user.home");
	File userHomeDirectory = new File(userHome);
	File file = new File(userHomeDirectory, fileName);
	return file;
    }

    private static File getGraphFile() {
	String child = GRAPH_FILE;
	return getFile(child);
    }

    private static Set<String> readGraphCsv() {

	File graphCsv = getGraphFile();

	return readFile(graphCsv);

    }

    private static Set<String> readFile(File file) {
	Set<String> oldFileContent = new HashSet<>();

	try {
	    if (!file.exists()) {
		boolean createSucces = file.createNewFile();
		log.info("Needed to create file {} and it succeeded: {}", file, createSucces);
	    }

	    try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {

		boolean keepReading = true;
		while (keepReading) {
		    String readLine = bufferedReader.readLine();
		    if (readLine == null) {
			keepReading = false;
		    } else {
			oldFileContent.add(readLine);
		    }
		}
	    }

	} catch (IOException e1) {
	    log.catching(Level.WARN, e1);
	    throw new UncheckedIOException(e1);
	}
	return oldFileContent;
    }

}
