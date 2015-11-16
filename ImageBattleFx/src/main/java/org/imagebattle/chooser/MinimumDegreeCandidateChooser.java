package org.imagebattle.chooser;

import java.io.File;
import java.util.Comparator;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.imagebattle.TransitiveDiGraph;

import javafx.util.Pair;

public class MinimumDegreeCandidateChooser extends ACandidateChooser {
	private static Logger log = LogManager.getLogger();

	public MinimumDegreeCandidateChooser(TransitiveDiGraph pGraph) {
		super(pGraph);
	}

	@Override
	Pair<File, File> doGetNextCandidates() {

		Function<File, Integer> fileToDegree = file -> graph.inDegreeOf(file) + graph.outDegreeOf(file);

		Function<Pair<File, File>, Integer> filePairToDegreeSum = pair -> {
			return fileToDegree.apply(pair.getKey()) + fileToDegree.apply(pair.getValue());
		};

		java.util.stream.Stream<Pair<File, File>> candidateStream = graph.getCandidateStream();
		Pair<File, File> result = candidateStream.sorted(Comparator.comparing(filePairToDegreeSum)).findFirst().get();
		log.debug("minDegreeSum:" + filePairToDegreeSum.apply(result));
		return result;
	}

}
