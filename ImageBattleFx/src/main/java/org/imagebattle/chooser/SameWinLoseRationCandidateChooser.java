package org.imagebattle.chooser;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    Pair<File, File> doGetNextCandidates() throws BattleFinishedException {
	log.debug("start");

	Map<Integer, List<File>> differenceMap = graph2.vertexSet()//
		.stream()//
		.collect(Collectors.groupingBy(graph2::getWinLoseDifference));

	log.debug("number of groups: {}", differenceMap.size());

	Optional<List<File>> biggestGroup = differenceMap//
		.values()//
		.stream()//
		.max(Comparator.comparing(List::size))//
		.map(list -> {
		    Integer difference = graph2.getWinLoseDifference(list.get(0));
		    log.debug("list size: {}  difference: {}", list.size(), difference);
		    return list;
		});

	/*
	 * When group is big, candidate pair lits is veeery long => before
	 * building it try some times to find any.
	 */
	List<File> biggestGroupList = biggestGroup.get();
	Pair<File, File> result = null;

	for (int i = 0; i < biggestGroupList.size(); i++) {
	    File from = getRandomElement(biggestGroupList);
	    File to = getRandomElement(biggestGroupList);
	    if (!Objects.equals(to, from) && !graph2.containsAnyEdge(from, to)) {
		result = new Pair<File, File>(from, to);
		log.debug("guess success at try: {}", i);
		break;
	    }
	}

	if (result == null) {
	    Map<Integer, List<Pair<File, File>>> countMap = biggestGroup//
		    .map(this::getCandidateStream)//
		    .orElseThrow(IllegalStateException::new)//
		    .collect(Collectors.groupingBy(a -> 1));

	    log.debug("candidate map size: {}", countMap.size());

	    result = countMap//
		    .entrySet().stream()//
		    // .peek(System.err::println)//
		    .findAny()//
		    .map(entry -> {
			List<Pair<File, File>> list = entry.getValue();
			return getRandomElement(list);
		    })//
		    .orElseThrow(BattleFinishedException::new);//
	}

	// TODO what when the biggest has only images that are already compared
	// to each other? can that happen at all?

	log.debug("end, result:{}", result);

	return result;
    }

    private <T> T getRandomElement(List<T> list) {
	int size = list.size();
	Random random = new Random();
	int index = random.nextInt(size);
	log.debug(index + "/" + size);
	return list.get(index);
    }

}
