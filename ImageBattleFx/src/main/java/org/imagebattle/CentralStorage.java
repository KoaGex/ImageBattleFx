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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
public class CentralStorage {
    private static Logger log = LogManager.getLogger();

    static void save(TransitiveDiGraph2 graph, Set<File> ignoredFiles) {
	// TODO idea: use observable list or observable set? then only add
	// changes to the file.
	String userHome = System.getProperty("user.home");
	File userHomeDirectory = new File(userHome);
	File graphCsv = new File(userHomeDirectory, "mediaBattleGraph.csv");

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

	// Try-with automatically closes it which should also trigger flush().
	try (FileWriter fileWriter = new FileWriter(graphCsv)) {
	    fileWriter.write(graphCsvContent);
	} catch (IOException e) {
	    throw new UncheckedIOException(e);
	}

    }

    static TransitiveDiGraph2 read(File chosenDirectory, String fileRegex, Boolean recursive) {

	Predicate<File> containedRecursively = file -> {
	    return file.getAbsolutePath().startsWith(chosenDirectory.getAbsolutePath());
	};
	Predicate<File> containedDirectly = file -> {
	    List<File> directorFiles = Arrays.asList(chosenDirectory.listFiles());
	    return directorFiles.contains(file);
	};
	Predicate<File> matchesChosenDirectory = recursive ? containedRecursively : containedDirectly;

	Predicate<File> matchesFileRegex = file -> {
	    Pattern pattern = Pattern.compile(fileRegex);
	    return pattern.matcher(file.getName().toUpperCase()).matches();
	};

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

    private static Set<String> readGraphCsv() {

	String userHome = System.getProperty("user.home");
	File userHomeDirectory = new File(userHome);
	File graphCsv = new File(userHomeDirectory, "mediaBattleGraph.csv");

	Set<String> oldFileContent = new HashSet<>();
	try (BufferedReader bufferedReader = new BufferedReader(new FileReader(graphCsv))) {

	    boolean keepReading = true;
	    while (keepReading) {
		String readLine = bufferedReader.readLine();
		if (readLine == null) {
		    keepReading = false;
		} else {
		    oldFileContent.add(readLine);
		}
	    }

	} catch (IOException e1) {
	    throw new UncheckedIOException(e1);
	}
	return oldFileContent;
    }

}
