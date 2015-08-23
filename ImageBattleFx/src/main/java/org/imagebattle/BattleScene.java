package org.imagebattle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.beans.property.DoubleProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Pair;

/**
 * This is where two images are displayed next to each other and the user can
 * click one of them. Then the next pair is displayed.
 * 
 * @author KoaGex
 *
 */
public class BattleScene extends Scene {

    private static Logger log = LogManager.getLogger();

    private final ImageBattleFolder imageBattleFolder;
    private final BattleImageView imageViewLeft;
    private final BattleImageView imageViewRight;
    private File fileLeft;
    private File fileRight;

    private final DoubleProperty progressProperty;

    public BattleScene(StackPane switchSceneStackPane, ImageBattleFolder folder, Runnable switchSceneAction) {
	super(switchSceneStackPane);
	imageBattleFolder = folder;

	HBox hBox = new HBox();

	Menu choosingMenu = new Menu("Choosing Algorithm");

	ObservableList<MenuItem> choosingMenuItems = choosingMenu.getItems();
	Function<String, MenuItem> createChoosingMenuItem = name -> {
	    MenuItem menuItem = new MenuItem(name);
	    menuItem.setOnAction(event -> folder.setChoosingAlgorithm(name));
	    return menuItem;
	};
	folder.getChoosingAlgorithms().stream().map(createChoosingMenuItem).forEach(choosingMenuItems::add);

	// progressbar
	ProgressBar progressBar = new ProgressBar();
	progressBar.setMinWidth(100d); // high with causes text to become "..."
	progressProperty = progressBar.progressProperty();
	Menu progressMenu = new Menu();
	Tooltip.install(progressBar, new Tooltip("progress of the whole image battle"));
	/*
	 * i don't know a good way to use Math.round with property.bind => use
	 * listener
	 */
	progressProperty.addListener((prop, old, newValue) -> {
	    double progress = progressProperty.get();
	    double commaDigitFactor = 1000d;
	    double percent = progress * 100 * commaDigitFactor;
	    percent = Math.round(percent) / commaDigitFactor;
	    progressMenu.textProperty().set(percent + "%");
	});

	progressMenu.setGraphic(progressBar);

	MenuBar menuBar = new MenuBar(choosingMenu, progressMenu);
	StackPane.setAlignment(menuBar, Pos.TOP_CENTER);

	Button switchSceneButton = createButton("Ranking", switchSceneAction);
	StackPane.setAlignment(switchSceneButton, Pos.TOP_RIGHT);
	StackPane.setMargin(switchSceneButton, new Insets(25, 15, 0, 0));

	imageViewLeft = new BattleImageView();
	imageViewRight = new BattleImageView();

	Label resolutionLabelLeft = imageViewLeft.getResolutionLabel();

	Label resolutionLabelRight = imageViewRight.getResolutionLabel();
	StackPane.setAlignment(resolutionLabelRight, Pos.BOTTOM_RIGHT);

	Function<String, Function<Runnable, Button>> creatButton2 = s -> run -> createButton(s, run);

	Function<Supplier<File>, Runnable> ignoreAction = supplier -> () -> ignoreAndShowNext(supplier.get());
	Function<Runnable, Button> createButton = creatButton2.apply("ignore");
	Function<Supplier<File>, Button> createIgnoreButton = ignoreAction.andThen(createButton);

	Button ignoreLeft = createIgnoreButton.apply(this::getFileLeft);
	Button ignoreRight = createIgnoreButton.apply(this::getFileRight);

	ImageView fullSizeImage = new ImageView();
	fullSizeImage.setPreserveRatio(true);
	fullSizeImage.fitHeightProperty().bind(this.heightProperty());
	fullSizeImage.fitWidthProperty().bind(this.widthProperty());

	Runnable removeFocusImage = () -> switchSceneStackPane.getChildren().remove(fullSizeImage);
	fullSizeImage.setOnMouseClicked(event -> removeFocusImage.run());

	Function<Supplier<File>, Supplier<Image>> fileToImageSupplier = imageSupplier -> () -> {
	    try {
		File file = imageSupplier.get();
		FileInputStream fis = new FileInputStream(file);
		return new Image(fis);
	    } catch (FileNotFoundException e1) {
		throw new UncheckedIOException(e1);
	    }
	};
	Function<Supplier<Image>, Runnable> imageSupplierToMaximizeAction = imageSupplier -> () -> {
	    Image image = imageSupplier.get();
	    fullSizeImage.setImage(image);
	    ObservableList<Node> children = switchSceneStackPane.getChildren();
	    children.remove(fullSizeImage);// avoid duplicate children
					   // exception, also left can replaced
					   // by right.
	    children.add(fullSizeImage);
	};

	Function<Supplier<File>, Runnable> fileSupplierToMaximize = fileToImageSupplier
		.andThen(imageSupplierToMaximizeAction);

	Runnable maximizeLeft = fileSupplierToMaximize.apply(this::getFileLeft);
	Runnable maximizeRight = fileSupplierToMaximize.apply(this::getFileRight);

	// zoom buttons
	Function<Runnable, Button> zoomButton = creatButton2.apply("zoom");
	Button zoomLeft = zoomButton.apply(maximizeLeft);
	Button zoomRight = zoomButton.apply(maximizeRight);

	HBox hBoxLeft = new HBox(resolutionLabelLeft, ignoreLeft, zoomLeft);
	StackPane.setAlignment(hBoxLeft, Pos.BOTTOM_LEFT);
	hBoxLeft.setAlignment(Pos.BOTTOM_LEFT);
	// this does not help: vBoxLeft.setMouseTransparent(true); // the vBox
	// does not catch mouse events. Without it click on images would be
	// catched by this.
	int hBoxHeight = 63;
	int hBoxWidth = 300;
	hBoxLeft.setMaxHeight(hBoxHeight); // TODO this should only be
					   // workaround
	hBoxLeft.setMaxWidth(hBoxWidth);

	HBox hBoxRight = new HBox(zoomRight, ignoreRight, resolutionLabelRight);
	StackPane.setAlignment(hBoxRight, Pos.BOTTOM_RIGHT);
	hBoxRight.setAlignment(Pos.BOTTOM_RIGHT);
	// this does not help: vBoxLeft.setMouseTransparent(true); // the vBox
	// does not catch mouse events. Without it click on images would be
	// catched by this.
	hBoxRight.setMaxHeight(hBoxHeight); // TODO this should only be
					    // workaround
	hBoxRight.setMaxWidth(hBoxWidth);

	// ignore both
	Button ignoreBoth = new Button("ignore both");
	ignoreBoth.setOnAction(event -> {
	    imageBattleFolder.ignoreFile(fileLeft);
	    imageBattleFolder.ignoreFile(fileRight);
	    displayNextImages(null, null);
	});
	StackPane.setAlignment(ignoreBoth, Pos.BOTTOM_CENTER);

	switchSceneStackPane.getChildren().addAll(hBox, hBoxLeft, hBoxRight, ignoreBoth, menuBar, switchSceneButton);

	displayNextImages(null, null);

	imageViewLeft.setOnMouseClicked(e -> {
	    displayNextImages(fileLeft, fileRight);
	});
	imageViewRight.setOnMouseClicked(e -> {
	    displayNextImages(fileRight, fileLeft);
	});

	widthProperty().addListener((a, b, c) -> adaptImageSizes());
	heightProperty().addListener((a, b, c) -> adaptImageSizes());

	hBox.getChildren().add(imageViewLeft);
	hBox.getChildren().add(imageViewRight);

	hBox.setAlignment(Pos.CENTER);

	setOnKeyPressed(keyEvent -> {

	    KeyCode code = keyEvent.getCode();

	    switch (code) {
	    case LEFT:
		removeFocusImage.run();
		displayNextImages(fileLeft, fileRight);
		break;

	    case RIGHT:
		removeFocusImage.run();
		displayNextImages(fileRight, fileLeft);
		break;

	    case H:
		maximizeLeft.run();
		break;

	    case L:
		maximizeRight.run();
		break;

	    case R:
		removeFocusImage.run();
		switchSceneAction.run();
		break;

	    case S: // for skip
		removeFocusImage.run();
		displayNextImages(null, null);
	    default:
		break;
	    }
	});

	adaptImageSizes();
    }

