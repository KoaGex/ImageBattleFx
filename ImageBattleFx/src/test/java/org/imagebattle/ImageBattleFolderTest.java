package org.imagebattle;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.hamcrest.core.IsNot;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ImageBattleFolderTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public CentralStorageRule centralStorageRule = new CentralStorageRule();

  @Test
  public void constructorEdge() throws IOException {
    // prepare
    TransitiveDiGraph graph = new TransitiveDiGraph();
    File fileWinner = temporaryFolder.newFile();
    File fileLoser = temporaryFolder.newFile();
    graph.addVertex(fileWinner);
    graph.addVertex(fileLoser);
    graph.addEdge(fileWinner, fileLoser);
    CentralStorage centralStorage = centralStorageRule.centralStorage();
    centralStorage.addEdges(graph);

    File root = temporaryFolder.getRoot();
    Boolean recursive = true;
    // act
    ImageBattleFolder folder = new ImageBattleFolder(centralStorage, root, file -> true, recursive);

    // assert
    List<ResultListEntry> resultList = folder.getResultList();
    ResultListEntry winnerEntry = resultList.get(0);
    assertThat(winnerEntry.wins, is(1));

    assertThat(resultList.size(), is(2));
  }

  @Test
  public void constructorIgnore() throws IOException {
    // prepare
    CentralStorage centralStorage = centralStorageRule.centralStorage();

    File fileIgnored = temporaryFolder.newFile();
    centralStorage.addToIgnored(fileIgnored);

    File root = temporaryFolder.getRoot();
    Boolean recursive = true;
    // act
    ImageBattleFolder folder = new ImageBattleFolder(centralStorage, root, file -> true, recursive);

    // assert
    List<ResultListEntry> resultList = folder.getResultList();
    ResultListEntry winnerEntry = resultList.get(0);
    assertThat(winnerEntry.ignored, is(true));

    assertThat(resultList.size(), is(1));
  }

  @Test
  public void makeDecision() throws IOException {
    // prepare
    // new ImageBattleFolder(chosenDirectory, fileRegex, recursive)
    File root = temporaryFolder.getRoot();
    File fileWinner = temporaryFolder.newFile();
    File fileLoser = temporaryFolder.newFile();
    Boolean recursive = false;
    CentralStorage centralStorage = centralStorageRule.centralStorage();
    ImageBattleFolder folder = new ImageBattleFolder(centralStorage, root, file -> true, recursive);

    // act
    folder.makeDecision(fileWinner, fileLoser);

    // assert
    List<ResultListEntry> resultList = folder.getResultList();
    ResultListEntry winnerEntry = resultList.get(0);
    assertThat("winner place", winnerEntry.file, is(fileWinner));
    assertThat("winner wins", winnerEntry.wins, is(1));

    ResultListEntry loserEntry = resultList.get(1);
    assertThat("loser place", loserEntry.file, is(fileLoser));
    assertThat("loser loses", loserEntry.loses, is(1));

  }

  @Test
  public void fixInconsistenceByDecision() throws IOException {
    // prepare
    File fileWinner = temporaryFolder.newFile();
    File fileLoser = temporaryFolder.newFile();
    File fileInconsistend = temporaryFolder.newFile();
    TransitiveDiGraph graph = new TransitiveDiGraph();
    graph.addVertex(fileWinner);
    graph.addVertex(fileLoser);
    graph.addVertex(fileInconsistend);
    graph.addEdge(fileInconsistend, fileLoser);
    CentralStorage centralStorage = centralStorageRule.centralStorage();
    centralStorage.addEdges(graph);
    centralStorage.addToIgnored(fileInconsistend);

    File root = temporaryFolder.getRoot();
    Boolean recursive = true;
    ImageBattleFolder folder = new ImageBattleFolder(centralStorage, root, file -> true, recursive);

    // act
    folder.makeDecision(fileWinner, fileInconsistend);

    // assert
    Set<File> ignoreFiles = centralStorage.readIgnoreFile();
    assertThat(ignoreFiles, IsNot.not(hasItem(fileInconsistend)));

  }

  @Test
  public void testIgnoreFile() throws IOException {
    // prepare
    File root = temporaryFolder.getRoot();
    File file = temporaryFolder.newFile();
    Boolean recursive = false;
    CentralStorage centralStorage = centralStorageRule.centralStorage();
    ImageBattleFolder folder = new ImageBattleFolder(centralStorage, root, f -> true, recursive);

    // act
    folder.ignoreFile(file);

    // assert
    List<ResultListEntry> resultList = folder.getResultList();
    ResultListEntry entry = resultList.get(0);
    assertThat("ignored listed in results", entry.file, is(file));

    Set<File> ignored = centralStorage.readIgnoreFile();
    assertThat("persisted size", ignored.size(), is(1));
    assertThat("persisted content", ignored, hasItem(file));

  }

  @Test
  public void fixInconsistencyByIgnore() throws IOException {
    // prepare
    File fileWinner = temporaryFolder.newFile();
    File fileLoser = temporaryFolder.newFile();
    File fileInconsistend = temporaryFolder.newFile();
    TransitiveDiGraph graph = new TransitiveDiGraph();
    graph.addVertex(fileWinner);
    graph.addVertex(fileLoser);
    graph.addVertex(fileInconsistend);
    graph.addEdge(fileInconsistend, fileLoser);
    CentralStorage centralStorage = centralStorageRule.centralStorage();
    centralStorage.addEdges(graph);
    centralStorage.addToIgnored(fileInconsistend);
    File root = temporaryFolder.getRoot();
    Boolean recursive = true;
    ImageBattleFolder folder = new ImageBattleFolder(centralStorage, root, file -> true, recursive);

    // act
    folder.ignoreFile(fileInconsistend);

    // assert
    TransitiveDiGraph readGraph = centralStorage.readGraph();
    assertThat(readGraph.vertexSet(), IsNot.not(hasItem(fileInconsistend)));

  }

}