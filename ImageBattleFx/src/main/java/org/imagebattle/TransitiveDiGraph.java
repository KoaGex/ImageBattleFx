package org.imagebattle;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

/**
 * 
 * A graph that represents a transitive relation.
 * 
 * @author KoaGex
 *
 */
public class TransitiveDiGraph extends SimpleDirectedGraph<File, DefaultEdge> {
  private static Logger LOG = LogManager.getLogger();
  private BooleanProperty finished = new SimpleBooleanProperty();

  public TransitiveDiGraph() {
    super(DefaultEdge.class);
  }

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  @Override
  public DefaultEdge addEdge(File sourceVertex, File targetVertex) {
    List<DefaultEdge> addEdgesTransitive = addEdgesTransitive(sourceVertex, targetVertex);
    return addEdgesTransitive.isEmpty() ? null : addEdgesTransitive.get(0);
  }

  public List<DefaultEdge> addEdgesTransitive(File sourceVertex, File targetVertex) {

    List<DefaultEdge> result = new ArrayList<>();

    LOG.trace(sourceVertex.getName() + " won against " + targetVertex.getName());

    BiFunction<File, File, DefaultEdge> addEdge = (from, to) -> {
      boolean edgeExists = super.containsEdge(from, to) || super.containsEdge(to, from);
      if (edgeExists) {
        LOG.trace("edge already set:" + from.getName() + " -> " + to.getName());
        return null;
      } else {
        // use super. without it would quickly result in an infinite recursive loop
        DefaultEdge newEdge = super.addEdge(from, to);
        LOG.trace("add edge {} -> {} . newEdge: {}", from.getName(), to.getName(), newEdge);
        return newEdge;
      }
    };

    int edgeCountOld = super.edgeSet().size();

    // ensure transitivity
    Queue<Pair<File, File>> queue = new LinkedList<Pair<File, File>>();

    queue.add(new Pair<File, File>(sourceVertex, targetVertex));
    while (!queue.isEmpty()) {

      Pair<File, File> current = queue.poll();
      File b = current.getKey();
      File c = current.getValue();

      DefaultEdge edge = addEdge.apply(b, c);
      if (edge != null) {
        result.add(edge);
      }

      // b -> c -> d
      super.outgoingEdgesOf(c).stream()//
          .map(super::getEdgeTarget) //
          .filter(d -> !containsAnyEdge(b, d))//
          .map(d -> new Pair<File, File>(b, d))//
          .peek(LOG::trace) //
          .forEach(queue::add);

      // a -> b -> c
      super.incomingEdgesOf(b).stream()//
          .map(super::getEdgeSource) //
          .filter(a -> !containsAnyEdge(c, a))//
          .map(a -> new Pair<File, File>(a, c))//
          .peek(LOG::trace) //
          .forEach(queue::add);
    }

    int edgeCountNew = super.edgeSet().size();
    int edgesAdded = edgeCountNew - edgeCountOld;
    int nodeCount = super.vertexSet().size();
    int ofMaximal = nodeCount * (nodeCount - 1) / 2;
    double percent = Double.valueOf(edgeCountNew) / Double.valueOf(ofMaximal);
    LOG.trace("added {} and now have {} edges of {} possible. In Percent: {}", edgesAdded,
        edgeCountNew, ofMaximal, percent);

    checkFinished();

    return result;
  }

  int getMaxEdgeCount() {
    Set<File> vertexSet = this.vertexSet();
    int nodeCount = vertexSet.size();
    int maxEdgeCount = nodeCount * (nodeCount - 1) / 2;
    return maxEdgeCount;
  }

  int getCurrentEdgeCount() {
    return this.edgeSet().size();
  }

  public Integer getWinLoseDifference(File file) {
    return outDegreeOf(file) - inDegreeOf(file);
  }

  public boolean containsAnyEdge(File v1, File v2) {
    return containsEdge(v1, v2) || containsEdge(v2, v1);
  }

