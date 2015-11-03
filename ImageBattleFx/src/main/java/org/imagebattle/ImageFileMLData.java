package org.imagebattle;

import java.awt.Image;
import java.io.File;

import org.encog.platformspecific.j2se.data.image.ImageMLData;

public class ImageFileMLData extends ImageMLData {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3891385696901562160L;
	private final File file;

	public ImageFileMLData(Image theImage, File file) {
		super(theImage);
		this.file = file;
	}

	File getFile() {
		return file;
	}

}
