package org.imagebattle;

import java.io.File;

import org.sqlite.JDBC;
import org.sqlite.SQLiteDataSource;

/**
 * @author Besitzer
 *
 */
public class SqliteDatabase extends SQLiteDataSource {

  /**
   * @param databaseFile
   */
  public SqliteDatabase(File databaseFile) {
    super();
    if (databaseFile.isDirectory()) {
      throw new IllegalArgumentException("databaseFile must not be a directory file");
    }
    this.setUrl(JDBC.PREFIX + databaseFile.getAbsolutePath());
  }

}
