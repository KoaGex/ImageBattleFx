package org.imagebattle;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.hamcrest.core.IsNot;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ImageBattleFolderTest {

	Path ignorePath = Paths.get(System.getProperty("user.home"), CentralStorageTest.IGNORE_FILE_TEST);
	Path graphPath = Paths.get(System.getProperty("user.home"), CentralStorageTest.GRAPH_FILE_TEST);
	CentralStorage centralStorage;

	@Rule
	public TemporaryFolder tf = new TemporaryFolder();

	@Before
	public void setUp() throws Exception {
		centralStorage = new CentralStorage(CentralStorageTest.GRAPH_FILE_TEST, CentralStorageTest.IGNORE_FILE_TEST);
	}

	@After
	public void tearDown() throws Exception {
		Files.deleteIfExists(graphPath);
		Files.deleteIfExists(ignorePath);
	}

	@Test
	public void constructor() throws IOException {
		// prepare
		TransitiveDiGraph graph = new TransitiveDiGraph();
		File fileWinner = tf.newFile();
		File fileLoser = tf.newFile();
		File fileIgnored = tf.newFile();
		graph.addVertex(fileWinner);
		graph.addVertex(fileLoser);
		graph.addEdge(fileWinner, fileLoser);
		centralStorage.addEdges(graph);
		centralStorage.addToIgnored(fileIgnored);
		File fileNew = tf.newFile();
		File root = tf.getRoot();
		Boolean recursive = true;
		// act
		ImageBattleFolder folder = new ImageBattleFolder(centralStorage, root, file -> true, recursive);

		// assert
		List<ResultListEntry> resultList = folder.getResultList();
		assertThat(resultList.size(), is(4));
		ResultListEntry winnerEntry = resultList.get(0);
		assertThat(winnerEntry.wins, is(1));

	}

	@Test
	public void makeDecision() throws IOException {
		// prepare
		// new ImageBattleFolder(chosenDirectory, fileRegex, recursive)
		File root = tf.getRoot();
		File fileWinner = tf.newFile();
		File fileLoser = tf.newFile();
		Boolean recursive = false;
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
		File root = tf.getRoot();
		Boolean recursive = true;
		File fileWinner = tf.newFile();
		File fileLoser = tf.newFile();
		File fileInconsistend = tf.newFile();
		TransitiveDiGraph graph = new TransitiveDiGraph();
		graph.addVertex(fileWinner);
		graph.addVertex(fileLoser);
		graph.addVertex(fileInconsistend);
		graph.addEdge(fileInconsistend, fileLoser);
		centralStorage.addEdges(graph);
		centralStorage.addToIgnored(fileInconsistend);
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
		File root = tf.getRoot();
		File file = tf.newFile();
		Boolean recursive = false;
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
		File root = tf.getRoot();
		Boolean recursive = true;
		File fileWinner = tf.newFile();
		File fileLoser = tf.newFile();
		File fileInconsistend = tf.newFile();
		TransitiveDiGraph graph = new TransitiveDiGraph();
		graph.addVertex(fileWinner);
		graph.addVertex(fileLoser);
		graph.addVertex(fileInconsistend);
		graph.addEdge(fileInconsistend, fileLoser);
		centralStorage.addEdges(graph);
		centralStorage.addToIgnored(fileInconsistend);
		ImageBattleFolder folder = new ImageBattleFolder(centralStorage, root, file -> true, recursive);

		// act
		folder.ignoreFile(fileInconsistend);

		// assert
		TransitiveDiGraph readGraph = centralStorage.readGraph();
		assertThat(readGraph.vertexSet(), IsNot.not(hasItem(fileInconsistend)));

	}

}