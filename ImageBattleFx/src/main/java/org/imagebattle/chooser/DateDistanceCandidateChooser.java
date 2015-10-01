package org.imagebattle.chooser;

import java.io.File;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javafx.util.Pair;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.imagebattle.TransitiveDiGraph2;

public class DateDistanceCandidateChooser extends ACandidateChooser {
	private static Logger log = LogManager.getLogger();
	private Map<File, Long> takenOnTime;

	public DateDistanceCandidateChooser(TransitiveDiGraph2 pGraph) {
		super(pGraph);
	}

	@Override
	Pair<File, File> doGetNextCandidates() {

		Function<File, Long> nodeIndexToTime = file -> {
			if (takenOnTime == null) {
				takenOnTime = new HashMap<File, Long>();
			}
			Long fTime = takenOnTime.computeIfAbsent(file, key -> { // computeIfAbsent is lazy execution
						Date date = readExif(file);
						if (date == null) {
							return null;
						}
						long time = date.getTime();
						System.out.println("file:" + file + "    \t taken on:" + date);
						return time;
					});
			return fTime;
		};

		// maybe pair apply, PairFunction->diff function?
		Function<Pair<File, File>, Long> pairToDistance = pair -> {
			File index1 = pair.getKey();
			File index2 = pair.getValue();
			long distance = Math.abs(nodeIndexToTime.apply(index1) - nodeIndexToTime.apply(index2));
			// System.out.println("distaance:" + distance);
			return distance;
		};

		return getCandidateStream()//
				.min(Comparator.comparing(pairToDistance))//
				.orElseThrow(() -> new RuntimeException("no more candidates, we are finished now"));

	}

}
