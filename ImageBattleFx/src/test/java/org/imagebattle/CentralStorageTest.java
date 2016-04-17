package org.imagebattle;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertThat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hamcrest.core.IsCollectionContaining;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNot;
import org.jgrapht.graph.DefaultEdge;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Testing {@link CentralStorage}.
 * 
 * @author KoaGex
 *
 */
public class CentralStorageTest {
  private static final Logger LOG = LogManager.getLogger();

  @Rule
  public TemporaryFolder tf = new TemporaryFolder();

  private Path graphPath = Paths.get(System.getProperty("user.home"),
      CentralStorageRule.GRAPH_FILE_TEST);
  private Path ignorePath = Paths.get(System.getProperty("user.home"),
      CentralStorageRule.IGNORE_FILE_TEST);

  @Rule
  public CentralStorageRule centralStorageRule = new CentralStorageRule();

  @Test
  public void addEdges() throws IOException {
    // prepare
    TransitiveDiGraph graph = new TransitiveDiGraph();
    File file1 = tf.newFile();
    File file2 = tf.newFile();
    graph.addVertex(file1);
    graph.addVertex(file2);
    graph.addEdge(file1, file2);
    CentralStorage centralStorage = centralStorageRule.centralStorage();

    // act
    centralStorage.addEdges(graph);

    // assert
    List<String> lines = Files.readAllLines(graphPath);
    assertThat("line count", lines.size(), is(1));

    String expectedLine = file1.getAbsolutePath() + ";" + file2.getAbsolutePath();
    assertThat("line content", lines.get(0), is(expectedLine));
  }

  @Test
  public void testReadGraph() throws IOException {
    LOG.info("read");

    // prepare
    BufferedWriter writer = Files.newBufferedWriter(graphPath, StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
    File file1 = tf.newFile("file1");
    File file2 = tf.newFile("file2");
    File file3 = tf.newFile("file3");
    writer.write(file1.getAbsolutePath() + ";" + file2.getAbsolutePath() + System.lineSeparator());
    writer.write(file1.getAbsolutePath() + ";" + file3.getAbsolutePath() + System.lineSeparator());
    writer.flush();
    writer.close();
    CentralStorage centralStorage = centralStorageRule.centralStorage();

    // act
    TransitiveDiGraph readGraph = centralStorage.readGraph();

    // test
    Set<DefaultEdge> edgeSet = readGraph.edgeSet();
    Assert.assertThat("edge count", edgeSet.size(), is(2));
    Set<File> vertexSet = readGraph.vertexSet();
    Assert.assertThat("node count", vertexSet.size(), is(3));
    assertThat("edge 1 -> 2", readGraph.containsEdge(file1, file2), is(true));
    assertThat("edge 2 -> 1", readGraph.containsEdge(file2, file1), is(false));
    assertThat("edge 1 -> 3", readGraph.containsEdge(file1, file3), is(true));
    assertThat("edge 3 -> 1", readGraph.containsEdge(file3, file1), is(false));
    assertThat("no 2-3 edge allowed", readGraph.containsAnyEdge(file2, file3), is(false));
  }

  /**
   * An absolute path to a file within the ignore file should be read.
   * 
   * @throws IOException
   */
  @Test
  public void testReadIgnoreFile() throws IOException {
    // prepare
    File file = new File("ignore_test_file.jpg");
    file = new File(file.getAbsolutePath());
    List<String> asList = Arrays.asList(file.getAbsolutePath());
    Files.write(ignorePath, asList);
    CentralStorage centralStorage = centralStorageRule.centralStorage();

    // act
    Set<File> readIgnoreFile = centralStorage.readIgnoreFile();

    // assert
    Assert.assertThat("ignoreFile does not contain the correct file", readIgnoreFile,
        hasItem(file));
    Assert.assertThat("count", readIgnoreFile.size(), IsEqual.equalTo(1));
  }

  /**
   * {@link CentralStorage#removeFromEdges(File)}
   * 
   * @throws IOException
   */
  @Test
  public void testRemoveFromEdges() throws IOException {
    // prepare
    TransitiveDiGraph graph = new TransitiveDiGraph();
    File file1 = tf.newFile();
    File file2 = tf.newFile();
    graph.addVertex(file1);
    graph.addVertex(file2);
    graph.addEdge(file1, file2);
    CentralStorage centralStorage = centralStorageRule.centralStorage();
    centralStorage.addEdges(graph);

    // act
    File fileToIgnore = file1;
    centralStorage.removeFromEdges(fileToIgnore);

    // assert
    TransitiveDiGraph readGraph = centralStorage.readGraph();
    assertThat(readGraph.vertexSet(), IsNot.not(IsCollectionContaining.hasItem(fileToIgnore)));
  }

  @Test
  public void removeFromIgnore() throws IOException {

    // prepare
    File file = tf.newFile("1.jpg");
    File file2 = tf.newFile("2.jpg");
    CentralStorage centralStorage = centralStorageRule.centralStorage();
    centralStorage.addToIgnored(file);
    centralStorage.addToIgnored(file2);

    // act
    centralStorage.removeFromIgnored(file);

    // assert
    Set<File> ignoredFiles = centralStorage.readIgnoreFile();
    assertThat(ignoredFiles.size(), is(1));
  }

  @Test
  public void readFileWithUmlauts() throws IOException {
    // prepare
    Files.write(ignorePath, "ü".getBytes());
    CentralStorage centralStorage = centralStorageRule.centralStorage();

    // act
    Set<File> set = centralStorage.readIgnoreFile();

    // assert
    File onlyFile = set.iterator().next();
    assertThat(onlyFile.getName(), is("ü"));

  }

}