package org.imagebattle;

import java.io.File;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javafx.util.Pair;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This one is more oriented on viewing your pictures in the order they were taken.
 * 
 * @author Besitzer
 *
 */
public class ChronologicKoCandidateChooser extends ACandidateChooser {
	private static Logger log = LogManager.getLogger();
	private List<File> sortedFiles = new LinkedList<>();

	private List<File> currentLevel = new LinkedList<>();

	public ChronologicKoCandidateChooser(TransitiveDiGraph2 pGraph) {
		super(pGraph);
	}

	@Override
	Pair<File, File> doGetNextCandidates() {
		Set<File> vertexSet = graph2.vertexSet();
		if (sortedFiles.isEmpty()) {
			vertexSet.stream()//
					.sorted(Comparator.comparing(this::readExif))//
					.forEach(sortedFiles::add);
		}

		int inDegree = 0; // loseCount
		while (currentLevel.size() < 2 && inDegree < vertexSet.size() - 1) {

			currentLevel.clear();
			// initialize on first use
			int tempInDegree = inDegree; // for lambda loop final

			sortedFiles.stream()//
					.filter(file -> graph2.inDegreeOf(file) == tempInDegree)// TODO what if there is only one with zero losses?
					.forEach(currentLevel::add);

			inDegree++; // in case only one is found, raise the number

			// FIXME possible infinite loop if rating is finished
		}

		if (currentLevel.size() < 2) {
			// TODO checking if the battle is finished should be determined by edge count and implemented in ACandidateChooser
			throw new RuntimeException("battle finished");
		}

		File one = currentLevel.remove(0);
		File two = currentLevel.remove(0);
		Pair<File, File> result = new Pair<File, File>(one, two);

		log.debug("outDegree: {}        key=   {} value= {}", inDegree, readExif(one), readExif(two));

		return result;
	}
}
