package org.imagebattle;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;

import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;

public class BattleImageViewTest extends ApplicationTest {

  private BattleImageView battleImageView;

  @Override
  public void start(Stage stage) throws Exception {

    battleImageView = new BattleImageView();
    Pane pane = new StackPane(battleImageView);
    Scene scene = new Scene(pane);
    stage.setScene(scene);
    stage.show();
  }

  @Test
  public void test() {
    File wallpaperDirectory = new File("D:\\bilder\\WALLPAPERS\\1920x1080_beste\\");
    File[] wallpapers = wallpaperDirectory.listFiles();
    Iterator<File> iterator = Arrays.stream(wallpapers).iterator();
    for (int i = 0; i < 10000; i++) {
      File image;
      if (!iterator.hasNext()) {
        iterator = Arrays.stream(wallpapers).iterator();
      }
      image = iterator.next();

      battleImageView.setNewFile(image);
      sleep(100);
      System.err.println(i);
    }

  }

}
