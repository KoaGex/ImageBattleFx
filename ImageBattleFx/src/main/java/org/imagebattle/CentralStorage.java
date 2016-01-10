package org.imagebattle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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

  private final File graphFile;

  private final File ignoreFile;

  public CentralStorage(String graphFileName, String ignoreFileName) {
    graphFile = getFile(graphFileName);
    ignoreFile = getFile(ignoreFileName);
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
        .collect(Collectors.joining(System.lineSeparator()));

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
    return readFile.stream().map(File::new).collect(Collectors.toSet());
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
   * @param ignoredFiles
   *          Each of the given files should be ignored in all coming media battles.
   */
  void addToIgnored(File ignoredFile) {
    Set<String> ignoredFiles = readFile(ignoreFile);

    ignoredFiles.add(ignoredFile.getAbsolutePath());

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

  public static String fileContentHash(File file) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      FileInputStream fis = new FileInputStream(file);
      java.security.DigestInputStream dis = new DigestInputStream(fis, md);
      BufferedReader br = new BufferedReader(new InputStreamReader(dis));
      br.lines().count();

      byte[] digest = md.digest();
      StringBuilder sb1 = new StringBuilder();
      StringBuilder sb2 = new StringBuilder();
      for (byte b : digest) {
        String hexString1 = Integer.toHexString(b);
        sb1.append(hexString1);
        String hexString2 = Integer.toHexString(0xff & b);
        sb2.append(hexString2);
      }
      return sb2.toString();

    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }

  }

  /**
   * @param databaseFile
   *          File that should not be a directory. If it does not exist it will be created.
   * @return {@link Connection} to the database file. It is open and can be used.
   */
  public static Connection getSqliteConnection(File databaseFile) {

    // TODO if file does not exist, it needs to be created with all tables
    Connection connection = null;
    try {
      Class.forName("org.sqlite.JDBC");
      connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    } catch (SQLException e) {
      System.err.println(e.getClass().getName() + ": " + e.getMessage());
    } catch (ClassNotFoundException e) {
      System.err.println(e.getClass().getName() + ": " + e.getMessage());
    }
    System.out.println("Opened database successfully");
    return connection;
  }

  /**
   * This table stores the hashes of files. The table storing the edges will use the integer ids of
   * this table.
   * 
   * @param connection
   *          Use {@link #getSqliteConnection(File)}.
   */
  public static void createMediaObjectsTable(Connection connection) {
    String createTable = " create table media_objects("
        + "id INTEGER PRIMARY KEY, hash TEXT, media_type TEXT) ";
    executeSql(connection, createTable);
  }

  /**
   * Depends on {@link #createMediaObjectsTable(Connection)}.
   * 
   * @param connection
   *          Use {@link #getSqliteConnection(File)}.
   */
  public static void createFilesTable(Connection connection) {
    String createTable = " create table files(" + //
        " media_object INTEGER NON NULL," + //
        " absolute_path TEXT," + //
        " FOREIGN KEY(media_object) REFERENCES media_objects(id)  ) ";
    executeSql(connection, createTable);
  }

  /**
   * @param connection
   *          Use {@link #getSqliteConnection(File)}.
   * @param sql
   *          Any sql statement that you want to be executed and don't expect an result from.
   */
  public static void executeSql(Connection connection, String sql) {
    try {
      Statement statement = connection.createStatement();
      statement.execute(sql);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

}