package org.imagebattle;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.io.File;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.testfx.framework.junit.ApplicationTest;

public class ImageResultsSceneTest extends ApplicationTest {
  @Rule
  public TemporaryFolder tf = new TemporaryFolder();
  private final BooleanProperty switched = new SimpleBooleanProperty(false);

  @Override
  public void start(Stage stage) throws Exception {
    Runnable switchSceneAction = () -> {
    };

    File folderDir = new File("src/test/resources");

    CentralStorage centralStorage = new CentralStorage(CentralStorage.GRAPH_FILE,
        CentralStorage.IGNORE_FILE, CentralStorage.SQLITE_FILE);
    Boolean recursive = false;
    ImageBattleFolder imageBattleFolder = new ImageBattleFolder(centralStorage, folderDir,
        MediaType.IMAGE, recursive);
    File[] testImages = folderDir.listFiles();

    // chain of wins: 1 > 2 > 3 > 4 ...
    for (int i = 0; i < testImages.length - 1; i++) {
      imageBattleFolder.makeDecision(testImages[i], testImages[i + 1]);
    }

    RankingScene scene = RankingScene.createRankingScene(imageBattleFolder, switchSceneAction);
    ImageView v;
    scene.refresh();

    stage.setScene(scene);
    stage.setWidth(1800);
    stage.setHeight(900);
    stage.show();

  }

  @Test
  public void test() {
    String query = ".switch-scene-button";
    clickOn(query, MouseButton.PRIMARY);
    Node switchButton = this.lookup(query).queryFirst();
    assertThat(switched.get(), is(false));
    assertThat(switchButton.isDisabled(), is(true));
  }
}
