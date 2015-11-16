package org.imagebattle.chooser;

import java.io.File;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.imagebattle.TransitiveDiGraph;

import javafx.util.Pair;

public class MaxNewEdgesCandidateChoser extends ACandidateChooser {

	public MaxNewEdgesCandidateChoser(TransitiveDiGraph pGraph) {
		super(pGraph);
	}

	@Override
	Pair<File, File> doGetNextCandidates() {
		Function<Pair<File, File>, Integer> pairToPossibleEdgeCount = pair -> {
			File key = pair.getKey();
			File value = pair.getValue();
			Set<File> keyIncoming = graph.incomingEdgesOf(key).stream().map(graph::getEdgeSource)
					.collect(Collectors.toSet());
			Set<File> valueIncoming = graph.incomingEdgesOf(value).stream().map(graph::getEdgeSource)
					.collect(Collectors.toSet());
			keyIncoming.removeAll(valueIncoming);
			valueIncoming.removeAll(keyIncoming);
			int incomeDiff = keyIncoming.size() + valueIncoming.size();

			Set<File> keyOutgoing = graph.outgoingEdgesOf(key).stream().map(graph::getEdgeTarget)
					.collect(Collectors.toSet());
			Set<File> valueOutgoing = graph.outgoingEdgesOf(value).stream().map(graph::getEdgeTarget)
					.collect(Collectors.toSet());
			keyOutgoing.removeAll(valueOutgoing);
			valueOutgoing.removeAll(keyOutgoing);
			int outgoingDiff = keyOutgoing.size() + valueOutgoing.size();

			return incomeDiff + outgoingDiff + incomeDiff * outgoingDiff; // product
			// to
			// reward
			// pairs
			// were
			// both
			// are
			// high
		};
		return graph.getCandidateStream()//
				.sorted(Comparator.comparing(pairToPossibleEdgeCount, Comparator.reverseOrder()))//
				.limit(1)//
				.peek(pair -> System.err.println(pairToPossibleEdgeCount.apply(pair)))//
				.findFirst()//
				.get();
	}

}
