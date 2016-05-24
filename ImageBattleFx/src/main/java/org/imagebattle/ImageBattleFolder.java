package org.imagebattle;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.imagebattle.chooser.ACandidateChooser;
import org.imagebattle.chooser.BiSectionCandidateChooser;
import org.imagebattle.chooser.ChronologicKoCandidateChooser;
import org.imagebattle.chooser.DateDistanceCandidateChooser;
import org.imagebattle.chooser.MaxNewEdgesCandidateChoser;
import org.imagebattle.chooser.MinimumDegreeCandidateChooser;
import org.imagebattle.chooser.RandomCandidateChooser;
import org.imagebattle.chooser.RankingTopDownCandidateChooser;
import org.imagebattle.chooser.SameWinLoseRationCandidateChooser;
import org.imagebattle.chooser.WinnerOrientedCandidateChooser;
import org.jgrapht.graph.DefaultEdge;

/**
 * Represents one folder the user has chosen. Files that are direct or indirect children of this
 * directory take part in the battle.
 * 
 * <p>
 * Workflow :
 * <ol>
 * <li>read data from database</li>
 * <li>scan directory for files</li>
 * <li>delete files that have disappeared</li>
 * <li>add new files</li>
 * <li>battle!
 * </ol>
 * </p>
 * 
 * @author Besitzer
 *
 */
public class ImageBattleFolder {

  private static Logger log = LogManager.getLogger();

  private final TransitiveDiGraph graph;

  private final Set<File> ignoredFiles = new HashSet<>();

  /**
   * Multiple strategies to select the next images to be compared.
   */
  private final Map<String, ACandidateChooser> choosingAlgorithms = new HashMap<>();
  private ACandidateChooser choosingAlgorithm;

  private final CentralStorage centralStorage;

  private final MediaType mediaType;

  /**
   * @param centralStorage
   *          TODO
   * @param chosenDirectory
   * @param fileRegex
   * @param recursive
   */
  public ImageBattleFolder(//
      CentralStorage centralStorage, //
      File chosenDirectory, //
      MediaType mediaType, //
      Boolean recursive//
  ) {
    this.centralStorage = centralStorage;
    this.mediaType = mediaType;
    Predicate<File> fileRegex = mediaType::matches;

    graph = new TransitiveDiGraph();

    log.info("chosenDirectory: {}", chosenDirectory);

    // search for new images and add them
    LinkedList<File> currentLevel = findFiles(chosenDirectory, recursive, fileRegex);

    // register new files so centralStorage can return them in readGraph and readIgnoreFile.
    centralStorage.registerFiles(currentLevel);

    // Merge in vertexes and edges from CentralStorage.
    TransitiveDiGraph readGraph = centralStorage.readGraph(chosenDirectory, fileRegex, recursive);
    readGraph.vertexSet().forEach(graph::addVertex);
    readGraph.edgeSet().forEach(edge -> {
      File edgeSource = readGraph.getEdgeSource(edge);
      File edgeTarget = readGraph.getEdgeTarget(edge);
      graph.addEdge(edgeSource, edgeTarget);
    });

    // Merge in ignored files from CentralStorage.
    Set<File> readIgnoreFile = centralStorage.readIgnoreFile(chosenDirectory, mediaType, recursive);
    ignoredFiles.addAll(readIgnoreFile);

    currentLevel.stream()//
        .filter(f -> !ignoredFiles.contains(f))//
        .forEach(graph::addVertex);

    // Handle files that were in edges AND on ignore list.
    Set<File> vertexSet = graph.vertexSet();
    HashSet<File> intersection = new HashSet<>(ignoredFiles);
    intersection.retainAll(vertexSet);
    intersection.forEach(ignoredFiles::remove); // Removing from graph would risk too much data
                                                // loss.
    log.warn("these {} files were in the edges and in ignore list of central storage: {}",
        intersection.size(), intersection);
    List<File> inconsistencies = centralStorage.getInconsistencies();
    long count = inconsistencies.stream()//
        .filter(intersection::contains)//
        .count();
    log.warn(" {} are in global AND local inconsistencies", count);

    // choosing algorithms
    Map<String, ACandidateChooser> newChoosingAlorithms = choosingAlgorithms;
    WinnerOrientedCandidateChooser winnerOriented = new WinnerOrientedCandidateChooser(graph);
    newChoosingAlorithms.put("Winner Oriented", winnerOriented);
    newChoosingAlorithms.put("Date Distance", new DateDistanceCandidateChooser(graph));
    newChoosingAlorithms.put("Chronologic KO", new ChronologicKoCandidateChooser(graph));
    newChoosingAlorithms.put("Random", new RandomCandidateChooser(graph));
    newChoosingAlorithms.put("MaxNewEdges", new MaxNewEdgesCandidateChoser(graph));
    newChoosingAlorithms.put("RankingTopDown", new RankingTopDownCandidateChooser(graph));
    newChoosingAlorithms.put("BiSection", new BiSectionCandidateChooser(graph));
    newChoosingAlorithms.put("MinimumDegree", new MinimumDegreeCandidateChooser(graph));
    SameWinLoseRationCandidateChooser sameWinLoseRatio = new SameWinLoseRationCandidateChooser(
        graph);
    newChoosingAlorithms.put("SameWinLoseRatio", sameWinLoseRatio);

    choosingAlgorithm = sameWinLoseRatio;

  }

