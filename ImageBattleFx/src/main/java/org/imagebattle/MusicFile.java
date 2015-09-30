package org.imagebattle;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.farng.mp3.MP3File;
import org.farng.mp3.TagException;
import org.farng.mp3.id3.ID3v1;

/**
 * Simplifies reading tags like band, album or title. Currently only works for
 * MP3 but maybe support for OGG will be added later.
 * 
 * @author KoaGex
 *
 */
public class MusicFile extends MP3File {
    private static Logger log = LogManager.getLogger();

    /**
     * Constructor replacement to avoid handling the exceptions.
     */
    static MusicFile create(File file) {
	try {
	    return new MusicFile(file);
	} catch (IOException | TagException e) {
	    throw new RuntimeException(e);
	}
    }

    /**
     * {@link #create(File)} should be used from the outside.
     */
    private MusicFile(File file) throws IOException, TagException {
	super(file);
    }

    String getAlbum() {
	if (hasID3v1Tag()) {
	    ID3v1 id3v1Tag = getID3v1Tag();
	    log.debug("album {}   albumTitle {}", id3v1Tag.getAlbum(), id3v1Tag.getAlbumTitle());
	    return id3v1Tag.getAlbum();
	}
	if (hasID3v2Tag()) {
	    return getID3v2Tag().getAlbumTitle();
	}
	return "";
    }

    String getTitle() {
	if (hasID3v1Tag()) {
	    ID3v1 id3v1Tag = getID3v1Tag();
	    log.debug("title {}   songTitle {}", id3v1Tag.getTitle(), id3v1Tag.getSongTitle());
	    return id3v1Tag.getTitle();
	}
	if (hasID3v2Tag()) {
	    return getID3v2Tag().getSongTitle();
	}
	return "";
    }

    String getArtist() {
	if (hasID3v1Tag()) {
	    ID3v1 id3v1Tag = getID3v1Tag();
	    log.debug("artist {0}   leadArtist {1}", id3v1Tag.getArtist(), id3v1Tag.getLeadArtist());
	    return id3v1Tag.getArtist();
	}
	if (hasID3v2Tag()) {
	    return getID3v2Tag().getLeadArtist();
	}
	return "";
    }
}
