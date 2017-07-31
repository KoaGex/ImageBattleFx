package org.imagebattle.chooser;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.imagebattle.CentralStorage;
import org.imagebattle.ImageBattleFolder;
import org.imagebattle.MediaType;
import org.imagebattle.ResultListEntry;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

/**
 * @author KoaGex
 *
 */
public class CandidateChooserSimulation {
  private static Logger log = LogManager.getLogger();
  // TODO turn this into a test?

  // TODO new chart: how many are surely place x?
  // TODO parameterize WinnerOriented
  /*
   * TODO write use cases - In Flames: find top 18 for a cd
   */
  public static void main(String[] args) {

    // TODO chart order fixable?

    // File dir = new File("D:\\bilder\\fun pics");
    File dir = new File("D:\\bilder\\2009\\2009-08-05wacken"); // 72 images

    String userHome = System.getProperty("user.home");
    File userHomeDirectory = new File(userHome);

    File file = new File(userHomeDirectory, "candidateChooserSimulation.db");

    boolean recursive = false;

    List<String> chooserNames = Arrays.asList(//
        CandidateChoosers.SameWinLoseRatio.getVisibleName(),
        CandidateChoosers.Winner_Oriented.getVisibleName(),
        CandidateChoosers.MinimumDegree.getVisibleName());
    String directory = "D:\\tech\\graphviz\\";
    DefaultCategoryDataset edgeCountDataSet = new DefaultCategoryDataset();

    Map<Integer, DefaultCategoryDataset> pointDataMap = new HashMap<>();

    for (String chooser : chooserNames) {

      file.delete();
      CentralStorage centralStorage = new CentralStorage(file.getName());
      ImageBattleFolder folder = new ImageBattleFolder(centralStorage, dir, MediaType.IMAGE,
          recursive, "name");

      List<File> files = folder.getResultList().stream().map(entry -> entry.file)
          .collect(Collectors.toList());

      /*
       * create one order of the files that should for this test represent the real order. First is
       * the best and last the worst.
       */
      Collections.shuffle(files);

      // change this string to switch chooser

      folder.setChoosingAlgorithm(chooser);
      log.warn("start with chooser: {}  ", chooser);

      int counter = 0;
      long start = System.currentTimeMillis();
      Optional<Pair<File, File>> nextToCompare;
      nextToCompare = folder.getNextToCompare();
      while (nextToCompare.isPresent()) {
        Pair<File, File> pair = nextToCompare.get();
        File key = pair.getKey();
        File value = pair.getValue();
        int keyIndex = files.indexOf(key);
        int valueIndex = files.indexOf(value);
        boolean keyIsBetter = keyIndex < valueIndex;
        File winner = keyIsBetter ? key : value;
        File loser = keyIsBetter ? value : key;
        folder.makeDecision(winner, loser);
        counter++;

        if (counter % 5 == 0) {
          String fileName = chooser + "_" + String.format("%03d", counter);
          folder.writeGraphImage(directory, fileName);

          List<ResultListEntry> resultList = folder.getResultList();
          int edgeCount = resultList.stream().mapToInt(e -> e.wins).sum();
          edgeCountDataSet.addValue(edgeCount, chooser, String.valueOf(counter));

          DefaultCategoryDataset dataset = pointDataMap.computeIfAbsent(counter,
              i -> new DefaultCategoryDataset());
          resultList.stream()//
              .collect(Collectors.groupingBy(result -> result.wins - result.loses))//
              .entrySet()//
              .stream()//
              .sorted(Comparator.comparing(Entry::getKey))//
              .forEach(entry -> {
                Integer points = entry.getKey();
                List<ResultListEntry> list = entry.getValue();
                dataset.addValue(list.size(), chooser, points);
              });

        }

        nextToCompare = folder.getNextToCompare();
      }
      long end = System.currentTimeMillis();
      log.warn("chooser: {}   decisions: {}     time: {}", chooser, counter, end - start);

    }

    JFreeChart lineChart = ChartFactory.createLineChart("title", "category", "value",
        edgeCountDataSet);
    try {
      ChartUtilities.saveChartAsJPEG(new File(directory, "edgeCount.jpg"), lineChart, 1800, 900);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    pointDataMap.forEach((counter, data) -> {

      JFreeChart pointChart = ChartFactory.createLineChart("title", "category", "value", data);
      try {
        ChartUtilities.saveChartAsJPEG(
            new File(directory, "pointChart_" + String.format("%03d", counter) + ".jpg"),
            pointChart, 1800, 900);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }

    });

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

  }

}