  ResultListEntry fileToResultEntry(File i) {
    ResultListEntry entry = new ResultListEntry();
    entry.file = i;
    entry.wins = outDegreeOf(i);
    entry.loses = inDegreeOf(i);
    entry.fixed = vertexSet().size() - 1 == entry.wins + entry.loses;
    return entry;
  }

  /**
   * @return The resulting stream of {@link #getCandidateStream(Collection)} when using
   *         {@link #vertexSet()}.
   */
  public final Stream<Pair<File, File>> getCandidateStream() {
    Set<File> vertexSet = this.vertexSet();
    return getCandidateStream(vertexSet);
  }

  /**
   * @param vertexSubset
   *          Must be a subset of {@link #vertexSet()}. The result Stream will only contain pairs
   *          with elements of this subset.
   * @return A {@link Stream} of {@link Pair} representing missing edges in the graph. If the Stream
   *         contains pair (a,b) it does not contain (b,a).
   */
  public final Stream<Pair<File, File>> getCandidateStream(Collection<File> vertexSubset) {

    if (!vertexSet().containsAll(vertexSubset)) {
      throw new IllegalArgumentException(
          "the given vertexSubset " + vertexSubset + "  ist not a subset of vertexSet!");
    }

    Stream<Pair<File, File>> candidatesStream = null;

    // multiple choosing algorithms will need the candidates
    candidatesStream = vertexSubset.stream() //
        .flatMap(from -> vertexSubset.stream() //
            .filter(to -> !this.containsAnyEdge(from, to))//
            .filter(to -> !Objects.equals(to, from)) //
            // graph does not allow loops
            .filter(to -> Comparator.comparing(File::getAbsolutePath).compare(to, from) > 0) //
            // avoid having candidates a-b and b-a
            .map(to -> new Pair<File, File>(from, to))//
    );
    return candidatesStream;
  }

  /**
   * @return How many candidate pairs should be left in the {@link #graph}.
   */
  public final int getCalculatedCandidateCount() {
    Set<File> vertexSet = this.vertexSet();
    int nodeCount = vertexSet.size();
    int currentEdgeCount = this.edgeSet().size();

    int maxEdgeCount = nodeCount * (nodeCount - 1) / 2;
    int calculatedCandidateCount = maxEdgeCount - currentEdgeCount;

    LOG.trace(calculatedCandidateCount);
    return calculatedCandidateCount;
  }

  @Override
  public boolean addVertex(File v) {
    boolean wasAdded = super.addVertex(v);
    checkFinished();
    return wasAdded;
  }

  public boolean removeVertex(File v) {
    boolean wasRemoved = super.removeVertex(v);
    checkFinished();
    return wasRemoved;
  };

  public void addNormalEdges(List<Pair<String, String>> filesPaths) {

    // add all edges
    for (Pair<String, String> pair : filesPaths) {
      final File winner = new File(pair.getKey());
      final File loser = new File(pair.getValue());
      addVertex(winner);
      addVertex(loser);
      super.addEdge(winner, loser);
    }
    // TODO check if transitive is still fulfilled

  }

  ReadOnlyBooleanProperty finishedProperty() {
    return finished;
  }

  List<Pair<File, File>> getEdgePairs() {

    Function<DefaultEdge, Pair<File, File>> function = edge -> new Pair<>(this.getEdgeTarget(edge),
        this.getEdgeSource(edge));

    List<Pair<File, File>> newEdgePairs = this.edgeSet().stream()//
        .map(function)//
        .collect(Collectors.toList());

    return newEdgePairs;
  }

  private void checkFinished() {
    int currentEdgeCount = getCurrentEdgeCount();
    int maxEdgeCount = getMaxEdgeCount();
    boolean graphCompleted = currentEdgeCount == maxEdgeCount;
    finished.set(graphCompleted);
  }

  List<DefaultEdge> simplifiedEdges() {
    Predicate<DefaultEdge> isTransitiveEdge = e -> vertexSet().stream()//
        .anyMatch(v -> containsEdge(getEdgeSource(e), v) && containsEdge(v, getEdgeTarget(e)));
    return edgeSet().stream()//
        .filter(isTransitiveEdge.negate())//
        .collect(Collectors.toList());
  }
}