package org.imagebattle;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.farng.mp3.MP3File;
import org.farng.mp3.TagException;
import org.farng.mp3.id3.AbstractID3v2;
import org.farng.mp3.id3.ID3v1;
import org.tritonus.share.sampled.file.TAudioFileFormat;

/**
 * Simplifies reading tags like band, album or title. Currently only works for MP3 but maybe support
 * for OGG will be added later.
 * 
 * @author KoaGex
 *
 */
final class MusicFile extends MP3File {
  private static final Logger LOG = LogManager.getLogger();
  private static final String UNKNOWN = "";

  /**
   * Constructor replacement to avoid handling the exceptions.
   */
  protected static MusicFile create(File file) {

    // Avoid org.farng.mp3.TagException: Unable to create FilenameTag
    if (file.getName().contains("(") && !file.getName().contains(")")) {
      return new MusicFile();
    }

    try {
      return new MusicFile(file);
    } catch (IOException | TagException e) {
      if (LOG.isWarnEnabled()) {
        LOG.warn("file:" + file.getAbsolutePath(), e);
      }
      return new MusicFile();
    }
  }

  /**
   * {@link #create(File)} should be used from the outside.
   */
  private MusicFile(File file) throws IOException, TagException {
    super(file);
  }

  private MusicFile() {
    super();
  }

  String getAlbum() {
    Function<ID3v1, String> v1Album = ID3v1::getAlbum;
    Function<AbstractID3v2, String> v2Album = AbstractID3v2::getAlbumTitle;
    return getTagValue(v1Album, v2Album);
  }

  String getTitle() {
    Function<ID3v1, String> v1Title = ID3v1::getTitle;
    Function<AbstractID3v2, String> v2Title = AbstractID3v2::getSongTitle;
    return getTagValue(v1Title, v2Title);
  }

  String getArtist() {
    Function<ID3v1, String> v1Artist = ID3v1::getArtist;
    Function<AbstractID3v2, String> v2Artist = AbstractID3v2::getLeadArtist;
    return getTagValue(v1Artist, v2Artist);
  }

  private String getTagValue(Function<ID3v1, String> v1, Function<AbstractID3v2, String> v2) {
    String result = UNKNOWN;
    if (hasID3v1Tag()) {
      ID3v1 id3v1Tag = getID3v1Tag();
      result = v1.apply(id3v1Tag);
    } else if (hasID3v2Tag()) {
      AbstractID3v2 id3v2Tag = getID3v2Tag();
      result = v2.apply(id3v2Tag);
    }
    return result;
  }

  String getLength() {
    File mp3file = getMp3file();
    AudioFileFormat fileFormat;
    try {
      fileFormat = AudioSystem.getAudioFileFormat(mp3file);
      if (fileFormat instanceof TAudioFileFormat) {
        Map<?, ?> properties = ((TAudioFileFormat) fileFormat).properties();
        String key = "duration";
        Long microseconds = (Long) properties.get(key);
        int mili = (int) (microseconds / 1000);
        int sec = (mili / 1000) % 60;
        int min = (mili / 1000) / 60;
        LOG.trace("time = {} : {}", min, sec);
        return min + ":" + sec;
      } else {
        throw new UnsupportedAudioFileException();
      }
    } catch (IOException | NullPointerException e) {
      LOG.catching(e);
    } catch (UnsupportedAudioFileException e) {
      LOG.trace("javax.sound.sampled.UnsupportedAudioFileException on file : {}",
          mp3file.getAbsoluteFile());
    }

    return "";

  }
}
