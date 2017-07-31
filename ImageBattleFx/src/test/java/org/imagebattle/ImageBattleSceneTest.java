package org.imagebattle;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.testfx.framework.junit.ApplicationTest;

/**
 * Test for {@link ImageBattleScene}.
 * 
 * @author KoaGex
 *
 */
public class ImageBattleSceneTest extends ApplicationTest {
  @Rule
  public TemporaryFolder tf = new TemporaryFolder();

  @Rule
  public CentralStorageRule storageRule = new CentralStorageRule();

  private final BooleanProperty switched = new SimpleBooleanProperty(false);

  @Override
  public void start(Stage stage) throws Exception {
    Runnable switchSceneAction = () -> switched.set(true);

    // File fileA = tf.newFile("fileA.jpg");
    // JPEGCreator.generateToFile(fileA);
    // File fileB = tf.newFile("fileB.jpg");
    // JPEGCreator.generateToFile(fileB);

    CentralStorage centralStorage = storageRule.centralStorage();
    Boolean recursive = false;
    File folderDir = new File("src/test/resources");
    Assume.assumeTrue(folderDir.exists());
    ImageBattleScene battleScene = ImageBattleScene.createBattleScene(
        new ImageBattleFolder(centralStorage, folderDir, MediaType.IMAGE, recursive, "name"),
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
