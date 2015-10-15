package org.imagebattle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * One imageview in the battle. Has a label that shows the current images
 * resolution.
 * 
 * @author Besitzer
 *
 */
class BattleImageView extends ImageView {
    private static final Logger log = LogManager.getLogger();

    private StringProperty resolution;
    private Label resolutionLabel;

    public BattleImageView() {
	super.setPreserveRatio(true);

	resolutionLabel = new Label();
	resolution = resolutionLabel.textProperty();
	String resolutionLabelStyleClassName = "resolution-label";
	resolutionLabel.getStyleClass().add(resolutionLabelStyleClassName);

	this.getStyleClass().add("battle-image-view");
    }

    Label getResolutionLabel() {
	return resolutionLabel;
    }

    void setNewImage(File imageFile) throws FileNotFoundException {

	// Do not use ImageIO.read(file) because it completely reads the whole
	// image file. This would even take more time than loading javafx.Image
	// with lower resolution.

	long start = System.currentTimeMillis();
	FileInputStream fis = new FileInputStream(imageFile);
	// TODO try to improve performance even more with lower resolutions
	Image image = new Image(fis, 1920, 1080d, true, true); // this improves
							       // speed by ~
							       // 100ms but
							       // breaks the
							       // resolution
							       // reading => use
							       // SimpleImageInfo
	long end = System.currentTimeMillis();
	log.trace("needed {} ms to load image {}", (end - start), imageFile.getName());

	super.setImage(image);
	String resolution1 = "x";

	// Read resolution with SimpleImageInfo
	try {
	    start = System.currentTimeMillis();
	    SimpleImageInfo imageInfo = new SimpleImageInfo(imageFile);
	    int height = imageInfo.getHeight();
	    int width = imageInfo.getWidth();
	    end = System.currentTimeMillis();
	    log.trace("needed {} ms to read image dimension {}x{}", (end - start), width, height);

	    resolution1 = width + "x" + height;
	} catch (IOException e) {
	    log.debug("exception while reading image resolution of " + imageFile, e);
	}
	resolution1 = resolution1.replace(".0", "");
	resolution.set(resolution1);

    }

}