    static BattleScene createBattleScene(ImageBattleFolder folder, Runnable switchSceneAction) {
	StackPane switchSceneStackPane = new StackPane();
	return new BattleScene(switchSceneStackPane, folder, switchSceneAction);
    }

    private void displayNextImages(File pWinner, File pLoser) {
	if (pWinner != null && pLoser != null) {
	    log.info("winner: {}       loser: {}", pWinner.getName(), pLoser.getName());
	    imageBattleFolder.makeDecision(pWinner, pLoser);

	    // persist
	    imageBattleFolder.save();
	    int humanDecisionCount = imageBattleFolder.getHumanDecisionCount();
	    log.trace("decisions made so far:" + humanDecisionCount);

	}

	double progress = imageBattleFolder.getProgress();
	progressProperty.set(progress);
	log.trace("progress: {}", progressProperty.get());

	try {
	    Pair<File, File> next = imageBattleFolder.getNextToCompare();
	    fileLeft = next.getKey();
                            	    fileRight = next.getValue();

	    imageViewLeft.setNewImage(fileLeft);
	    imageViewRight.setNewImage(fileRight);

	    adaptImageSizes();
	} catch (BattleFinishedException e) {
	    // TODO switch the scene and don't let the user come back
	} catch (NoSuchElementException | FileNotFoundException e) {
	    e.printStackTrace();
	}

    }

