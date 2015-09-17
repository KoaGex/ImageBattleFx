package org.imagebattle;

import java.io.File;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.util.Pair;

public class RandomCandidateChooser extends ACandidateChooser {

	private static Logger log = LogManager.getLogger();

	public RandomCandidateChooser(TransitiveDiGraph2 pGraph) {
		super(pGraph);
	}

	@Override
	Pair<File, File> doGetNextCandidates() {
		long start = System.currentTimeMillis();

		int candidateCount = getCalculatedCandidateCount();
		Random random = new Random();
		int nextInt = random.nextInt(candidateCount);
		Pair<File, File> pair = getCandidateStream().skip(nextInt).findAny().get();

		long end = System.currentTimeMillis();
		log.trace("time needed: {}", end - start);
		return pair;
	}
}
