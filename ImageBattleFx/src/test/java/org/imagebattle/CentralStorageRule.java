package org.imagebattle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.rules.ExternalResource;

public class CentralStorageRule extends ExternalResource {

  static final String IGNORE_FILE_TEST = "ignoreFile_test.csv";
  static final String GRAPH_FILE_TEST = "graphFile_test.csv";
  static final String SQLITE_FILE_TEST = "sqliteFile_test.csv";
  private Path ignorePath = Paths.get(System.getProperty("user.home"), IGNORE_FILE_TEST);
  private Path graphPath = Paths.get(System.getProperty("user.home"), GRAPH_FILE_TEST);
  private Path sqlitePath = Paths.get(System.getProperty("user.home"), SQLITE_FILE_TEST);
  private CentralStorage centralStorage;

  @Override
  protected void before() throws Throwable {
    centralStorage = new CentralStorage(GRAPH_FILE_TEST, IGNORE_FILE_TEST, SQLITE_FILE_TEST);
  }

  @Override
  protected void after() {
    try {
      Files.deleteIfExists(graphPath);
      Files.deleteIfExists(ignorePath);
      Files.deleteIfExists(sqlitePath);
    } catch (IOException e) {
      throw new UncheckedIOException("careful: some files were maybe not deleted", e);
    }
  }

  public CentralStorage centralStorage() {
    return centralStorage;
  }
}
