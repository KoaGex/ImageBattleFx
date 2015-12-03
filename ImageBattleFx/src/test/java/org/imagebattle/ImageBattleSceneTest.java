package org.imagebattle;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.testfx.framework.junit.ApplicationTest;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;

/**
 * Test for {@link ImageBattleScene}.
 * 
 * @author KoaGex
 *
 */
public class ImageBattleSceneTest extends ApplicationTest {
	@Rule
	public TemporaryFolder tf = new TemporaryFolder();

	private final BooleanProperty switched = new SimpleBooleanProperty(false);

	Path ignorePath = Paths.get(System.getProperty("user.home"), CentralStorageTest.IGNORE_FILE_TEST);
	Path graphPath = Paths.get(System.getProperty("user.home"), CentralStorageTest.GRAPH_FILE_TEST);
	CentralStorage centralStorage;

	@Before
	public void setUp() throws Exception {
		centralStorage = new CentralStorage(CentralStorageTest.GRAPH_FILE_TEST, CentralStorageTest.IGNORE_FILE_TEST);
	}

	@After
	public void tearDown() throws Exception {
		Files.deleteIfExists(graphPath);
		Files.deleteIfExists(ignorePath);
	}

	@Override
	public void start(Stage stage) throws Exception {
		Runnable switchSceneAction = () -> switched.set(true);

		// File fileA = tf.newFile("fileA.jpg");
		// JPEGCreator.generateToFile(fileA);
		// File fileB = tf.newFile("fileB.jpg");
		// JPEGCreator.generateToFile(fileB);

		CentralStorage centralStorage = new CentralStorage(CentralStorageTest.GRAPH_FILE_TEST,
				CentralStorageTest.IGNORE_FILE_TEST);
		Boolean recursive = false;
		File folderDir = new File("src/test/resources");
		Assume.assumeTrue(folderDir.exists());
		ImageBattleScene battleScene = ImageBattleScene.createBattleScene(
				new ImageBattleFolder(centralStorage, folderDir, ImageBattleApplication.imagePredicate, recursive),
				switchSceneAction);

		stage.setScene(battleScene);
		stage.setWidth(1800);
		stage.setHeight(900);
		stage.show();
	}

	@Test
	public void switchAfterFinish() {
		Set<Node> images = lookup(".image-view").queryAll();
		ArrayList<Node> list = new ArrayList<>(images);
		clickOn(list.get(0), MouseButton.PRIMARY);
		clickOn(list.get(1), MouseButton.PRIMARY);
		if (!switched.get()) {
			clickOn(list.get(0), MouseButton.PRIMARY);
		}
		// for testing this needs real images
		assertThat(switched.get(), is(true));
	}

}
