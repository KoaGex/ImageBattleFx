package org.imagebattle;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hamcrest.core.IsCollectionContaining;
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

}