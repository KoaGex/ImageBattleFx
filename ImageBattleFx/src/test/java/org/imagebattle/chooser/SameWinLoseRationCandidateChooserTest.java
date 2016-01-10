/**
 * 
 */
package org.imagebattle.chooser;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Optional;

import org.imagebattle.TransitiveDiGraph;
import org.junit.Test;

import javafx.util.Pair;

/**
 * Testing {@link SameWinLoseRationCandidateChooser}.
 * 
 * @author KoaGex
 *
 */
public class SameWinLoseRationCandidateChooserTest {

  @Test
  public void test() {
    // prepare
    TransitiveDiGraph graph = new TransitiveDiGraph();
    // Files do not have to exist for this test.
    File fileWin = new File("a");
    File fileLose = new File("b");
    File fileNoFight = new File("c");
    graph.addVertex(fileWin);
    graph.addVertex(fileLose);
    graph.addVertex(fileNoFight);
    graph.addEdge(fileWin, fileLose);
    // after this setup all nodes have different win lose rations (+1, 0, -1) but the fight is not
    // finished.
    SameWinLoseRationCandidateChooser chooser = new SameWinLoseRationCandidateChooser(graph);

    // act
    Optional<Pair<File, File>> nextCandidates = chooser.getNextCandidates();

    // assert
    assertTrue(nextCandidates.isPresent());
  }

}
