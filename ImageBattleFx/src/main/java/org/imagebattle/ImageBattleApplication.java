package org.imagebattle;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.File;

import javafx.application.Application;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ImageBattleApplication extends Application {

	private static String IMAGE_BATTLE = "Image Battle ";

	// Issue list


	// TODO export the result list (txt, csv, html)
	// TODO battleScene: rotate images
	// TODO Hotkey overview
	// TODO do imageBattle on result of online image search
	// TODO menu->view: make all detail information like place,resolution, filename optional
	// TODO better fileChooser that shows image count, maybe previews?! treeView
	// TODO use TreeSets to speed up choosers with sorting
	// TODO generic trying of the winner oriented chooser to optimize the coefficients?
	// TODO chooser: choose images with close to each other resolution (product)
	// TODO in battle scene show the current ranking place ?
	// TODO ranking diashow
	// TODO ranking: click image to fullscreen/diashow , then navigate with arrow keys
	// TODO hotkeys for ignore left,right,both
	// TODO ranking scene: button to un-ignore images
	// TODO threaded: getNextCandidates before user makes decision, load them and then check if still valid
	// TODO cache images to improve speed ( mostly when opening the ranking scene )
	// TODO hotkey to reset search ( create new file and overwrite )
	// TODO Buttons with ICON for switching scenes
	// TODO recursive compare with all sub directories
	// TODO error handling: directory with .dat was moved
	// TODO can the process be speed up even more for many images ( >200 ) ?
	// TODO context menu: delete on file system
	// TODO Diagrams of wins and loses to display total progress
	// TODO compare videos, music
	// TODO all actions as icon buttons
	// TODO identify images by hash value? to keep wins after renaming
	// TODO move decision count into one new dialog together with edge count, and other information about how far the battle has come.
	// TODO remember dimensions for each scene
	// TODO right-click -> rename in results scene
	// TODO when rating scene is slim ( heigth > 2* width ) display images below each other
	// TODO F11 hotkey for fullscreen ( keep that mode when switching )
	// TODO when rating is finished only show ranking without switch button
	// TODO speed up choosing by selecting images that should be close to each other ( maybe look at pixel count), do this as a new Choosing algorithm
	// TODO JUnit
	// TODO also show the worst pictures
	// TODO try rewrite in Javascript? FirefoxOS app?
	// TODO rewrite using spark for a more centralized way => how to handle multiple users rating the same images? each has own graph but candidate choosing looks at others graphs

	private static Logger log = LogManager.getLogger();

	Stage _stage;
	BattleScene ratingScene;
	RankingScene resultsScene;
	private double screenHeight;
	private double screenWidth;

	// private TransitiveDiGraph graph;
	private ImageBattleFolder imageBattleFolder;

	@Override
	public void start(Stage pStage) throws Exception {
		log.info("start");
		_stage = pStage;

		DirectoryChooser dc = new DirectoryChooser();
		File dir;
		boolean funPicsShortcut = log.isDebugEnabled();
		funPicsShortcut = false;
		dir = funPicsShortcut ? new File("D:\\bilder\\fun pics") : dc.showDialog(_stage);

		if (dir == null) {
			System.exit(1);
		}
		IMAGE_BATTLE += String.format(" ( %s ) ", dir.getAbsolutePath());

		// initialize
		_stage.setTitle(IMAGE_BATTLE);
		// _stage.setFullScreen(true);

		// gather images
		imageBattleFolder = ImageBattleFolder.readOrCreate(dir);

		ratingScene = BattleScene.createBattleScene(imageBattleFolder, this::showResultsScene);
		_stage.setScene(ratingScene);

		_stage.setHeight(screenHeight - 50);
		_stage.setWidth(screenWidth - 50);
		_stage.show();

		_stage.sizeToScene();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		screenHeight = screenSize.getHeight();
		screenWidth = screenSize.getWidth();

		resultsScene = RankingScene.createRankingScene(imageBattleFolder, this::showRatingScene);

		setStageLayout();

		resultsScene.getStylesheets().add("style.css");
		ratingScene.getStylesheets().add("style.css");

		Application.getUserAgentStylesheet();
		// Application.setUserAgentStylesheet(url);
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
		resultsScene.refresh();

	}

	private void showRatingScene() {
		_stage.setScene(ratingScene);
		setStageLayout();
	}

	public static void main(String[] args) {
		launch(args);
	}
}
