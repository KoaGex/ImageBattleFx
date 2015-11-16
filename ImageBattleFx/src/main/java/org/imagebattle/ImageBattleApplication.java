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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

public class ImageBattleApplication extends Application {

	// Issue list

	// TODO create hash values of image content to uniquely identify them after
	// moving.
	// TODO use deeplearning4j
	// TODO history for the last selected folders
	// TODO export the result list (txt, csv, html)
	// TODO battleScene: rotate images ( tried mediautil-1.0 but image size decreased)
	// TODO Hotkey overview
	// TODO do imageBattle on result of online image search
	// TODO menu->view: make all detail information like place,resolution,
	// filename optional
	// TODO use TreeSets to speed up choosers with sorting
	// TODO generic trying of the winner oriented chooser to optimize the
	// coefficients?
	// TODO in battle scene show the current ranking place ?
	// TODO ranking diashow
	// TODO ranking: click image to fullscreen/diashow , then navigate with
	// arrow keys
	// TODO ranking scene: button to un-ignore images
	// TODO threaded: getNextCandidates before user makes decision, load them
	// and then check if still valid
	// TODO cache images to improve speed ( mostly when opening the ranking
	// scene )
	// TODO Buttons with ICON for switching scenes
	// TODO error handling: directory/images were moved
	// TODO can the process be speed up even more for many images ( >200 ) ?
	// TODO context menu: delete on file system
	// TODO Diagrams of wins and loses to display total progress
	// TODO compare videos
	// TODO all actions as icon buttons
	// TODO move decision count into one new dialog together with edge count,
	// and other information about how far the battle has come.
	// TODO remember dimensions for each scene
	// TODO right-click -> rename in results scene
	// TODO when rating scene is slim ( heigth > 2* width ) display images below
	// each other
	// TODO F11 hotkey for fullscreen ( keep that mode when switching )
	// TODO when rating is finished only show ranking without switch button
	// TODO JUnit
	// TODO rewrite using spark for a more centralized way => how to handle
	// multiple users rating the same images? each has own graph but candidate
	// choosing looks at others graphs
	// TODO favorite images (star/heart) for photo album? how to display in results?
	// TODO take command line args to directly start image/music and provide a directory.
	// TODO build: package with jre, maybe javapackager?
	// TODO Use D3.js and WebView to display the current graph (to help users understand the concept and to debug )
	private static final Predicate<File> musicPredicate = createFileRegexChecker(".*\\.(MP3|OGG)");

	public static final Predicate<File> imagePredicate = createFileRegexChecker(".*\\.(BMP|GIF|JPEG|JPG|PNG)");

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
		 * What happens? A Window shows with buttons that let the user choose
		 * which kind of files should battle. There should be a label asking the
		 * user to make a choice.
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
			showDirectoryChooser(musicPredicate, MusicBattleScene::createBattleScene,
					MusicRankingScene::createRankingScene);
		});
		imagesButton.setOnAction(event -> {
			log.info("battle type: images");
			showDirectoryChooser(imagePredicate, BattleScene::createBattleScene, RankingScene::createRankingScene);
		});

		Scene battleKindChooserScene = new Scene(gridPane);

		// hotkeys
		ObservableMap<KeyCombination, Runnable> accelerators = battleKindChooserScene.getAccelerators();
		accelerators.put(new KeyCodeCombination(KeyCode.I), () -> imagesButton.getOnAction().handle(null));
		accelerators.put(new KeyCodeCombination(KeyCode.M), () -> musicButton.getOnAction().handle(null));

		battleKindChooserScene.getStylesheets().add(CSS_FILE);
		_stage.setScene(battleKindChooserScene);
		_stage.setResizable(false);
		_stage.setTitle("MediaBattle");
		_stage.centerOnScreen();
		_stage.show();

		log.debug("showing battle kind chooser");
	}

	private void showDirectoryChooser(Predicate<File> fileRegex,
			BiFunction<ImageBattleFolder, Runnable, Scene> ratingSceneCreator,
			BiFunction<ImageBattleFolder, Runnable, Scene> rankingSceneCreator) {

		BiConsumer<File, Boolean> confirmAction = (file, recursive) -> startBattle(file, ratingSceneCreator, fileRegex,
				rankingSceneCreator, recursive);
		DirectoryChooserScene directoryChooserScene = DirectoryChooserScene.create(fileRegex, confirmAction);

		directoryChooserScene.getStylesheets().add(CSS_FILE);

		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		_stage.setWidth(screenSize.getWidth() - 100);
		_stage.setHeight(screenSize.getHeight() - 100);
		_stage.setResizable(true);
		_stage.centerOnScreen();
		_stage.setScene(directoryChooserScene);
	}

	private void startBattle(File dir, BiFunction<ImageBattleFolder, Runnable, Scene> ratingSceneCreator,
			Predicate<File> fileRegex, BiFunction<ImageBattleFolder, Runnable, Scene> rankingSceneCreator,
			Boolean recursive) {

		if (dir == null) {
			System.exit(1);
		}
		String newTitle = IMAGE_BATTLE + String.format(" ( %s ) ", dir.getAbsolutePath());

		// initialize
		_stage.setTitle(newTitle);
		// _stage.setFullScreen(true);

		// gather images
		CentralStorage centralStorage = new CentralStorage(CentralStorage.GRAPH_FILE, CentralStorage.IGNORE_FILE);
		imageBattleFolder = new ImageBattleFolder(centralStorage, dir, fileRegex, recursive);

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
