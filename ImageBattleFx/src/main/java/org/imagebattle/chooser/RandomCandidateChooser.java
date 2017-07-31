package org.imagebattle.chooser;

import java.io.File;
import java.util.Random;

import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.imagebattle.TransitiveDiGraph;

public class RandomCandidateChooser extends ACandidateChooser {

  private static Logger log = LogManager.getLogger();

  public RandomCandidateChooser(TransitiveDiGraph pGraph) {
    super(pGraph);
  }

  @Override
  Pair<File, File> doGetNextCandidates() {
    long start = System.currentTimeMillis();

    int candidateCount = graph.getCalculatedCandidateCount();
    Random random = new Random();
    int nextInt = random.nextInt(candidateCount);
    Pair<File, File> pair = graph.getCandidateStream().skip(nextInt).findAny().get();

    long end = System.currentTimeMillis();
    log.trace("time needed: {}", end - start);
    return pair;
  }
}
