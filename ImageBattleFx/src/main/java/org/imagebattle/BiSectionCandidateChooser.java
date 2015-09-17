package org.imagebattle;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.util.Pair;

/**
 * https://en.wikipedia.org/wiki/Nested_intervals
 * https://de.wikipedia.org/wiki/Bisektion
 * 
 * @author Besitzer
 *
 */
public class BiSectionCandidateChooser extends ACandidateChooser {
    private static Logger log = LogManager.getLogger();

    public BiSectionCandidateChooser(TransitiveDiGraph2 pGraph) {
	super(pGraph);
    }

    @Override
    Pair<File, File> doGetNextCandidates() {
	Function<File, Integer> degreeOf = file -> graph2.inDegreeOf(file) + graph2.outDegreeOf(file);
	Function<File, Integer> toWinLoseDifference = file -> graph2.outDegreeOf(file) - graph2.inDegreeOf(file);

	// sorted from best to worst
	List<File> rankingList = graph2.vertexSet()//
		.stream()//
		.sorted(Comparator.comparing(toWinLoseDifference, Comparator.reverseOrder()))//
		.collect(Collectors.toList());

	Function<File, Integer> toWorstWinner = file -> {
	    return graph2//
		    .incomingEdgesOf(file).stream()//
		    .map(graph2::getEdgeSource)//
		    .mapToInt(rankingList::indexOf)//
		    .max()//
		    .orElse(0);
	};

	Function<File, Integer> toBestLoser = file -> {
	    return graph2//
		    .outgoingEdgesOf(file).stream()//
		    .map(graph2::getEdgeTarget)//
		    .mapToInt(rankingList::indexOf)//
		    .min()//
		    .orElse(rankingList.size() - 1);
	};

	Function<File, Integer> toIntervalLength = file -> {
	    return toBestLoser.apply(file) - toWorstWinner.apply(file);
	};

	// TODO improve by choosing images with big worstWinner-bestLooser
	// distance?
	File minimumDegreeCandidate = graph2.vertexSet().stream()//
		.filter(file -> degreeOf.apply(file) < graph2.vertexSet().size() - 1)//
		.sorted(Comparator.comparing(toIntervalLength, Comparator.reverseOrder()))//
		.findFirst()//
		.get();

	System.err.println("first:" + toWinLoseDifference.apply(rankingList.get(0)));

	int worstWinner = toWorstWinner.apply(minimumDegreeCandidate);

	int bestLooser = toBestLoser.apply(minimumDegreeCandidate);

	int medium = (bestLooser + worstWinner) / 2;

	// get the candidate that has the lowest difference to medium in
	// rankingList
	File otherCandidate = rankingList.stream()//
		.filter(file -> !graph2.containsEdge(file, minimumDegreeCandidate))//
		.filter(file -> !graph2.containsEdge(minimumDegreeCandidate, file))//
		.filter(file -> !minimumDegreeCandidate.equals(file))//
		.sorted(Comparator.comparing(file -> Math.abs(medium - rankingList.indexOf(file))))//
		.findFirst()//
		.get();

	log.debug("self:{} wW:{} bL:{} medium:{} other:{} diff:{}", rankingList.indexOf(minimumDegreeCandidate),
		worstWinner, bestLooser, medium, rankingList.indexOf(otherCandidate), bestLooser - worstWinner);
	// TODO i dont think this works correctly. debug by viewing the winner and looser lists.

	return new Pair<File, File>(minimumDegreeCandidate, otherCandidate);
    }

}
