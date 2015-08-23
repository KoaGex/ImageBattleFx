package org.imagebattle;

import java.io.File;
import java.util.Comparator;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fj.F;
import fj.data.vector.V;
import fj.data.vector.V2;
import javafx.util.Pair;

public class MinimumDegreeCandidateChooser extends ACandidateChooser {
    private static Logger log = LogManager.getLogger();

    public MinimumDegreeCandidateChooser(TransitiveDiGraph2 pGraph) {
	super(pGraph);
    }

    @Override
    Pair<File, File> doGetNextCandidates() {

	Function<Pair<File, File>, V2<File>> pairToVector = pair -> V.v(pair.getKey(), pair.getValue());
	F<File, Integer> fileToDegree = file -> graph2.inDegreeOf(file) + graph2.outDegreeOf(file);
	Function<V2<File>, Integer> toDegreeSum = v2 -> {
	    V2<Integer> v2Degree = v2.map(fileToDegree);
	    return v2Degree._1() + v2Degree._2();
	};

	Function<Pair<File, File>, Integer> filePairToDegreeSum = pairToVector.andThen(toDegreeSum);

	java.util.stream.Stream<Pair<File, File>> candidateStream = getCandidateStream();
	Pair<File, File> result = candidateStream.sorted(Comparator.comparing(filePairToDegreeSum)).findFirst().get();
	log.debug("minDegreeSum:"+ filePairToDegreeSum.apply(result));
	return result;
    }

}
