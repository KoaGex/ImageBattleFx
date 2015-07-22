package org.imagebattle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextFlow;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Complete scene with all content displaying the ranking order.
 * 
 * Should only be one element => singleton. Needs contact to the folder. => factory?
 * 
 * @author Besitzer
 *
 */
public class RankingScene extends Scene {
	private static Logger log = LogManager.getLogger();

	private ObservableList<Node> resultsVboxChildren;
	private ImageBattleFolder imageBattleFolder;
	private final Map<File, Image> imagesMap = new HashMap<File, Image>();

	private RankingScene(StackPane switchSceneStackPane, ImageBattleFolder folder, Runnable switchSceneAction) {
		super(switchSceneStackPane);
		imageBattleFolder = folder;

		Button switchSceneButton = new Button("Vergleiche");
		switchSceneButton.setOnAction(event -> switchSceneAction.run());
		StackPane.setAlignment(switchSceneButton, Pos.TOP_RIGHT);
		StackPane.setMargin(switchSceneButton, new Insets(15, 15, 0, 0));

		ScrollPane scrollPane = new ScrollPane();

		switchSceneStackPane.getChildren().addAll(scrollPane, switchSceneButton);

		// create results scene
		TextFlow textFlow = new TextFlow();
		scrollPane.vvalueProperty().addListener((a, b, neW) -> {
			if (neW.doubleValue() > 0.9) {
				addImagesToResultsScene();
			}
		});
		scrollPane.setFitToWidth(true);
		scrollPane.setHbarPolicy(ScrollBarPolicy.NEVER);
		scrollPane.setContent(textFlow);
		resultsVboxChildren = textFlow.getChildren();

		setOnKeyPressed(keyEvent -> {
			KeyCode code = keyEvent.getCode();
			if (KeyCode.R.equals(code)) {
				switchSceneAction.run();
			}
		});
	}

	static RankingScene createRankingScene(ImageBattleFolder folder, Runnable testPulseListener) {
		StackPane switchSceneStackPane = new StackPane();
		RankingScene rankingScene = new RankingScene(switchSceneStackPane, folder, testPulseListener);
		return rankingScene;
	}

	private void addImagesToResultsScene() {
		int alreadyAdded = resultsVboxChildren.size();
		// display the newest result states
		List<ResultListEntry> resultList = imageBattleFolder.getResultList();
		resultList.stream().skip(alreadyAdded).limit(10).forEach(entry -> {

			Label label = new Label();
			label.setText(String.format("place: %3d.   wins: %3d   loses: %3d", entry.place, entry.wins, entry.loses));
			ImageView imageView = new ImageView();
			File file = entry.file;
			log.debug(file.getName());
			boolean smooth = true;
			boolean preserveRatio = true;
			Image image = imagesMap.computeIfAbsent(file, key -> {
				try {
					FileInputStream fis = new FileInputStream(key);
					return new Image(fis, 500d, 250d, preserveRatio, smooth);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					return null;
				}
			});

			imageView.setImage(image);
			imageView.setPreserveRatio(true);
			imageView.setFitHeight(250d);

			// TODO only show images, information as tooltip

				StackPane stackPane = new StackPane();

				String placeLabelText = entry.ignored ? "ignore" : String.valueOf(entry.place);
				Label placeLabel = new Label(placeLabelText);
				placeLabel.getStyleClass().add("place-label");
				stackPane.getChildren().addAll(imageView, placeLabel);
				StackPane.setMargin(imageView, new Insets(5d));
				StackPane.setAlignment(placeLabel, Pos.TOP_LEFT);

				String toolTipText = "wins: " + entry.wins + "\nloses: " + entry.loses + "\nFile: " + file.getName();

				Tooltip toolTip = new Tooltip(toolTipText);
				if (!entry.ignored) {
					Tooltip.install(stackPane, toolTip);
				}

				resultsVboxChildren.add(stackPane);

			});
	}

	void refresh() {
		// remove old list

		resultsVboxChildren.clear();

		IntStream.range(0, 6).forEach(i -> addImagesToResultsScene());

	}

}