package org.imagebattle;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javafx.util.Pair;
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

  public static final String SQLITE_FILE = "mediaBattleDatabase.sqlite";

  private final Database database;

  /**
   * Constructor
   */
  public CentralStorage(String sqliteFileName) {
    database = new Database(new SqliteDatabase(getFile(sqliteFileName)));
  }

  TransitiveDiGraph readGraph(//
      File chosenDirectory, //
      Predicate<? super File> matchesFileRegex, //
      Boolean recursive//
  ) {

    TransitiveDiGraph graph = database.queryEdges(chosenDirectory, matchesFileRegex, recursive);
    log.info("database graph node count: {}    edge count: {}", graph.vertexSet().size(),
        graph.edgeSet().size());

    return graph;
  }

  /**
   * @return The whole graph with all saved edges. It does not save files without edges.
   */
  TransitiveDiGraph readGraph() {
    return database.queryEdges();
  }

  void addEdges(List<Pair<File, File>> newEdges) {
    for (Pair<File, File> pair : newEdges) {
      database.addEdge(pair.getKey(), pair.getValue());
    }
  }

  void addEdges(TransitiveDiGraph graph) {
    addEdges(graph.getEdgePairs());
  }

  /**
   * @param chosenDirectory
   * @param fileRegex
   * @param recursive
   * @return
   */
  Set<File> readIgnoreFile(File chosenDirectory, MediaType mediaType, Boolean recursive) {

    return database.queryIgnored(chosenDirectory, mediaType, recursive);
  }

  Set<File> readIgnoreFile() {
    return database.queryIgnored();
  }

  /**
   * @param fileToIgnore
   *          Delete all edges and add it to the ignore list.
   */
  void removeFromEdges(File fileToIgnore) {
    database.removeFromEdges(fileToIgnore);
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
    database.addToIgnore(ignoredFile);
  }

  void removeFromIgnored(File file) {
    database.removeFromIgnore(file);
  }

  static File getFile(String fileName) {
    String userHome = System.getProperty("user.home");
    File userHomeDirectory = new File(userHome);
    File file = new File(userHomeDirectory, fileName);
    return file;
  }

  /**
   * @return Files that ignored but at the same time have edges.
   */
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

  void registerFiles(Collection<File> files) {
    database.registerFiles(files);
  }
}