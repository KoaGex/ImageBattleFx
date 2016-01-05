package org.imagebattle;

import java.util.Arrays;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

/**
 * 
 * Compare / rate music.
 * 
 * @author KoaGex
 *
 */
final class MusicBattleScene extends ABattleScene<BattleMusicView> {

  @Override
  void doInitializeMediaViews() {
    mediaLeft = new BattleMusicView(() -> displayNextImages(fileLeft, fileRight));
    mediaRight = new BattleMusicView(() -> displayNextImages(fileRight, fileLeft));
  }

  private MusicBattleScene(StackPane switchSceneStackPane, ImageBattleFolder folder,
      Runnable switchSceneAction) {
    super(switchSceneStackPane, folder, switchSceneAction);

    // One finishes => continue with the other song.
    mediaLeft.setOnEndOfMedia(() -> {
      mediaLeft.reset();
      mediaLeft.stop();
      mediaRight.stopResetAndPlay();
    });
    mediaRight.setOnEndOfMedia(() -> {
      mediaRight.reset();
      mediaRight.stop();
      mediaLeft.stopResetAndPlay();
    });

    HBox hBoxLeft = new HBox(ignoreLeft);
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

    HBox hBoxRight = new HBox(ignoreRight);
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

    hBox.getChildren().add(mediaLeft);
    hBox.getChildren().add(mediaRight);
    HBox.setHgrow(mediaLeft, Priority.ALWAYS);
    HBox.setHgrow(mediaRight, Priority.ALWAYS);
    StackPane.setMargin(hBox, new Insets(80, 15, 15, 15));

    hBox.setAlignment(Pos.CENTER);
    HBox.setMargin(mediaLeft, new Insets(30));
    HBox.setMargin(mediaRight, new Insets(30));

  }

  static MusicBattleScene createBattleScene(ImageBattleFolder folder, Runnable switchSceneAction) {
    StackPane switchSceneStackPane = new StackPane();
    return new MusicBattleScene(switchSceneStackPane, folder, switchSceneAction);
  }

}