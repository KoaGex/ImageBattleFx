package org.imagebattle;

import java.io.File;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.Assert;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;

public class BattleMusicViewTest extends ApplicationTest {
  private BattleMusicView battleMusicView;

  @Override
  public void start(Stage stage) throws Exception {
    Runnable chooseAction = () -> System.err.println("hu");

    battleMusicView = new BattleMusicView(chooseAction);
    Scene scene = new Scene(battleMusicView);
    stage.setScene(scene);
    stage.show();

  }

  @Test(timeout = 5000)
  public void test() {
    SimpleBooleanProperty bool = new SimpleBooleanProperty();
    bool.set(false);
    battleMusicView.setOnEndOfMedia(() -> {
      bool.set(true);
    });

    File file = new File("D:\\tmp\\fur-elise-1.mp3");
    Platform.runLater(() -> {
      battleMusicView.setNewFile(file);
      battleMusicView.play();
    });

    sleep(3300);
    Assert.assertTrue(bool.get());

  }
}