  private LinkedList<File> findFiles(File chosenDirectory, Boolean recursive,
      Predicate<File> fileRegex) {
    LinkedList<File> currentLevel = new LinkedList<>();

    // TODO refactor: Function<File, List<File>>
    if (recursive) {
      LinkedList<File> queue = new LinkedList<>();
      queue.add(chosenDirectory);
      while (!queue.isEmpty()) {
        File currentDirectory = queue.removeFirst();

        File[] subdirectories = currentDirectory.listFiles();
        if (subdirectories != null) {
          List<File> asList = Arrays.asList(subdirectories);
          Map<Boolean, List<File>> partition = asList.stream()
              .collect(Collectors.partitioningBy(File::isDirectory));

          // add directories to queue
          queue.addAll(partition.get(Boolean.TRUE));

          // add matching files to current level
          partition.get(Boolean.FALSE).stream()//
              .filter(fileRegex)//
              .forEach(currentLevel::add);
        }
      }
    } else {
      File[] allFiles = chosenDirectory.listFiles();
      if (allFiles != null) {
        for (File aFile : allFiles) {
          if (fileRegex.test(aFile)) {
            currentLevel.add(aFile);
          }
        }
      }
    }
    return currentLevel;
  }

  /**
   * @return
   */
  public List<ResultListEntry> getResultList() {

    // Comparator<File> winnerFirstComparator =
    // Comparator.comparing(graph2::outDegreeOf).reversed()
    // .thenComparing(Comparator.comparing(graph2::inDegreeOf));
    Comparator<File> winnerFirstComparator = Comparator.comparing(graph::getWinLoseDifference,
        Comparator.reverseOrder());
    List<ResultListEntry> resultList = graph.vertexSet().stream() //
        .sorted(winnerFirstComparator)//
        .map(graph::fileToResultEntry)//
        .collect(Collectors.toList());
    // zip would be cool
    for (int i = 0; i < resultList.size(); i++) {
      resultList.get(i).place = i + 1;
    }

    // at the end append all ignored files. The user should to see them to
    // maybe un-ignore or delete them.
    ignoredFiles.stream().map(file -> {
      ResultListEntry resultListEntry = new ResultListEntry();
      resultListEntry.file = file;
      resultListEntry.ignored = true;
      return resultListEntry;
    }).forEach(resultList::add);

    return resultList;
  }

  public void makeDecision(File pWinner, File pLoser) {

    List<DefaultEdge> newEdges = graph.addEdgesTransitive(pWinner, pLoser);

    Function<DefaultEdge, Pair<File, File>> function = edge -> {
      File winner = graph.getEdgeSource(edge);
      File loser = graph.getEdgeTarget(edge);
      return new Pair<>(winner, loser);
    };

    List<Pair<File, File>> newEdgePairs = newEdges.stream()//
        .map(function)//
        .collect(Collectors.toList());
    centralStorage.addEdges(newEdgePairs);

    // Clean up inconsistent central storage (when files are both ignored and in the graph).
    centralStorage.removeFromIgnored(pLoser);
    centralStorage.removeFromIgnored(pWinner);

  }

  public Set<String> getChoosingAlgorithms() {
    return choosingAlgorithms.keySet();
  }

  public void setChoosingAlgorithm(String pAlgorithmName) {
    ACandidateChooser newAlgorithm = choosingAlgorithms.get(pAlgorithmName);
    Objects.requireNonNull(newAlgorithm);
    choosingAlgorithm = newAlgorithm;
    log.info(pAlgorithmName);
  }

  public Optional<Pair<File, File>> getNextToCompare() {

    Optional<Pair<File, File>> nextToCompare = Optional.empty();

    // loop until we find two existing images
    boolean bothExist = false;
    while (!bothExist) {
      // TODO what if this loop removes all nodes ?!

      long start = System.currentTimeMillis();
      nextToCompare = choosingAlgorithm.getNextCandidates();
      long duration = System.currentTimeMillis() - start;
      log.trace("time needed to find next pair: {} ms", duration);

      if (nextToCompare.isPresent()) {
        Pair<File, File> pair = nextToCompare.get();

        // check for images that have been deleted in the meantime
        File key = pair.getKey();
        File value = pair.getValue();

        log.trace("nextToCompare: key={}    value={}", key.getName(), value.getName());

        bothExist = true;
        if (!key.exists()) {
          graph.removeVertex(key);
          bothExist = false;
        }
        if (!value.exists()) {
          graph.removeVertex(value);
          bothExist = false;
        }
      } else {
        bothExist = true; // break the loop.
      }

    }

    return nextToCompare;

  }

  /**
   * @param file
   *          This file will no longer appear in a battle scene. In ranking scenes it will be marked
   *          as ignored and have no place. All ignored files are placed after the worst rated file.
   */
  void ignoreFile(File file) {

    log.info("added file {}. ", file);
    log.trace(" Now on ignore: {}", ignoredFiles);

    ignoredFiles.add(file);
    centralStorage.addToIgnored(file);

    graph.removeVertex(file);
    centralStorage.removeFromEdges(file);

  }

  double getProgress() {
    int maxEdgeCount = graph.getMaxEdgeCount();
    int currentEdgeCount = graph.getCurrentEdgeCount();
    return Double.valueOf(currentEdgeCount) / Double.valueOf(maxEdgeCount);
  }

  /**
   * @param fileToReset
   *          remove this file from the graph and then add it again to remove all edges to and from
   *          the file. It also allows to bring ignored files back into the fight.
   */
  void reset(File fileToReset) {
    graph.removeVertex(fileToReset);
    graph.addVertex(fileToReset);
    centralStorage.removeFromEdges(fileToReset);

    ignoredFiles.remove(fileToReset);
    centralStorage.removeFromIgnored(fileToReset);
  }

  private void checkFinished() {

  }

  ReadOnlyBooleanProperty finishedProperty() {
    return graph.finishedProperty();
  }

}
