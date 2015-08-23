package org.imagebattle;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javafx.util.Pair;

/**
 * How is this different from Bubble sort?
 * 
 * @author KoaGex
 *
 */
public class RankingTopDownCandidateChooser extends ACandidateChooser {

    public RankingTopDownCandidateChooser(TransitiveDiGraph2 pGraph) {
	super(pGraph);
    }

    @Override
    Pair<File, File> doGetNextCandidates() {
	Pair<File, File> result = null;

	Comparator<File> winnerFirstComparator = Comparator.comparing(graph2::outDegreeOf).reversed()
		.thenComparing(Comparator.comparing(graph2::inDegreeOf));
	List<File> resultList = graph2.vertexSet().stream() //
		.sorted(winnerFirstComparator)//
		.collect(Collectors.toList());

	boolean keepSearching = true;
	int i = 0;
	int step = 1;
	while (keepSearching) {
	    try {
		File file1 = resultList.get(i);
		File file2 = resultList.get(i + step);
		if (!graph2.containsEdge(file1, file2) && !graph2.containsEdge(file2, file1)) {
		    keepSearching = false;
		    result = new Pair<File, File>(file1, file2);
		}
		i++;
	    } catch (IndexOutOfBoundsException e) {
		i = 0;
		step++;
	    }

	}

	return result;
    }

}
