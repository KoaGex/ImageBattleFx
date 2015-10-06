package org.imagebattle.chooser;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.imagebattle.BattleFinishedException;
import org.imagebattle.ImageBattleFolder;

import javafx.util.Pair;

/**
 * @author KoaGex
 *
 */
public class CandidateChooserSimulation {
    private static Logger log = LogManager.getLogger();
    // TODO turn this into a test?

    public static void main(String[] args) {
	File funPicsDir = new File("D:\\bilder\\fun pics");
	File imageBattleDat = new File(funPicsDir, "imageBattle.dat");
	imageBattleDat.delete();

	String regex = ".*\\.(BMP|GIF|JPEG|JPG|PNG)";
	boolean recursive = false;
	ImageBattleFolder folder = ImageBattleFolder.readOrCreate(funPicsDir, regex, recursive);

	List<File> files = folder.getResultList().stream().map(entry -> entry.file).collect(Collectors.toList());

	/*
	 * create one order of the files that should for this test represent the
	 * real order. First is the best and last the worst.
	 */
	Collections.shuffle(files);

	// change this string to switch chooser
	String chooser = "MaxNewEdges";

	folder.setChoosingAlgorithm(chooser);
	log.warn("start with chooser: {}  ", chooser);

	int counter = 0;
	long start = System.currentTimeMillis();
	boolean finished = false;
	while (!finished) {
	    try {
		Pair<File, File> nextToCompare = folder.getNextToCompare();
		File key = nextToCompare.getKey();
		File value = nextToCompare.getValue();
		int keyIndex = files.indexOf(key);
		int valueIndex = files.indexOf(value);
		boolean keyIsBetter = keyIndex < valueIndex;
		File winner = keyIsBetter ? key : value;
		File loser = keyIsBetter ? value : key;
		folder.makeDecision(winner, loser);
		counter++;
	    } catch (BattleFinishedException e) {
		long end = System.currentTimeMillis();
		log.warn("chooser: {}   decisions: {}     time: {}", chooser, counter, end - start);

		// funpics
		// chooser: SameWinLoseRatio decisions: 993 time: 9255
		// chooser: Winner Oriented decisions: 1078 time: 270086
		// chooser: BiSection decisions: 1104 time: 526268
		// chooser: Chronologic KO decisions: 1290 time: 14717
		// chooser: Date Distance decisions: 1214 time: 124325
		// chooser: MinimumDegree decisions: 1499 time: 340058
		// chooser: RankingTopDown decisions: 1592 time: 18367
		// chooser: RankingTopDown decisions: 2863 time: 40386
		// chooser: Random decisions: 1661 time: 71047
		// chooser: Random decisions: 1707 time: 70348
		// chooser: Random decisions: 2744 time: 347094
		// chooser: MaxNewEdges decisions: 3987 time: 3009149
		// chooser: MaxNewEdges decisions: 4001 time: 2103470

		// TODO check that result list is the same as files

		finished = true;
	    }
	}
    }

}
