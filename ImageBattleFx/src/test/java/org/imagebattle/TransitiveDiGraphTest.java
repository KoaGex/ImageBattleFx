package org.imagebattle;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import javafx.util.Pair;

/**
 * Testing {@link TransitiveDiGraph}.
 * 
 * @author KoaGex
 *
 */
public class TransitiveDiGraphTest {
	@Test
	public void addEdgeSimple() {
		// prepare
		TransitiveDiGraph graph = new TransitiveDiGraph();
		File fileA = new File("a");
		File fileB = new File("b");
		graph.addVertex(fileA);
		graph.addVertex(fileB);

		// act
		graph.addEdge(fileA, fileB);

		// assert
		Assert.assertTrue("containsRightDirection", graph.containsEdge(fileA, fileB));
		Assert.assertFalse("contains false direction", graph.containsEdge(fileB, fileA));
		Assert.assertTrue("contains any direction", graph.containsAnyEdge(fileB, fileA));

	}

	@Test
	public void getCalculatedCandidateCount() {
		// prepare
		TransitiveDiGraph graph = new TransitiveDiGraph();
		File fileA = new File("a");
		File fileB = new File("b");
		File fileC = new File("c");
		graph.addVertex(fileA);
		graph.addVertex(fileB);
		graph.addVertex(fileC);
		graph.addEdge(fileA, fileB);

		// act
		int calculatedCandidateCount = graph.getCalculatedCandidateCount();

		// assert
		assertThat(calculatedCandidateCount, is(2));

	}

	@Test
	public void getCandidateStream() {
		// prepare
		TransitiveDiGraph graph = new TransitiveDiGraph();
		File fileA = new File("a");
		File fileB = new File("b");
		File fileC = new File("c");
		graph.addVertex(fileA);
		graph.addVertex(fileB);
		graph.addVertex(fileC);
		graph.addEdge(fileA, fileB);

		// act
		Stream<Pair<File, File>> candidateStream = graph.getCandidateStream();
		List<Pair<File, File>> candidateList = candidateStream.collect(Collectors.toList());

		// assert
		assertThat("size", candidateList.size(), is(2));
		boolean containsAC = false;
		boolean containsBC = false;
		for (Pair<File, File> pair : candidateList) {
			File key = pair.getKey();
			File value = pair.getValue();
			containsAC |= (fileA.equals(key) && fileC.equals(value)) || (fileC.equals(key) && fileA.equals(value));
			containsBC |= (fileB.equals(key) && fileC.equals(value)) || (fileC.equals(key) && fileB.equals(value));
		}

		assertThat("AC", containsAC, is(true));
		assertThat("BC", containsBC, is(true));

	}

	@Test
	public void unfinished() {
		// prepare
		TransitiveDiGraph graph = new TransitiveDiGraph();
		File fileA = new File("a");
		File fileB = new File("b");
		File fileC = new File("c");
		graph.addVertex(fileA);
		graph.addVertex(fileB);
		graph.addVertex(fileC);
		graph.addEdge(fileA, fileB);

		// act
		boolean finished = graph.finishedProperty().get();

		// assert
		assertThat(finished, is(false));

	}

	@Test
	public void finishByAdd() {

		// prepare
		TransitiveDiGraph graph = new TransitiveDiGraph();
		File fileA = new File("a");
		File fileB = new File("b");
		File fileC = new File("c");
		graph.addVertex(fileA);
		graph.addVertex(fileB);
		graph.addVertex(fileC);
		graph.addEdge(fileA, fileB);

		// act
		graph.addEdge(fileB, fileC);
		boolean finishedAfterAdd = graph.finishedProperty().get();

		// assert
		assertThat(finishedAfterAdd, is(true));
	}

	@Test
	public void unfinishByRemove() {

		// prepare
		TransitiveDiGraph graph = new TransitiveDiGraph();
		File fileA = new File("a");
		File fileB = new File("b");
		File fileC = new File("c");
		graph.addVertex(fileA);
		graph.addVertex(fileB);
		graph.addVertex(fileC);
		graph.addEdge(fileA, fileB);

		// act
		graph.removeVertex(fileC);
		boolean finishedAfterRemove = graph.finishedProperty().get();

		// assert
		assertThat(finishedAfterRemove, is(true));
	}

	@Test
	public void addEdgesTransitive() {
		TransitiveDiGraph graph = new TransitiveDiGraph();

	}
}
