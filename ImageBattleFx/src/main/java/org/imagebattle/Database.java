package org.imagebattle;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;

import javax.sql.DataSource;

/**
 * A database to store everything the media battle application wants to save.
 * 
 * @author KoaGex
 *
 */
public class Database {

  private static final String FILES = "files";
  private static final String MEDIA_OBJECTS = "media_objects";
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

    Collection<String> tables = tables();

    BiConsumer<String, Runnable> createIfMissing = (tableName, createMethod) -> {
      if (!tables.contains(tableName)) {
        createMethod.run();
      }
    };

    createIfMissing.accept(MEDIA_OBJECTS, this::createMediaObjectsTable);
    createIfMissing.accept(FILES, this::createFilesTable);

  }

  public Collection<String> tables() {
    String queryAllTableNames = "SELECT name FROM sqlite_master WHERE type='table'";
    return query(queryAllTableNames, resultSet -> resultSet.getString(1));
  }

  /**
   * This table stores the hashes of files. The table storing the edges will use the integer ids of
   * this table.
   * 
   */
  private void createMediaObjectsTable() {
    String createTable = " create table " + MEDIA_OBJECTS + "("
        + "id INTEGER PRIMARY KEY, hash TEXT, media_type TEXT) ";
    executeSql(createTable);
  }

  /**
   * Depends on {@link #createMediaObjectsTable(Connection)}.
   * 
   */
  private void createFilesTable() {
    String createTable = " create table " + FILES + "(" + //
        " media_object INTEGER NON NULL," + //
        " absolute_path TEXT," + //
        " FOREIGN KEY(media_object) REFERENCES " + MEDIA_OBJECTS + "(id)  ) ";
    executeSql(createTable);
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
    String insert = " insert into " + MEDIA_OBJECTS + "(hash,media_type) values ('" + hash + "','"
        + mediaType.name() + "')";
    executeSql(insert);
  }

  /**
   * @param mediaObjectId
   * @param file
   */
  public void addFile(int mediaObjectId, File file) {

    // TODO preparedStatement? test performance
    String insert = "insert into " + FILES + " (media_object, path) values (" + mediaObjectId + ",'"
        + file.getAbsolutePath() + "')";

    executeSql(insert);
  }

  public void lookupMediaItemId(String hash) {

  }

  /**
   * @return Zero or more {@link MediaObject} that match the given criteria.
   */
  public Collection<MediaObject> queryMediaObjects() {
    String query = "select * from " + MEDIA_OBJECTS;

    RowMapper<MediaObject> mediaObjectMapper = resultSet -> {
      int id = resultSet.getInt("id");
      String hash = resultSet.getString("hash");
      String mediaTypeString = resultSet.getString("media_type");
      MediaType mediaType = MediaType.valueOf(mediaTypeString);
      MediaObject mediaObject = new MediaObject(id, hash, mediaType);
      return mediaObject;
    };

    List<MediaObject> result = query(query, mediaObjectMapper);

    return result;
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

  private <T> List<T> query(String query, RowMapper<T> rowMapper) {
    List<T> result;
    try {
      Connection connection = dataSource.getConnection();
      Statement statement = connection.createStatement();
      ResultSet resultSet = statement.executeQuery(query);

      result = new LinkedList<>();
      while (resultSet.next()) {
        result.add(rowMapper.map(resultSet));
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    return result;
  }

}
