package org.imagebattle;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class DirectoryChooserFile extends File {

    /**
     * 
     */
    private static final long serialVersionUID = -6307423313397766348L;

    final List<File> images = new LinkedList<>();

    private int recursiveImageCount = 0;

    boolean hasImageBattleFile;

    public DirectoryChooserFile(String pathname, String regex) {
	super(pathname);
	File[] listFiles = listFiles();
	if (listFiles != null) {
	    for (File file : listFiles) {
		if (file.getName().toUpperCase().matches(regex)) {
		    images.add(file);
		}
	    }
	}

	hasImageBattleFile = new File(pathname + File.separator + ImageBattleFolder.IMAGE_BATTLE_DAT).exists();
    }

    @Override
    public String toString() {
	String hasFileChar = hasImageBattleFile ? "*" : "";
	return getName() + " (" + images.size() + "/" + recursiveImageCount + ")" + hasFileChar;
    }

    void incrementRecursiveImageCount() {
	recursiveImageCount++;
    }

}
