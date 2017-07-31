package org.imagebattle;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * <p>
 * Beschreibung</>
 * 
 * @author Besitzer
 *
 */
public class WebViewTest extends Application {

  @Override
  public void start(Stage stage) throws Exception {
    WebView webView = new WebView();
    WebEngine engine = webView.getEngine();
    engine.load(new File(
        "C:\\java_workspaces\\media_battle\\ImageBattleFx\\ImageBattleFx\\src\\main\\resources\\audio.html")
            .toURI().toURL().toString());
    stage.setScene(new Scene(webView));
    stage.show();
    Timer timer = new Timer();
    TimerTask t = new TimerTask() {

      @Override
      public void run() {
        System.err.println("reload");
        Platform.runLater(() -> engine.reload());
      }
    };
    // timer.scheduleAtFixedRate(t, 2000, 5000);

    engine.setOnAlert(event -> {
      String data = event.getData();
      System.err.println(data);
      engine.reload();

      Document document = engine.getDocument();
      Element audio1 = document.getElementById("audio1");
      audio1.setAttribute("src", ""); // TODO change audio object source
    });
  }

  public static void main(String[] args) {
    launch(args);
  }
}
