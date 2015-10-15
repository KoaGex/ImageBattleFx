package org.imagebattle;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

class DirectoryChooserFile extends File {

    /**
     * 
     */
    private static final long serialVersionUID = -6307423313397766348L;

    final List<File> images = new LinkedList<>();

    private int recursiveImageCount = 0;

    DirectoryChooserFile(String pathname, Predicate<File> fileRegex) {
	super(pathname);
	File[] listFiles = listFiles();
	if (listFiles != null) {
	    for (File file : listFiles) {
		if (fileRegex.test(file)) {
		    images.add(file);
		}
	    }
	}

    }

    @Override
    public String toString() {
	return getName() + " (" + images.size() + "/" + recursiveImageCount + ")";
    }

    void incrementRecursiveImageCount() {
	recursiveImageCount++;
    }

}
