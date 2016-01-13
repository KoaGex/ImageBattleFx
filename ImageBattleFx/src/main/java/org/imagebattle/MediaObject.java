package org.imagebattle;

/**
 * @author Besitzer
 *
 */
public class MediaObject {

  private final int id;
  private final String hash;
  private final MediaType mediaType;

  /**
   * @param id
   * @param hash
   * @param mediaType
   */
  public MediaObject(int id, String hash, MediaType mediaType) {
    this.id = id;
    this.hash = hash;
    this.mediaType = mediaType;
  }

  public int id() {
    return id;
  }

  public String hash() {
    return hash;
  }

  public MediaType mediaType() {
    return mediaType;
  }

}