    private void ignoreAndShowNext(File fileToIgnore) {
	imageBattleFolder.ignoreFile(fileToIgnore);
	displayNextImages(null, null);
    }

    /**
     * Choose the image sizes i a way that give both images the same amount of
     * pixels
     */
    private void adaptImageSizes() {

	double windowWidth = getWidth();
	double windowHeight = getHeight();

	// window dimensions are 0 if it is not yet displayed
	if (windowWidth > 0 && windowHeight > 0) {

	    Image imageLeft = imageViewLeft.getImage();
	    double leftWidth = imageLeft.getWidth();
	    double leftHeight = imageLeft.getHeight();

	    Image imageRight = imageViewRight.getImage();
	    double rightWidth = imageRight.getWidth();
	    double rightHeight = imageRight.getHeight();

	    // in the end we need a sizing factor for each image

	    // no image can be higher than the window
	    // double leftMaxHeightFactor = windowHeight / leftHeight;
	    // double rightMaxHeightFactor = windowHeight / rightHeight;

	    // both images together can't be wider than the window

	    // both images should in the end, have the same area
	    double leftArea = leftWidth * leftHeight;
	    double rightArea = rightWidth * rightHeight;
	    double leftRightFactor = leftArea / rightArea;
	    double rightAdaptedWidth = Math.sqrt(leftRightFactor) * rightWidth;
	    double rightAdaptedHeight = Math.sqrt(leftRightFactor) * rightHeight;
	    double rightAdaptedArea = rightAdaptedWidth * rightAdaptedHeight;

	    double combinedWidth = leftWidth + rightAdaptedWidth;
	    double combinedHeight = Math.max(leftHeight, rightAdaptedHeight);

	    double combinedWidthFactor = windowWidth / combinedWidth;
	    double cominedHeightFactor = windowHeight / combinedHeight;

	    double finalFactor = Math.min(cominedHeightFactor, combinedWidthFactor);
	    double newLeftWidth = leftWidth * finalFactor;
	    double newLeftHeight = leftHeight * finalFactor;
	    double newRightWidth = rightAdaptedWidth * finalFactor;
	    double newRightHeight = rightAdaptedHeight * finalFactor;

	    log.trace("widths :  {} - {}", String.valueOf(newLeftWidth), newRightWidth);
	    log.trace("heights:  {} - {}", String.valueOf(newLeftHeight), newRightHeight);

	    double newLeftArea = newLeftWidth * newLeftHeight;
	    double newRightArea = newRightWidth * newRightHeight;

	    if (Math.abs(newLeftArea - newRightArea) > 5) {
		log.warn("Unequal Areas: {0} - {1}", newRightArea, newLeftArea);
	    }

	    imageViewLeft.setFitWidth(newLeftWidth);
	    imageViewRight.setFitWidth(newRightWidth);

	}
    }

    private Button createButton(String text, Runnable action) {
	Button button = new Button(text);
	button.setOnAction(event -> action.run());
	return button;
    }

    private File getFileLeft() {
	return fileLeft;
    }

    private File getFileRight() {
	return fileRight;
    }

}