package org.imagebattle;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ImageBattleApplication extends Application {

  private static final Predicate<File> musicPredicate = createFileRegexChecker(".*\\.(MP3|OGG)");

  public static final Predicate<File> imagePredicate = createFileRegexChecker(
      ".*\\.(BMP|GIF|JPEG|JPG|PNG)");

  private static final String IMAGE_BATTLE = "Image Battle ";

  private static final String CSS_FILE = "style.css";

  private static Logger log = LogManager.getLogger();

  Stage _stage;
  Scene ratingScene;
  Scene resultsScene;
  private double screenHeight;
  private double screenWidth;

  // private TransitiveDiGraph graph;
  private ImageBattleFolder imageBattleFolder;

  @Override
  public void start(Stage pStage) throws Exception {
    Thread.setDefaultUncaughtExceptionHandler(ImageBattleApplication::showError);

    log.info("start");
    _stage = pStage;
    String iconFileName = "/imageBattle32.png";
    URL resource = this.getClass().getResource(iconFileName);
    log.debug("icon image url: {}", resource);
    Image icon = new Image(resource.toString());
    _stage.getIcons().add(icon);

    /*
     * What happens? A Window shows with buttons that let the user choose which kind of files should
     * battle. There should be a label asking the user to make a choice.
     */

    // Choose which kind of battle
    Label label = new Label("Which kind of files should be ranked?");
    Button imagesButton = new Button("Images");
    Button musicButton = new Button("Music");

    // GridPane
    GridPane gridPane = new GridPane();

    GridPane.setConstraints(label, 0, 0);
    GridPane.setColumnSpan(label, 2);
    GridPane.setMargin(label, new Insets(20, 20, 0, 20));

    GridPane.setConstraints(imagesButton, 0, 1);
    GridPane.setMargin(imagesButton, new Insets(20, 20, 20, 30));

    GridPane.setConstraints(musicButton, 1, 1);
    GridPane.setMargin(musicButton, new Insets(20));

    gridPane.getChildren().addAll(label, imagesButton, musicButton);

    musicButton.setOnAction(event -> {
      log.info("battle type: music");
      showDirectoryChooser(MediaType.MUSIC, MusicBattleScene::createBattleScene,
          MusicRankingScene::createRankingScene);
    });
    imagesButton.setOnAction(event -> {
      log.info("battle type: images");
      showDirectoryChooser(MediaType.IMAGE, ImageBattleScene::createBattleScene,
          RankingScene::createRankingScene);
    });

    Scene battleKindChooserScene = new Scene(gridPane);

    // hotkeys
    ObservableMap<KeyCombination, Runnable> accelerators = battleKindChooserScene.getAccelerators();
    accelerators.put(new KeyCodeCombination(KeyCode.I),
        () -> imagesButton.getOnAction().handle(null));
    accelerators.put(new KeyCodeCombination(KeyCode.M),
        () -> musicButton.getOnAction().handle(null));

    battleKindChooserScene.getStylesheets().add(CSS_FILE);
    _stage.setScene(battleKindChooserScene);
    _stage.setResizable(false);
    _stage.setTitle("MediaBattle");
    _stage.centerOnScreen();
    _stage.show();

    log.debug("showing battle kind chooser");
  }

  private void showDirectoryChooser(MediaType mediaType,
      BiFunction<ImageBattleFolder, Runnable, Scene> ratingSceneCreator, //
      BiFunction<ImageBattleFolder, Runnable, Scene> rankingSceneCreator //
  ) {

    BiConsumer<File, Boolean> confirmAction = (file, recursive) -> startBattle(file, //
        ratingSceneCreator, //
        mediaType, //
        rankingSceneCreator, //
        recursive//
    );
    DirectoryChooserScene directoryChooserScene = DirectoryChooserScene.create(mediaType::matches,
        confirmAction);

    directoryChooserScene.getStylesheets().add(CSS_FILE);

    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    _stage.setWidth(screenSize.getWidth() - 100);
    _stage.setHeight(screenSize.getHeight() - 100);
    _stage.setResizable(true);
    _stage.centerOnScreen();
    _stage.setScene(directoryChooserScene);
  }

  private void startBattle(File dir,
      BiFunction<ImageBattleFolder, Runnable, Scene> ratingSceneCreator, //
      MediaType mediaType, //
      BiFunction<ImageBattleFolder, Runnable, Scene> rankingSceneCreator, //
      Boolean recursive//
  ) {

    if (dir == null) {
      System.exit(1);
    }
    String newTitle = IMAGE_BATTLE + String.format(" ( %s ) ", dir.getAbsolutePath());

    // initialize
    _stage.setTitle(newTitle);
    // _stage.setFullScreen(true);

    // gather images
    CentralStorage centralStorage = new CentralStorage(CentralStorage.GRAPH_FILE,
        CentralStorage.IGNORE_FILE, CentralStorage.SQLITE_FILE);
    imageBattleFolder = new ImageBattleFolder(centralStorage, dir, mediaType, recursive);

    ratingScene = ratingSceneCreator.apply(imageBattleFolder, this::showResultsScene);

    _stage.setScene(ratingScene);

    _stage.setHeight(screenHeight - 50);
    _stage.setWidth(screenWidth - 50);
    _stage.show();

    _stage.sizeToScene();
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    screenHeight = screenSize.getHeight();
    screenWidth = screenSize.getWidth();

    resultsScene = rankingSceneCreator.apply(imageBattleFolder, this::showRatingScene);

    setStageLayout();

    resultsScene.getStylesheets().add(CSS_FILE);
    ratingScene.getStylesheets().add(CSS_FILE);

  }

  private void setStageLayout() {

    int stageMargin = 50;
    _stage.setHeight(screenHeight - stageMargin);
    _stage.setWidth(screenWidth - stageMargin);
    _stage.setX(stageMargin / 2);
    _stage.setY(stageMargin / 2);
  }

  private void showResultsScene() {

    // display the scene
    _stage.setScene(resultsScene);
    setStageLayout();
    if (resultsScene instanceof MusicRankingScene) {
      ((MusicRankingScene) resultsScene).refresh();
    }
    if (resultsScene instanceof RankingScene) {
      ((RankingScene) resultsScene).refresh();
    }

  }

  private void showRatingScene() {
    _stage.setScene(ratingScene);
    setStageLayout();
  }

  private static Predicate<File> createFileRegexChecker(String regex) {
    Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    return file -> {
      return pattern.matcher(file.getName()).matches();
    };
  }

  public static void main(String[] args) {
    launch(args);
  }

  private static void showError(Thread t, Throwable e) {
    System.err.println("***Default exception handler***");
    log.error("Exception caught by global exception handler:", e);
    if (Platform.isFxApplicationThread()) {
      showErrorDialog(e);
    } else {
      System.err.println("An unexpected error occurred in " + t);
    }
  }

  private static void showErrorDialog(Throwable e) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    StringWriter errorMsg = new StringWriter();
    e.printStackTrace(new PrintWriter(errorMsg));
    alert.setContentText(errorMsg.toString());
    alert.setTitle(e.getMessage());
    alert.setHeaderText(e.getMessage());
    alert.setWidth(1000);
    alert.showAndWait();
  }
}
