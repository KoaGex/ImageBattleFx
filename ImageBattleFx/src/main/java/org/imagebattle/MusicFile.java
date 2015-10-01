package org.imagebattle;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

import org.farng.mp3.MP3File;
import org.farng.mp3.TagException;
import org.farng.mp3.id3.AbstractID3v2;
import org.farng.mp3.id3.ID3v1;

/**
 * Simplifies reading tags like band, album or title. Currently only works for
 * MP3 but maybe support for OGG will be added later.
 * 
 * @author KoaGex
 *
 */
public class MusicFile extends MP3File {
    private static final String UNKNOWN = "";

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
	return getTagValue(ID3v1::getAlbum, AbstractID3v2::getAlbumTitle);
    }

    String getTitle() {
	return getTagValue(ID3v1::getTitle, AbstractID3v2::getSongTitle);
    }

    String getArtist() {
	return getTagValue(ID3v1::getArtist, AbstractID3v2::getLeadArtist);
    }

    private String getTagValue(Function<ID3v1, String> v1, Function<AbstractID3v2, String> v2) {
	if (hasID3v1Tag()) {
	    ID3v1 id3v1Tag = getID3v1Tag();
	    return v1.apply(id3v1Tag);
	}
	if (hasID3v2Tag()) {
	    AbstractID3v2 id3v2Tag = getID3v2Tag();
	    return v2.apply(id3v2Tag);
	}
	return UNKNOWN;
    }
}
