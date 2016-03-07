package org.imagebattle;

import java.io.File;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * @author Besitzer
 *
 */
public enum MediaType {
  IMAGE(".*\\.(BMP|GIF|JPEG|JPG|PNG)"), //
  MUSIC(".*\\.(MP3|OGG)");

  Predicate<File> predicate;

  private MediaType(String regex) {
    predicate = createFileRegexChecker(regex);
  }

  public boolean matches(File file) {
    return predicate.test(file);
  }

  private static Predicate<File> createFileRegexChecker(String regex) {
    Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    return file -> {
      return pattern.matcher(file.getName()).matches();
    };
  }

}