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
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

final class DirectoryChooserScene extends Scene {
    private static Logger log = LogManager.getLogger();
    private TreeView<DirectoryChooserFile> treeView = new TreeView<>();

    static DirectoryChooserScene create(String fileRegex, BiConsumer<File, Boolean> confirmAction) {
	BorderPane borderPane = new BorderPane();
	return new DirectoryChooserScene(borderPane, fileRegex, confirmAction);
    }

    private DirectoryChooserScene(BorderPane borderPane, String fileRegex, BiConsumer<File, Boolean> confirmAction) {
	super(borderPane);

	TreeItem<DirectoryChooserFile> rootItem = buildDirectoryTree(fileRegex);
	treeView.setRoot(rootItem);
	rootItem.setExpanded(true);

	borderPane.setLeft(treeView);
	FlowPane flowPane = new FlowPane();
	ScrollPane scrollPane = new ScrollPane(flowPane);
	scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
	scrollPane.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
	flowPane.prefWidthProperty().bind(scrollPane.widthProperty().subtract(30));

	borderPane.setCenter(scrollPane);

	Button okButton = new Button("ok");
	okButton.setDefaultButton(true);

	CheckBox recursiveCheckbox = new CheckBox("Inlude files in subdirectories");

	okButton.setOnAction(event -> {
	    MultipleSelectionModel<TreeItem<DirectoryChooserFile>> selectionModel = treeView.getSelectionModel();
	    DirectoryChooserFile chosenDirectory = selectionModel.getSelectedItem().getValue();

	    confirmAction.accept(chosenDirectory, recursiveCheckbox.isSelected());
	});

	borderPane.setBottom(new HBox(okButton, recursiveCheckbox));

	treeView.getSelectionModel().selectedItemProperty().addListener((a, b, newSelected) -> {
	    System.out.println(newSelected);
	    List<File> images = newSelected.getValue().images;
	    ObservableList<Node> children = flowPane.getChildren();
	    children.clear();
	    List<ImageView> collect = images.stream().map(file -> {

		ImageView imageView = new ImageView();
		log.trace(file.getName());
		boolean smooth = true;
		boolean preserveRatio = true;
		Image image;
		try {
		    FileInputStream fis = new FileInputStream(file);
		    image = new Image(fis, 500d, 200, preserveRatio, smooth);
		} catch (FileNotFoundException e) {
		    e.printStackTrace();
		    return null;
		}

		imageView.setImage(image);
		imageView.setPreserveRatio(true);
		imageView.setFitHeight(200);
		FlowPane.setMargin(imageView, new Insets(3));
		return imageView;
	    }).collect(Collectors.toList());
	    children.addAll(collect);
	});
	treeView.requestFocus();
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
				log.trace("success:" + file);
			    });
			}
			return FileVisitResult.CONTINUE;
		    }

		    @Override
		    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
			System.err.println("failed:" + file);
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
	    log.debug("tree building finished");
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
