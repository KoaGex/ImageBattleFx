package org.imagebattle.chooser;

import java.io.File;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.imagebattle.TransitiveDiGraph;

import javafx.util.Pair;

public class DateDistanceCandidateChooser extends ACandidateChooser {
	private static Logger log = LogManager.getLogger();
	private Map<File, Long> takenOnTime;

	public DateDistanceCandidateChooser(TransitiveDiGraph pGraph) {
		super(pGraph);
	}

	@Override
	Pair<File, File> doGetNextCandidates() {

		Function<File, Long> nodeIndexToTime = file -> {
			if (takenOnTime == null) {
				takenOnTime = new HashMap<File, Long>();
			}
			// computeIfAbsent is lazy execution
			Long fTime = takenOnTime.computeIfAbsent(file, key -> {
				Date date = readExif(file);
				if (date == null) {
					return null;
				}
				long time = date.getTime();
				log.trace("file:" + file + "    \t taken on:" + date);
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

		return graph.getCandidateStream()//
				.min(Comparator.comparing(pairToDistance))//
				.orElseThrow(() -> new RuntimeException("no more candidates, we are finished now"));

	}

}
