package org.imagebattle.chooser;

import java.util.function.Function;

import org.imagebattle.TransitiveDiGraph;

public enum CandidateChoosers {

  Winner_Oriented("Winner Oriented", WinnerOrientedCandidateChooser::new), //
  DateDistance("Date Distance", DateDistanceCandidateChooser::new), //
  Chronologic_KO("Chronologic KO", ChronologicKoCandidateChooser::new), //
  Random("Random", RandomCandidateChooser::new), //
  MaxNewEdges("MaxNewEdges", MaxNewEdgesCandidateChoser::new), //
  RankingTopDown("RankingTopDown", RankingTopDownCandidateChooser::new), //
  BiSection("BiSection", BiSectionCandidateChooser::new), //
  MinimumDegree("MinimumDegree", MinimumDegreeCandidateChooser::new), //
  SameWinLoseRatio("SameWinLoseRatio", SameWinLoseRationCandidateChooser::new), //
  ;

  private CandidateChoosers() {
    // TODO Auto-generated constructor stub
  }

  private String visibleName;
  private Function<TransitiveDiGraph, ACandidateChooser> chooser;

  private CandidateChoosers(String visibleName,
      Function<TransitiveDiGraph, ACandidateChooser> chooser) {
    this.visibleName = visibleName;
    this.chooser = chooser;
  }

  public String getVisibleName() {
    return visibleName;
  }

  public Function<TransitiveDiGraph, ACandidateChooser> getChooser() {
    return chooser;
  }
}
