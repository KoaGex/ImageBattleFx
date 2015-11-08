/**
 * 
 */
package org.imagebattle;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.beans.property.DoubleProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.util.Pair;

/**
 * A battle scene shows two media items at the same time and lets the user choose which one he likes better. Afterwards
 * the next two elements are displayed.
 * 
 * Several buttons are positioned to allow ignoring, switching to the ranking scene.
 * 
 * @author KoaGex
 *
 */
abstract class ABattleScene<T extends IMediaView> extends Scene {
	private static final Logger LOG = LogManager.getLogger();

	protected File fileLeft;
	protected File fileRight;
	protected T mediaLeft;
	protected T mediaRight;
	private final ImageBattleFolder imageBattleFolder;
	private final DoubleProperty progressProperty;
	private final Runnable switchSceneAction;
	/**
	 * Using a map for the hotkeys allows to add hotkeys from super and from sub class. Also they don't need to be
	 * grouped in a switch case block within {@link #setOnKeyPressed(javafx.event.EventHandler)}.
	 */
	private final Map<KeyCode, Runnable> hotKeyMap = new HashMap<>();
	protected final Button ignoreLeft;
	protected final Button ignoreRight;
	protected Runnable doBeforeDisplayNext;

	/**
	 * Constructor
	 * 
	 * @param stackPane
	 *            Serves as the root node for the whole scene.
	 * @param folder
	 *            Determines which elements take part in the battle.
	 * @param switchSceneAction
	 *            An action that executes the switch to the ranking scene.
	 */
	protected ABattleScene(StackPane stackPane, ImageBattleFolder folder, Runnable switchSceneAction) {
		super(stackPane);
		imageBattleFolder = folder;
		this.switchSceneAction = switchSceneAction;

		Button switchSceneButton = createButton("Ranking", switchSceneAction);
		StackPane.setAlignment(switchSceneButton, Pos.TOP_RIGHT);
		StackPane.setMargin(switchSceneButton, new Insets(25, 15, 0, 0));

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

		Function<String, Function<Runnable, Button>> creatButton2 = s -> run -> createButton(s, run);
		Function<Supplier<File>, Runnable> ignoreAction = supplier -> () -> ignoreAndShowNext(supplier.get());
		Function<Runnable, Button> createButton = creatButton2.apply("ignore");
		Function<Supplier<File>, Button> createIgnoreButton = ignoreAction.andThen(createButton);
		ignoreLeft = createIgnoreButton.apply(this::getFileLeft);
		ignoreRight = createIgnoreButton.apply(this::getFileRight);

		// ignore both
		Button ignoreBoth = new Button("ignore both");
		ignoreBoth.setOnAction(event -> {
			ignoreBoth();
		});
		StackPane.setAlignment(ignoreBoth, Pos.BOTTOM_CENTER);

		progressMenu.setGraphic(progressBar);

		MenuBar menuBar = new MenuBar(choosingMenu, progressMenu);
		StackPane.setAlignment(menuBar, Pos.TOP_CENTER);

		stackPane.getChildren().addAll(ignoreBoth, menuBar, switchSceneButton);

		initializeMediaViews();

		// Key pressed -> look into hotKeyMap and execute.
		setOnKeyPressed(keyEvent -> {
			KeyCode code = keyEvent.getCode();
			Runnable action = hotKeyMap.get(code);
			if (action != null) {
				action.run();
			}
		});

		// Hotkeys
		addHotKey(KeyCode.LEFT, () -> displayNextImages(fileLeft, fileRight));
		addHotKey(KeyCode.RIGHT, () -> displayNextImages(fileRight, fileLeft));
		addHotKey(KeyCode.R, switchSceneAction);
		addHotKey(KeyCode.Y, ignoreAction.apply(this::getFileLeft));
		addHotKey(KeyCode.M, ignoreAction.apply(this::getFileRight));
		addHotKey(KeyCode.V, this::ignoreBoth);

		// Load the first candidates.
		displayNextImages(null, null);

	}

	/**
	 * Uses {@link ImageBattleFolder#ignoreFile(File)} for both files , none of them wins and displays the next files.
	 */
	private final void ignoreBoth() {
		imageBattleFolder.ignoreFile(fileLeft);
		imageBattleFolder.ignoreFile(fileRight);
		displayNextImages(null, null);
	}

	protected File getFileLeft() {
		return fileLeft;
	}

	protected File getFileRight() {
		return fileRight;
	}

	protected void displayNextImages(File pWinner, File pLoser) {
		if (doBeforeDisplayNext != null) {
			doBeforeDisplayNext.run();
		}

		if (pWinner != null && pLoser != null) {
			LOG.info("winner: {}       loser: {}", pWinner.getName(), pLoser.getName());
			imageBattleFolder.makeDecision(pWinner, pLoser);

			// persist
			imageBattleFolder.save();
		}

		double progress = imageBattleFolder.getProgress();
		progressProperty.set(progress);
		LOG.trace("progress: {}", progressProperty.get());

		Optional<Pair<File, File>> next = imageBattleFolder.getNextToCompare();
		next.ifPresent(pair -> {
			fileLeft = pair.getKey();
			fileRight = pair.getValue();

			mediaRight.setNewFile(fileLeft);
			mediaLeft.setNewFile(fileRight);
		});

		if (!next.isPresent()) {
			imageBattleFolder.setFinished(true);
			switchSceneAction.run();
		}

	}

	abstract void doInitializeMediaViews();

	private void initializeMediaViews() {
		doInitializeMediaViews();
		Objects.requireNonNull(mediaLeft);
		Objects.requireNonNull(mediaRight);
	}

	protected Button createButton(String text, Runnable action) {
		Button button = new Button(text);
		button.setOnAction(event -> action.run());
		return button;
	}

	protected final void addHotKey(KeyCode key, Runnable action) {
		hotKeyMap.put(key, action);
	}

	private void ignoreAndShowNext(File fileToIgnore) {
		imageBattleFolder.ignoreFile(fileToIgnore);
		displayNextImages(null, null);
	}

}
