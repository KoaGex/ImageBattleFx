package org.imagebattle;

import java.io.File;
import java.net.URI;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

public class BattleMusicView extends GridPane {
    private static Logger log = LogManager.getLogger();

    private MediaPlayer player;

    private final Slider slider;

    private Consumer<File> fileSetter;

    public BattleMusicView(Runnable chooseAction) {
	
	getStyleClass().add("battle-music-view");

	slider = new Slider();
	Label fileNameLabel = new Label();
	Label albumNameLabel = new Label();
	Label titleNameLabel = new Label();
	Label artistNameLabel = new Label();
	fileSetter = file -> {
	    fileNameLabel.setText(file.getName());
	    MusicFile musicFile = MusicFile.create(file);
	    albumNameLabel.setText(musicFile.getAlbum());
	    titleNameLabel.setText(musicFile.getTitle());
	    artistNameLabel.setText(musicFile.getArtist());
	};

	Button playButton = new Button("play");
	playButton.setOnAction(e -> {
	    player.play();
	});
	Button stopButton = new Button("Pause");
	stopButton.setOnAction(ev -> {
	    log.debug(player.getStatus());
	    player.pause();
	    log.debug(player.getCurrentTime());
	    // slider.setValue(0.7);
	});

	Button chooseButton = new Button("Choose");
	chooseButton.setOnAction(ev -> {
	    chooseAction.run();
	});

	// current time change -> slider
	Thread thread = new Thread(() -> {
	    while (true) {
		try {
		    Thread.sleep(1000);
		    boolean pressed = slider.isPressed();
		    if (!pressed && player != null) { // Give the user time for
						      // dragging.
			Duration currentTime = player.getCurrentTime();
			double millis = currentTime.toMillis();
			Platform.runLater(() -> {
			    slider.setValue(millis);
			});
		    }
		} catch (Exception e1) {
		    e1.printStackTrace();
		}
	    }

	});
	thread.setDaemon(true);
	thread.start();

	// slider change -> current time
	slider.valueProperty().addListener(observable -> {
	    double value = slider.getValue();
	    double currentTime = player.getCurrentTime().toMillis();
	    /*
	     * to only scroll when difference is more than one second. Otherwise
	     * this would trigger little stops every second.
	     */
	    if (Math.abs(currentTime - value) > 1000) {
		log.debug("slider Change:" + value);
		player.seek(new Duration(value));
	    }

	});
	
	

	int row = 0;
	addRow(row++, playButton, stopButton);

	ObservableList<Node> children = getChildren();
	children.add(slider);
	setConstraints(slider, 0, row++);
	setHgrow(slider, Priority.ALWAYS);
	setColumnSpan(slider, 2);

	children.add(chooseButton);
	setConstraints(chooseButton, 0, row++);

	children.add(fileNameLabel);
	setConstraints(fileNameLabel, 0, row++);
	setHgrow(fileNameLabel, Priority.ALWAYS);

	// artist
	addRow(row++,new Label("Artist"),artistNameLabel);
	setHgrow(artistNameLabel, Priority.ALWAYS);

	// album
	addRow(row++,new Label("Album"),albumNameLabel);
	setHgrow(albumNameLabel, Priority.ALWAYS);

	// title
	addRow(row++,new Label("Title"),titleNameLabel);
	setHgrow(titleNameLabel, Priority.ALWAYS);

    }

    void setNewFile(File musicFile) {
	log.debug(musicFile);

	if (player != null) {
	    player.stop();
	}

	URI uri = musicFile.toURI();
	Media m = new Media(uri.toString());
	Duration duration = m.getDuration();
	log.debug(duration);
	player = new MediaPlayer(m);
	player.setStopTime(Duration.minutes(40));
	Duration stopTime = player.getStopTime();
	log.debug(stopTime);

	double dur1 = player.getMedia().getDuration().toMillis();
	log.debug("duration:" + dur1);

	m.durationProperty().addListener((a, b, c) -> {
	    log.debug("dur change:" + c);
	    slider.setMax(c.toMillis());
	});

	fileSetter.accept(musicFile);
    }
}
