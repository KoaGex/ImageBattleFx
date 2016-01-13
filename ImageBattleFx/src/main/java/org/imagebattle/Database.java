package org.imagebattle;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedList;

import javax.sql.DataSource;

/**
 * A database to store everything the media battle application wants to save.
 * 
 * @author KoaGex
 *
 */
public class Database {

  private final DataSource dataSource;

  /**
   * Constructor.
   * 
   * @param dataSource
   *          This looks like any {@link DataSource} can be used but currently it assumes a
   *          {@link SqliteDatabase}. Should the parameter type be changed to that class or keep
   *          using the interface?
   */
  public Database(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * This table stores the hashes of files. The table storing the edges will use the integer ids of
   * this table.
   * 
   */
  public void createMediaObjectsTable() {
    String createTable = " create table media_objects("
        + "id INTEGER PRIMARY KEY, hash TEXT, media_type TEXT) ";
    executeSql(createTable);
  }

  /**
   * Depends on {@link #createMediaObjectsTable(Connection)}.
   * 
   */
  public void createFilesTable() {
    String createTable = " create table files(" + //
        " media_object INTEGER NON NULL," + //
        " absolute_path TEXT," + //
        " FOREIGN KEY(media_object) REFERENCES media_objects(id)  ) ";
    executeSql(createTable);
  }

  /**
   * @param connection
   *          Use {@link #getSqliteConnection(File)}.
   * @param sql
   *          Any sql statement that you want to be executed and don't expect an result from.
   */
  private void executeSql(String sql) {
    try {
      Connection connection = dataSource.getConnection();
      Statement statement = connection.createStatement();
      statement.execute(sql);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Add one item to the media_objects table. One mediaObject represents one image, musicTrack or
   * whatever else may be added.
   * 
   * @param hash
   *          Hash should be created by SHA-256 over the whole file content. It should uniquely
   *          identify the mediaObject. This way an image can be recognized after it was moved.
   * @param mediaType
   *          Currently String is allowed. This may later become an enum.
   */
  public void addMediaObject(String hash, MediaType mediaType) {
    String insert = " insert into media_objects(hash,media_type) values ('" + hash + "','"
        + mediaType.name() + "')";
    executeSql(insert);
  }

  /**
   * @return Zero or more {@link MediaObject} that match the given criteria.
   */
  public Collection<MediaObject> queryMediaObjects() {
    String query = "select * from media_objects";
    LinkedList<MediaObject> result;
    try {
      Connection connection = dataSource.getConnection();
      Statement statement = connection.createStatement();
      ResultSet resultSet = statement.executeQuery(query);

      result = new LinkedList<>();
      while (resultSet.next()) {
        int id = resultSet.getInt("id");
        String hash = resultSet.getString("hash");
        String mediaTypeString = resultSet.getString("media_type");
        MediaType mediaType = MediaType.valueOf(mediaTypeString);
        MediaObject mediaObject = new MediaObject(id, hash, mediaType);
        result.add(mediaObject);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

    return result;
  }
}
