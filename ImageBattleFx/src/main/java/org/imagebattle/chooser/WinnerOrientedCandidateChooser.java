package org.imagebattle.chooser;

import java.io.File;
import java.util.Comparator;
import java.util.Random;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.imagebattle.TransitiveDiGraph;

import javafx.util.Pair;

public class WinnerOrientedCandidateChooser extends ACandidateChooser {
  private static Logger log = LogManager.getLogger();

  public WinnerOrientedCandidateChooser(TransitiveDiGraph pGraph) {
    super(pGraph);
  }

  @Override
  Pair<File, File> doGetNextCandidates() {
    int calculatedCandidateCount = graph.getCalculatedCandidateCount();

    // J8 style, gather candidates
    // TODO multiple choosing algorithms will need the candidates => move to graph?
    Stream<Pair<File, File>> candidatesStream = graph.getCandidateStream();
    // List<Pair<File, File>> candidates = candidatesStream.collect(Collectors.toList());

    // TODO don't first sort nodes and then create pairs, instead sort pairs by product of lossCount
    // ?
    // => prefer comparing pairs where both have lost only a few times or one has not yet lost
    ToIntFunction<Pair<File, File>> pairToLossCountProduct = pair -> {

      File key = pair.getKey();
      File value = pair.getValue();

      int loseCountKey = graph.inDegreeOf(key);
      int loseCountValue = graph.inDegreeOf(value);
      int loseSum = loseCountKey + loseCountValue; // use sum to not rate 0-8 as good as 0-0
      int loseDistance = Math.abs(loseCountKey - loseCountValue);// prefer 4-4 over 2-8 => low
                                                                 // distance ist good

      int winCountKey = graph.outDegreeOf(key);
      int winCountValue = graph.outDegreeOf(value);
      int winSum = winCountKey + winCountValue; // use sum to not rate 0-8 as good as 0-0, a high
                                                // winsum is good
      int winDistance = Math.abs(winCountKey - winCountValue);// prefer 4-4 over 2-8 => low distance
                                                              // ist good

      return 10 * loseSum + 8 * loseDistance - winSum + 5 * winDistance;
    };

    // normal sorting is ascending ( small to big )
    // candidates.sort(Comparator.comparingInt(pairToLossCountProduct));

    // System.err.println("first:" + pairToLossCountProduct.applyAsInt(candidates.get(0)));
    // candidates.stream().limit(90).mapToInt(pairToLossCountProduct).forEach(i ->
    // System.err.print(" " + i));
    // System.err.println();
    // System.err.println("last :" +
    // pairToLossCountProduct.applyAsInt(candidates.get(calculatedCandidateCount -
    // 1)));
    // candidates.stream().limit(60).forEach(i -> System.err.print(" " + i));
    // System.err.println();

    // choose lambda depending on number of candidates, variance should be ~ 1/3 of count
    // large factor leads to preferring items in the front, low results are more likely
    double lambda = Math.sqrt(3 / Math.sqrt(Double.valueOf(calculatedCandidateCount)));
    log.trace("lambda: {}", lambda);
    Random rand = new Random(System.currentTimeMillis());
    Supplier<Integer> expRandomSupplier = () -> {
      Double expRand = Math.log(1 - rand.nextDouble()) / (-lambda);
      int randomExpNumber = (int) Math.round(expRand);
      return randomExpNumber;
    };
    // Stream.generate(expRandomSupplier).limit(60).forEach(System.out::println); for testing the
    // exponential
    // distribution

    log.trace("candidate pair count: {}   ", calculatedCandidateCount);

    Integer expRandomIndex = expRandomSupplier.get();
    expRandomIndex = expRandomIndex - 1; // most likely value is 1, 0 never apperas but we need 0 to
                                         // access the
    // array

    // check range
    expRandomIndex = Math.min(calculatedCandidateCount, expRandomIndex);
    expRandomIndex = Math.max(0, expRandomIndex);

    Pair<File, File> pair = candidatesStream//
        .sorted(Comparator.comparingInt(pairToLossCountProduct))// sorting is what takes the time =>
                                                                // keep a
        // sorted list and adapt it to the changes ?
        // seems like the sorted stream takes as long as the completely sorted lits ?!
        .skip(expRandomIndex)//
        .findFirst()//
        .get();
    log.trace("exponentialRandomIndex: {}", expRandomIndex);

    // graph2.edgeSet().forEach(System.err::println);

    return pair;
  }
}