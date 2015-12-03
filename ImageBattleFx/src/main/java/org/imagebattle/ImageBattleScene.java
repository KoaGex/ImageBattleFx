package org.imagebattle;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * This is where two images are displayed next to each other and the user can click one of them. Then the next pair is
 * displayed.
 * 
 * @author KoaGex
 *
 */
final class ImageBattleScene extends ABattleScene<BattleImageView> {

	private static final Logger LOG = LogManager.getLogger();

	static ImageBattleScene createBattleScene(ImageBattleFolder folder, Runnable switchSceneAction) {
		StackPane switchSceneStackPane = new StackPane();
		return new ImageBattleScene(switchSceneStackPane, folder, switchSceneAction);
	}

	@Override
	void doInitializeMediaViews() {
		mediaLeft = new BattleImageView();
		mediaRight = new BattleImageView();
	}

	@Override
	protected void doAfterDisplayNext() {
		adaptImageSizes();
	}

	private ImageBattleScene(StackPane switchSceneStackPane, ImageBattleFolder folder, Runnable switchSceneAction) {
		super(switchSceneStackPane, folder, switchSceneAction);

		Label resolutionLabelLeft = mediaLeft.getResolutionLabel();

		Label resolutionLabelRight = mediaRight.getResolutionLabel();
		StackPane.setAlignment(resolutionLabelRight, Pos.BOTTOM_RIGHT);

		ImageView fullSizeImage = new ImageView();
		fullSizeImage.setPreserveRatio(true);
		fullSizeImage.fitHeightProperty().bind(this.heightProperty());
		fullSizeImage.fitWidthProperty().bind(this.widthProperty());

		Runnable removeFocusImage = () -> switchSceneStackPane.getChildren().remove(fullSizeImage);
		doBeforeDisplayNext = removeFocusImage;

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
		Function<String, Function<Runnable, Button>> creatButton2 = s -> run -> createButton(s, run);
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

		HBox hBox = new HBox();
		List<HBox> elements = Arrays.asList(hBox, hBoxLeft, hBoxRight);
		switchSceneStackPane.getChildren().addAll(0, elements);

		mediaLeft.setOnMouseClicked(mouseEvent -> {
			File thisImage = fileLeft;
			File otherImage = fileRight;
			imageClicked(mouseEvent, thisImage, otherImage);
		});
		mediaRight.setOnMouseClicked(mouseEvent -> {
			File thisImage = fileRight;
			File otherImage = fileLeft;
			imageClicked(mouseEvent, thisImage, otherImage);
		});

		widthProperty().addListener((a, b, c) -> adaptImageSizes());
		heightProperty().addListener((a, b, c) -> adaptImageSizes());

		hBox.getChildren().add(mediaLeft);
		hBox.getChildren().add(mediaRight);

		hBox.setAlignment(Pos.CENTER);

		// Hotkeys
		addHotKey(KeyCode.H, maximizeLeft);
		addHotKey(KeyCode.L, maximizeRight);

		adaptImageSizes();
	}

	private void imageClicked(MouseEvent e, File thisImage, File otherImage) {
		MouseButton button = e.getButton();
		switch (button) {
		case PRIMARY:
			displayNextImages(thisImage, otherImage);
			break;
		case SECONDARY:
			ContextMenu contextMenu = new ContextMenu();

			Consumer<File> openFile = file -> {
				try {
					Desktop.getDesktop().open(file);
				} catch (IOException e1) {
					LOG.catching(Level.WARN, e1);
					throw new UncheckedIOException(e1);
				}
			};

			MenuItem menuItemOpen = new MenuItem("open image");
			menuItemOpen.setOnAction(ev -> openFile.accept(thisImage));

			MenuItem menuItemOpenDir = new MenuItem("open directory");
			menuItemOpenDir.setOnAction(ev -> openFile.accept(thisImage.getParentFile()));

			contextMenu.getItems().addAll(menuItemOpen, menuItemOpenDir);
			contextMenu.show(this.getWindow(), e.getScreenX(), e.getScreenY());
			break;

		default:
			break;
		}
	}

	/**
	 * Choose the image sizes i a way that give both images the same amount of pixels
	 */
	private void adaptImageSizes() {

		double windowWidth = getWidth();
		double windowHeight = getHeight();

		// window dimensions are 0 if it is not yet displayed
		if (windowWidth > 0 && windowHeight > 0) {

			Image imageLeft = mediaLeft.getImage();
			Image imageRight = mediaRight.getImage();

			if (imageLeft != null && imageRight != null) {

				double leftWidth = imageLeft.getWidth();
				double leftHeight = imageLeft.getHeight();
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

				double combinedWidth = leftWidth + rightAdaptedWidth;
				double combinedHeight = Math.max(leftHeight, rightAdaptedHeight);

				double combinedWidthFactor = windowWidth / combinedWidth;
				double cominedHeightFactor = windowHeight / combinedHeight;

				double finalFactor = Math.min(cominedHeightFactor, combinedWidthFactor);
				double newLeftWidth = leftWidth * finalFactor;
				double newLeftHeight = leftHeight * finalFactor;
				double newRightWidth = rightAdaptedWidth * finalFactor;
				double newRightHeight = rightAdaptedHeight * finalFactor;

				LOG.trace("widths :  {} - {}", String.valueOf(newLeftWidth), newRightWidth);
				LOG.trace("heights:  {} - {}", String.valueOf(newLeftHeight), newRightHeight);

				double newLeftArea = newLeftWidth * newLeftHeight;
				double newRightArea = newRightWidth * newRightHeight;

				if (Math.abs(newLeftArea - newRightArea) > 5) {
					LOG.warn("Unequal Areas: {0} - {1}", newRightArea, newLeftArea);
				}

				mediaLeft.setFitWidth(newLeftWidth);
				mediaRight.setFitWidth(newRightWidth);

			}
		}
	}

}