package org.imagebattle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

/**
 * A scene for a dialog to choose on directory for your next battle.
 * 
 * On the left is a tree structure of your folders.
 * 
 * To the right is an area for previewing the folders files relevant to the
 * chosen battle type.
 * 
 * Bottom line: Ok button and checkbox for recursive.
 * 
 * @author KoaGex
 *
 */
final class DirectoryChooserScene extends Scene {
    private static final Logger LOG = LogManager.getLogger();
    /**
     * Directory tree of folder that contain files matching the battle type.
     */
    private final TreeView<DirectoryChooserFile> treeView = new TreeView<>();

    static DirectoryChooserScene create(String fileRegex, BiConsumer<File, Boolean> confirmAction) {
	BorderPane borderPane = new BorderPane();
	return new DirectoryChooserScene(borderPane, fileRegex, confirmAction);
    }

    private DirectoryChooserScene(BorderPane borderPane, String fileRegex, BiConsumer<File, Boolean> confirmAction) {
	super(borderPane);

	TreeItem<DirectoryChooserFile> rootItem = buildDirectoryTree(fileRegex);
	treeView.setRoot(rootItem);
	rootItem.setExpanded(true);

	FlowPane flowPane = new FlowPane();
	ScrollPane scrollPane = new ScrollPane(flowPane);
	scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
	scrollPane.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
	flowPane.prefWidthProperty().bind(scrollPane.widthProperty().subtract(30));

	SplitPane splitPane = new SplitPane(treeView, scrollPane);

	/*
	 * If 10 percent are less than 250 pixels the divider will be set to 250
	 * pixels from the left.
	 */
	treeView.setMinWidth(250);
	splitPane.setDividerPositions(0.10);
	// MinWidth does not affect scrollPane. It just shows a scrollbar.

	borderPane.setCenter(splitPane);

	Button okButton = new Button("ok");
	okButton.setDefaultButton(true);

	CheckBox recursiveCheckbox = new CheckBox("Inlude files in subdirectories");
	// Defaulty use recursive mode. Should be used in most cases.
	recursiveCheckbox.setSelected(true);

	okButton.setOnAction(event -> {
	    MultipleSelectionModel<TreeItem<DirectoryChooserFile>> selectionModel = treeView.getSelectionModel();
	    DirectoryChooserFile chosenDirectory = selectionModel.getSelectedItem().getValue();

	    confirmAction.accept(chosenDirectory, recursiveCheckbox.isSelected());
	});

	HBox hBox = new HBox(recursiveCheckbox, okButton);
	HBox.setMargin(okButton, new Insets(5));
	hBox.setAlignment(Pos.CENTER);
	borderPane.setBottom(hBox);

	// Use this to avoid mixing images of multiple folders.
	StringProperty currentDirectory = new SimpleStringProperty();
	// List bind/unbind does not work. Need old list to remove binding.

	treeView.getSelectionModel().selectedItemProperty().addListener((a, b, newSelected) -> {
	    LOG.trace(newSelected);
	    // TODO recursively get all images?

	    DirectoryChooserFile value = newSelected.getValue();

	    String absolutePath = value.getAbsolutePath();
	    currentDirectory.setValue(absolutePath);

	    List<File> images = value.images;
	    ObservableList<Node> children = flowPane.getChildren();
	    children.clear();
	    Runnable convertAndAdd = () -> {
		for (File file : images) {
		    // If directory changed, do not add images of old directory.
		    if (absolutePath.equals(currentDirectory.get())) {
			ImageView imageView = loadImagePreview(file);
			Platform.runLater(() -> {
			    children.add(imageView);
			});
		    }
		}
	    };

	    Thread thread = new Thread(convertAndAdd);
	    thread.start();
	});
	treeView.requestFocus();
    }

    /**
     * @param file
     *            Load this image file.
     * @return {@link ImageView} in bounded size.
     */
    private ImageView loadImagePreview(File file) {

	ImageView imageView = new ImageView();
	LOG.trace(file.getName());
	boolean smooth = true;
	boolean preserveRatio = true;
	Image image;
	try {
	    FileInputStream fis = new FileInputStream(file);
	    image = new Image(fis, 500d, 200, preserveRatio, smooth);
	} catch (FileNotFoundException e) {
	    throw new UncheckedIOException(e);
	}

	imageView.setImage(image);
	imageView.setPreserveRatio(true);
	imageView.setFitHeight(200);
	FlowPane.setMargin(imageView, new Insets(3));
	return imageView;
    }

    private TreeItem<DirectoryChooserFile> buildDirectoryTree(String fileRegex) {

	TreeItem<DirectoryChooserFile> rootItem = new TreeItem<>(new DirectoryChooserFile("D:\\", fileRegex));
	Path start = Paths.get("D:\\");
	Pattern pattern = Pattern.compile(fileRegex);
	Runnable walk = () -> {
	    try {
		// A normal Files.walk stumbles over errors it can not handle.
		Files.walkFileTree(start, new FileVisitor<Path>() {

		    @Override
		    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			boolean isDollarDir = dir.toFile().getAbsolutePath().contains("$");
			return isDollarDir ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
		    }

		    @Override
		    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			boolean isImage = pattern.matcher(file.toFile().getAbsolutePath().toUpperCase()).matches();
			if (isImage) {
			    Platform.runLater(() -> {
				createTreeItem(rootItem, file.toFile(), fileRegex);
				LOG.trace("success: {}", file);
			    });
			}
			return FileVisitResult.CONTINUE;
		    }

		    @Override
		    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
			LOG.debug("failed: {}", file);
			return FileVisitResult.CONTINUE;
		    }

		    @Override
		    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			return FileVisitResult.CONTINUE;
		    }
		});

	    } catch (IOException e) {
		throw new UncheckedIOException(e);
	    }
	    LOG.debug("tree building finished");
	};

	Thread thread = new Thread(walk);
	thread.start();

	return rootItem;
    }

    private void createTreeItem(TreeItem<DirectoryChooserFile> rootItem, File imageFile, String regex) {
	List<File> list = new LinkedList<>();
	File f = imageFile;
	while (f != null) {
	    list.add(0, f);
	    f = f.getParentFile();
	}

	TreeItem<DirectoryChooserFile> treeItem = rootItem;
	for (File file2 : list) {
	    String absolutePath = file2.getAbsolutePath();
	    // System.out.println(" - " + absolutePath);
	    if (file2.equals(treeItem.getValue())) {
		continue;
	    } else {
		// if not present create , if directory navigate
		// down
		if (file2.isDirectory()) {
		    boolean noneMatch = treeItem.getChildren().stream()//
			    .map(TreeItem::getValue)//
			    .noneMatch(file2::equals);
		    if (noneMatch) {
			DirectoryChooserFile value = new DirectoryChooserFile(absolutePath, regex);
			TreeItem<DirectoryChooserFile> newTreeItem = new TreeItem<>(value);
			treeItem.getChildren().add(newTreeItem);
		    }

		    // lookup
		    treeItem = treeItem.getChildren().stream()//
			    .filter(child -> file2.equals(child.getValue()))//
			    .findFirst()//
			    .get();

		    treeItem.getValue().incrementRecursiveImageCount();
		}
	    }
	}
    }

}
