package org.imagebattle;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author KoaGex
 * 
 *         Why? Assume having two battles: A = /X/Y/Z , B = /X/Y then each should use the results of
 *         the other.
 * 
 *         How to save it? - SQLITE? only way to not always write the complete file? - CSV? might be
 *         good for export and import - SERIALIZED GRAPH? is binary, not an open format. always hold
 *         the whole graph in memory? - other graph saving format? might be helpful for compression.
 *         Or simpler: just zip.
 *
 */
public class CentralStorage {

  private Logger log = LogManager.getLogger();

  public static final String GRAPH_FILE = "mediaBattleGraph.csv";
  public static final String IGNORE_FILE = "mediaBattleIgnore.csv";
  public static final String SQLITE_FILE = "mediaBattleDatabase.sqlite";

  private final File graphFile;

  private final File ignoreFile;

  private final Database database;

  /**
   * Constructor
   * 
   * @param graphFileName
   * @param ignoreFileName
   */
  public CentralStorage(String graphFileName, String ignoreFileName, String sqliteFileName) {
    graphFile = getFile(graphFileName);
    ignoreFile = getFile(ignoreFileName);
    database = new Database(new SqliteDatabase(getFile(sqliteFileName)));
  }

  TransitiveDiGraph readGraph(File chosenDirectory, Predicate<? super File> matchesFileRegex,
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
    TransitiveDiGraph graph = new TransitiveDiGraph();
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
   * @return The whole graph with all saved edges. It does not save files without edges.
   */
  TransitiveDiGraph readGraph() {
    TransitiveDiGraph graph = new TransitiveDiGraph();

    Set<String> oldFileContent = readGraphCsv();
    oldFileContent.forEach(line -> {
      String[] split = line.split(";");
      String fromString = split[0];
      String toString = split[1];
      File from = new File(fromString);
      File to = new File(toString);

      // Adding vertexes will not create duplicates.
      graph.addVertex(from);
      graph.addVertex(to);

      graph.addEdge(from, to);
    });

    return graph;
  }

  void addEdges(TransitiveDiGraph graph) {

    /*
     * Idea: use observable list or observable set? then only add changes to the file.
     * 
     * Why not? Unecessary optimisation. Think about it again when performance issues arise.
     */

    Set<String> oldFileContent = readGraphCsv();

    Stream<String> edgesOfCurrentGraphStream = edgeLines(graph);

    String graphCsvContent = Stream.concat(oldFileContent.stream(), edgesOfCurrentGraphStream)//
        .distinct()//
        .sorted()//
        .collect(Collectors.joining(System.lineSeparator())); // FIXME heap space

    log.debug("file lines before save: {}    current edge count: {} ", oldFileContent.size(),
        graph.edgeSet().size());

    writeStringIntoFile(graphCsvContent, graphFile);
  }

  Set<File> readIgnoreFile(File chosenDirectory, Predicate<File> fileRegex, Boolean recursive) {
    Set<String> readFile = readFile(ignoreFile);

    Predicate<File> containedRecursively = file -> {
      return file.getAbsolutePath().startsWith(chosenDirectory.getAbsolutePath());
    };
    Predicate<File> containedDirectly = file -> {
      List<File> directorFiles = Arrays.asList(chosenDirectory.listFiles());
      return directorFiles.contains(file);
    };

    Predicate<File> matchesChosenDirectory = recursive ? containedRecursively : containedDirectly;

    Predicate<File> acceptFile = matchesChosenDirectory.and(fileRegex);
    Set<File> result = readFile.stream()//
        .map(File::new)//
        .filter(acceptFile)//
        .collect(Collectors.toSet());
    log.debug("count of ignored files: {}", result.size());

    return result;
  }

  Set<File> readIgnoreFile() {
    Set<String> readFile = readFile(ignoreFile);
    Set<File> queryIgnored = database.queryIgnored();

    log.debug("files in file: {}    files in db: {}", readFile.size(), queryIgnored.size());
    Set<File> result = readFile.stream()//
        .map(File::new)//
        .collect(Collectors.toSet());

    // migration
    Map<Boolean, List<File>> ignoredFilesThatAreNotInDb = result.stream()//
        .filter(file -> !queryIgnored.contains(file))//
        .filter(File::exists)//
        // .limit(20)// dont migrate everything at once
        .collect(Collectors.partitioningBy(ImageBattleApplication.imagePredicate));

    List<File> images = ignoredFilesThatAreNotInDb.get(Boolean.TRUE);
    List<File> music = ignoredFilesThatAreNotInDb.get(Boolean.FALSE);
    images.forEach(image -> database.addToIgnore(image, MediaType.IMAGE));
    music.forEach(song -> database.addToIgnore(song, MediaType.MUSIC));

    return result;
  }

  /**
   * @param fileToIgnore
   *          Delete all edges and add it to the ignore list.
   */
  void removeFromEdges(File fileToIgnore) {
    TransitiveDiGraph graph = readGraph();
    int edgesBefore = graph.edgeSet().size();
    graph.removeVertex(fileToIgnore);
    int edgesAfter = graph.edgeSet().size();

    log.debug("removed {} and with it {} edges", fileToIgnore, edgesBefore - edgesAfter);

    Stream<String> edgeLines = edgeLines(graph);

    String graphCsvContent = edgeLines.sorted()// sorted to easily spot changes with diff
        // no distinct needed because nothing was added.
        .collect(Collectors.joining(System.lineSeparator()));

    writeStringIntoFile(graphCsvContent, graphFile);
  }

  /**
   * @param newContent
   *          Replaces the old content of the file.
   * @param targetFile
   *          File will be created if necessary.
   */
  private void writeStringIntoFile(String newContent, File targetFile) {
    // Try-with automatically closes it which should also trigger flush().
    try (FileWriter fileWriter = new FileWriter(targetFile)) {
      fileWriter.write(newContent);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * TODO To remove files from the ignore list a new method will be added.
   * 
   * @param ignoredFile
   *          Each of the given files should be ignored in all coming media battles.
   */
  void addToIgnored(File ignoredFile) {
    Set<String> ignoredFiles = readFile(ignoreFile);

    String absolutePath = ignoredFile.getAbsolutePath();
    database.addToIgnore(ignoredFile, MediaType.IMAGE); // FIXME add parameter for media type
    ignoredFiles.add(absolutePath);

    String newIgnoredFiles = ignoredFiles.stream()//
        .sorted()//
        .collect(Collectors.joining(System.lineSeparator()));

    log.trace(newIgnoredFiles);
    writeStringIntoFile(newIgnoredFiles, ignoreFile);

  }

  void removeFromIgnored(File file) {
    Set<String> oldIgnoredFiles = readFile(ignoreFile); // does set mess with the ordering?

    boolean wasRemoved = oldIgnoredFiles.remove(file.getAbsolutePath());

    String newIgnoredFiles = oldIgnoredFiles.stream()//
        .distinct()//
        .sorted()//
        .collect(Collectors.joining(System.lineSeparator()));

    log.debug("file {} was removed: {}", file, wasRemoved);
    writeStringIntoFile(newIgnoredFiles, ignoreFile);

    database.removeFromIgnore(file, MediaType.IMAGE); // FIXME add parameter for media type
  }

  static File getFile(String fileName) {
    String userHome = System.getProperty("user.home");
    File userHomeDirectory = new File(userHome);
    File file = new File(userHomeDirectory, fileName);
    return file;
  }

  private Set<String> readGraphCsv() {
    return readFile(graphFile);
  }

  private Set<String> readFile(File file) {
    log.debug(file);
    Path path = Paths.get(file.getAbsolutePath());
    List<String> readAllLines = null;
    try {
      // charset to support german umlauts
      readAllLines = Files.readAllLines(path, StandardCharsets.ISO_8859_1);
    } catch (NoSuchFileException e) {
      log.debug("file {} does not exist", file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    readAllLines = readAllLines == null ? new ArrayList<>() : readAllLines;
    return new HashSet<>(readAllLines);
  }

  private Stream<String> edgeLines(TransitiveDiGraph graph) {
    Stream<String> edgesOfCurrentGraphStream = graph.edgeSet().stream()//
        .map(edge -> {
          String edgeSource = graph.getEdgeSource(edge).getAbsolutePath();
          String edgeTarget = graph.getEdgeTarget(edge).getAbsolutePath();
          return edgeSource + ";" + edgeTarget;
        });
    return edgesOfCurrentGraphStream;
  }

  List<File> getInconsistencies() {
    TransitiveDiGraph graph = readGraph();
    Set<File> vertexSet = graph.vertexSet();
    Set<File> ignoredFiles = readIgnoreFile();

    List<File> inconsistencies = ignoredFiles.stream()//
        .filter(vertexSet::contains)//
        .sorted()//
        .collect(Collectors.toList());

    log.debug("count: {}   content: {}", inconsistencies.size(), inconsistencies);

    return inconsistencies;
  }

}