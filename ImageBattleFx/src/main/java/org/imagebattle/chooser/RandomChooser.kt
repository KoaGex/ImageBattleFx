package org.imagebattle.chooser

import javafx.util.Pair
import org.apache.logging.log4j.LogManager
import org.imagebattle.TransitiveDiGraph
import java.io.File
import java.util.Random

class RandomChooser(pGraph: TransitiveDiGraph) : ACandidateChooser(pGraph) {
	val logger = LogManager.getLogger();
	override fun doGetNextCandidates(): Pair<File, File> {
		val start = System.currentTimeMillis();

		val candidateCount = graph.getCalculatedCandidateCount();
		val nextInt: Long = Random().nextInt(candidateCount).toLong();
		val pair = graph.getCandidateStream().skip(nextInt).findAny().get();

		val end = System.currentTimeMillis();
		logger.trace("time needed: {}", end - start);
		return pair;
	}
}