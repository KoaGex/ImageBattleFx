package org.imagebattle;

import java.io.File;
import java.util.List;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;

final class MusicRankingScene extends Scene {
  private static Logger log = LogManager.getLogger();

  private final Runnable refresh;

  private MusicRankingScene(StackPane switchSceneStackPane, ImageBattleFolder folder,
      Runnable switchSceneAction) {
    super(switchSceneStackPane);
    List<ResultListEntry> resultList = folder.getResultList();

    Button switchSceneButton = new Button("Vergleiche");
    switchSceneButton.setOnAction(event -> switchSceneAction.run());
    StackPane.setAlignment(switchSceneButton, Pos.TOP_RIGHT);
    StackPane.setMargin(switchSceneButton, new Insets(15, 15, 0, 0));

    TableView<ResultListEntry> table = new TableView<>();
    StackPane.setMargin(table, new Insets(10));
    ObservableList<TableColumn<ResultListEntry, ?>> columns = table.getColumns();

    switchSceneStackPane.getChildren().addAll(table, switchSceneButton);

    // place column
    TableColumn<ResultListEntry, String> placeColumn = new TableColumn<>("place");
    placeColumn.setCellValueFactory(x -> {
      return new SimpleStringProperty(String.valueOf(x.getValue().place));
    });
    columns.add(placeColumn);

    // file name column
    TableColumn<ResultListEntry, String> fileNameColumn = new TableColumn<>("file name");
    fileNameColumn.setCellValueFactory(x -> {
      return new SimpleStringProperty(x.getValue().file.getName());
    });
    columns.add(fileNameColumn);

    // wins column
    TableColumn<ResultListEntry, String> winsColumn = new TableColumn<>("wins");
    winsColumn.setCellValueFactory(x -> {
      return new SimpleStringProperty(String.valueOf(x.getValue().wins));
    });
    columns.add(winsColumn);

    // loses column
    TableColumn<ResultListEntry, String> losesColumn = new TableColumn<>("loses");
    losesColumn.setCellValueFactory(x -> {
      return new SimpleStringProperty(String.valueOf(x.getValue().loses));
    });
    columns.add(losesColumn);

    // ignored column
    TableColumn<ResultListEntry, String> ignoredColumn = new TableColumn<>("ignored");
    ignoredColumn.setCellValueFactory(x -> {
      return new SimpleStringProperty(String.valueOf(x.getValue().ignored));
    });
    columns.add(ignoredColumn);

    // helper functions for mp3 tag reading
    Function<ResultListEntry, File> f = rle -> rle.file;
    Function<ResultListEntry, MusicFile> toMusicFile = f.andThen(MusicFile::create);

    // artist
    TableColumn<ResultListEntry, String> artistColumn = createColumn("Artist",
        toMusicFile.andThen(MusicFile::getArtist));
    columns.add(artistColumn);

    // Album
    TableColumn<ResultListEntry, String> albumColumn = createColumn("Album",
        toMusicFile.andThen(MusicFile::getAlbum));
    columns.add(albumColumn);

    // Title
    TableColumn<ResultListEntry, String> titleColumn = createColumn("Title",
        toMusicFile.andThen(MusicFile::getTitle));
    columns.add(titleColumn);

    ObservableList<ResultListEntry> items = table.getItems();
    items.addAll(resultList);
    refresh = () -> {
      items.setAll(folder.getResultList());
    };

    setOnKeyPressed(keyEvent -> {
      KeyCode code = keyEvent.getCode();
      if (KeyCode.R.equals(code)) {
        switchSceneAction.run();
      }
    });
  }

  static MusicRankingScene createRankingScene(ImageBattleFolder folder,
      Runnable testPulseListener) {
    StackPane switchSceneStackPane = new StackPane();
    MusicRankingScene rankingScene = new MusicRankingScene(switchSceneStackPane, folder,
        testPulseListener);
    return rankingScene;
  }

  void refresh() {
    refresh.run();
  }

  private TableColumn<ResultListEntry, String> createColumn(String columnTitle,
      Function<ResultListEntry, String> function) {
    TableColumn<ResultListEntry, String> tableColumn = new TableColumn<>(columnTitle);
    tableColumn.setCellValueFactory(x -> {
      ResultListEntry resultListEntry = x.getValue();
      String cellValue = function.apply(resultListEntry);
      return new SimpleStringProperty(cellValue);
    });
    return tableColumn;
  }

}
