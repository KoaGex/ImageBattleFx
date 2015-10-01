package org.imagebattle.chooser;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.imagebattle.BattleFinishedException;
import org.imagebattle.TransitiveDiGraph2;

import javafx.util.Pair;

public class SameWinLoseRationCandidateChooser extends ACandidateChooser {
    private static Logger log = LogManager.getLogger();

    public SameWinLoseRationCandidateChooser(TransitiveDiGraph2 pGraph) {
	super(pGraph);
    }

    @Override
    Pair<File, File> doGetNextCandidates() throws  BattleFinishedException {

	Pair<File, File> result = graph2.vertexSet()//
		.stream()//
		.collect(Collectors.groupingBy(graph2::getWinLoseDifference))//
		.values()//
		.stream()//
		.max(Comparator.comparing(List::size))//
		.map(list -> {
		    Integer difference = graph2.getWinLoseDifference(list.get(0));
		    log.debug("list size: {}  difference: {}", list.size(), difference);
		    return list;
		})//
		.map(this::getCandidateStream)//
		.orElseThrow(IllegalStateException::new)//
		// TODO randomize
		.collect(Collectors.groupingBy(a -> 1))//
		.entrySet().stream()//
//		.peek(System.err::println)//
		.findAny()//
		.map(entry -> {
		    List<Pair<File, File>> list = entry.getValue();
		    int size = list.size();
		    Random random = new Random();
		    int index = random.ints(0, size).findFirst().getAsInt();
		    log.debug(index + "/" + size);
		    return list.get(index);
		})//
		.orElseThrow(BattleFinishedException::new);//

	// TODO what when the biggest has only images that are already compared
	// to each other? can that happen at all?

	return result;
    }

}
