package org.imagebattle;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javafx.util.Pair;
import javax.sql.DataSource;

/**
 * A database to store everything the media battle application wants to save.
 * 
 * @author KoaGex
 *
 */
class Database {

  private static final String IGNORED = "ignored";
  private static final String FILES = "files";
  private static final String MEDIA_OBJECTS = "media_objects";
  private static final String EDGES = "edges";
  private final DataSource dataSource;

  /**
   * Constructor.
   * 
   * @param dataSource
   *          This looks like any {@link DataSource} can be used but currently it assumes a
   *          {@link SqliteDatabase}. Should the parameter type be changed to that class or keep
   *          using the interface?
   */
  Database(DataSource dataSource) {
    this.dataSource = dataSource;

    Collection<String> tables = tables();

    BiConsumer<String, Runnable> createIfMissing = (tableName, createMethod) -> {
      if (!tables.contains(tableName)) {
        createMethod.run();
      }
    };

    createIfMissing.accept(MEDIA_OBJECTS, this::createMediaObjectsTable);
    createIfMissing.accept(FILES, this::createFilesTable);
    createIfMissing.accept(IGNORED, this::createIgnoredTable);
    createIfMissing.accept(EDGES, this::createEdgesTable);

  }

  void addEdge(File winner, File loser) {
    int winnerId = mediaId(winner);
    int loserId = mediaId(loser);

    String insert = "insert into " + EDGES + " values (" + winnerId + "," + loserId + ")";
    executeSql(insert);
  }

  TransitiveDiGraph queryEdges() {

    String query = "select f_win.absolute_path, f_los.absolute_path " + //
        " from " + EDGES + " e " + //
        " join  " + MEDIA_OBJECTS + " m_win" + //
        " on m_win.id = e.winner " + //
        " join " + FILES + " f_win " + //
        " on f_win.media_object = m_win.id " + //
        " join  " + MEDIA_OBJECTS + " m_los" + //
        " on m_los.id = e.loser " + //
        " join " + FILES + " f_los " + //
        " on f_los.media_object = m_los.id " //
        ;

    RowMapper<Pair<String, String>> rowMapper = resultSet -> new Pair(resultSet.getString(1),
        resultSet.getString(2));
    List<Pair<String, String>> filesPaths = query(query, rowMapper);
    System.err.println(filesPaths.size());
    System.err.println(filesPaths.get(0));

    // return filesPaths.stream()//
    // .map(File::new)//
    // .filter(File::exists)//
    // .collect(Collectors.toSet());

    TransitiveDiGraph result = new TransitiveDiGraph();
    for (Pair<String, String> pair : filesPaths) {
      File winner = new File(pair.getKey());
      File loser = new File(pair.getValue());
      result.addVertex(winner);
      result.addVertex(loser);
      result.addEdge(winner, loser);
    }

    return result;
  }

  TransitiveDiGraph queryEdges(File chosenDirectory, Predicate<? super File> matchesFileRegex,
      Boolean recursive) {

    String query = "select f_win.absolute_path, f_los.absolute_path " + //
        " from " + EDGES + " e " + //
        " join  " + MEDIA_OBJECTS + " m_win" + //
        " on m_win.id = e.winner " + //
        " join " + FILES + " f_win " + //
        " on f_win.media_object = m_win.id " + //
        " join  " + MEDIA_OBJECTS + " m_los" + //
        " on m_los.id = e.loser " + //
        " join " + FILES + " f_los " + //
        " on f_los.media_object = m_los.id " //
        ;

    RowMapper<Pair<String, String>> rowMapper = resultSet -> new Pair<String, String>(
        resultSet.getString(1), //
        resultSet.getString(2)//
    );
    List<Pair<String, String>> filesPaths = query(query, rowMapper);
    System.err.println(filesPaths.size());
    System.err.println(filesPaths.get(0));

    Predicate<File> containedRecursively = file -> {
      return file.getAbsolutePath().startsWith(chosenDirectory.getAbsolutePath());
    };
    Predicate<File> containedDirectly = file -> {
      List<File> directorFiles = Arrays.asList(chosenDirectory.listFiles());
      return directorFiles.contains(file);
    };
    Predicate<File> matchesChosenDirectory = recursive ? containedRecursively : containedDirectly;

    Predicate<File> acceptFile = matchesChosenDirectory.and(matchesFileRegex);

    TransitiveDiGraph result = new TransitiveDiGraph();
    for (Pair<String, String> pair : filesPaths) {
      File winner = new File(pair.getKey());
      File loser = new File(pair.getValue());
      if (acceptFile.test(winner) && acceptFile.test(loser)) {
        result.addVertex(winner);
        result.addVertex(loser);
        result.addEdge(winner, loser);
      }
    }

    return result;
  }

  /**
   * @param mediaObject
   */
  void addToIgnore(File file) {

    int id = mediaId(file);

    String insert = "insert into " + IGNORED + " values (" + id + ")";
    executeSql(insert);
  }

  private int mediaId(File file) {
    Optional<Integer> lookupIdByFile = lookupFile(file);

    int id;
    if (lookupIdByFile.isPresent()) {
      id = lookupIdByFile.get();
    } else {

      String hash = new FileContentHash(file).hash();
      Optional<Integer> lookupMediaItemId = lookupMediaItemId(hash);

      if (lookupMediaItemId.isPresent()) {
        id = lookupMediaItemId.get();
      } else {
        addMediaObject(hash, detectMediaType(file));
        id = lookupMediaItemId(hash).orElseThrow(RuntimeException::new);
      }

      addFile(id, file);
    }
    return id;
  }

  /**
   * @param file
   */
  void removeFromIgnore(File file) {
    int id = mediaId(file);
    String delete = "delete from " + IGNORED + " where  media_object = " + id;
    executeSql(delete);
  }

  /**
   * @return Set containing all existing files that are ignored.
   */
  Set<File> queryIgnored() {

    String query = "select files.absolute_path from " + FILES + " join  " + MEDIA_OBJECTS + " on "
        + MEDIA_OBJECTS + ".id =" + FILES + ".media_object join " + IGNORED + " on " + IGNORED
        + ".media_object = " + MEDIA_OBJECTS + ".id";

    RowMapper<String> rowMapper = resultSet -> resultSet.getString(1);
    List<String> filesPaths = query(query, rowMapper);
    System.err.println(filesPaths.size());

    return filesPaths.stream()//
        .map(File::new)//
        .filter(File::exists)//
        .collect(Collectors.toSet());
  }

  Set<File> queryIgnored(File chosenDirectory, MediaType mediaType, Boolean recursive) {
    String query = "select files.absolute_path from " + FILES + //
        " join  " + MEDIA_OBJECTS + //
        " on " + MEDIA_OBJECTS + ".id =" + FILES + ".media_object " + //
        " join " + IGNORED + //
        " on " + IGNORED + ".media_object = " + MEDIA_OBJECTS + ".id" + //
        " where " + MEDIA_OBJECTS + ".media_type = '" + mediaType.name() + "'";

    RowMapper<String> rowMapper = resultSet -> resultSet.getString(1);
    List<String> filesPaths = query(query, rowMapper);

    // block copied from CentralStorage
    Predicate<File> containedRecursively = file -> {
      return file.getAbsolutePath().startsWith(chosenDirectory.getAbsolutePath());
    };
    Predicate<File> containedDirectly = file -> {
      List<File> directorFiles = Arrays.asList(chosenDirectory.listFiles());
      return directorFiles.contains(file);
    };
    Predicate<File> matchesChosenDirectory = recursive ? containedRecursively : containedDirectly;

    return filesPaths.stream()//
        .map(File::new)//
        .filter(File::exists)//
        .filter(matchesChosenDirectory)//
        .collect(Collectors.toSet());

  }

  /**
   * @return Names of all tables in the sqlite database.
   */
  Collection<String> tables() {
    String queryAllTableNames = "SELECT name FROM sqlite_master WHERE type='table'";
    RowMapper<String> rowMapper = resultSet -> resultSet.getString(1);
    return query(queryAllTableNames, rowMapper);
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

  // TODO first aim to replace the ignore list, that is easier than the whole graph
  private void createIgnoredTable() {
    String createTable = " create table " + IGNORED + "(" + //
        " media_object INTEGER NON NULL," + //
        " FOREIGN KEY(media_object) REFERENCES " + MEDIA_OBJECTS + "(id)  ) ";
    executeSql(createTable);
  }

  private void createEdgesTable() {
    String createTable = " create table " + EDGES + "(" + //
        " winner INTEGER NON NULL," + //
        " loser  INTEGER NON NULL," + //
        " FOREIGN KEY(winner) REFERENCES " + MEDIA_OBJECTS + "(id) ,  " + //
        " FOREIGN KEY(loser)  REFERENCES " + MEDIA_OBJECTS + "(id)  ) ";
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
  void addMediaObject(String hash, MediaType mediaType) {
    String insert = " insert into " + MEDIA_OBJECTS + "(hash,media_type) values ('" + hash + "','"
        + mediaType.name() + "')";
    executeSql(insert);
  }

  /**
   * @param mediaObjectId
   * @param file
   */
  void addFile(int mediaObjectId, File file) {

    // TODO preparedStatement? test performance
    String insert = "insert into " + FILES + " (media_object, absolute_path) values ("
        + mediaObjectId + ",'" + file.getAbsolutePath() + "')";

    executeSql(insert);
  }

  /**
   * @param file
   * @return
   */
  Optional<Integer> lookupFile(File file) {

    String query = "select media_object from " + FILES + " where absolute_path = '"
        + file.getAbsolutePath() + "'";

    List<Integer> files = query(query, rs -> rs.getInt(1));
    return files.stream()//
        .findAny();

  }

  Optional<Integer> lookupMediaItemId(String hash) {
    String query = "select id from " + MEDIA_OBJECTS + " where hash = '" + hash + "'";
    List<Integer> ids = query(query, rs -> rs.getInt(1));
    return ids.stream().findAny();
  }

  /**
   * @return Zero or more {@link MediaObject} that match the given criteria.
   */
  Collection<MediaObject> queryMediaObjects() {
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
    try (Connection connection = dataSource.getConnection()) {
      Statement statement = connection.createStatement();
      statement.execute(sql);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private <T> List<T> query(String query, RowMapper<T> rowMapper) {
    List<T> result;
    try (Connection connection = dataSource.getConnection()) {

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

  private MediaType detectMediaType(File file) {
    // TODO im not 100% happy with this. Create a class MediaFile that detects type while creating?
    return Arrays.stream(MediaType.values()).filter(type -> type.matches(file))//
        .findAny()//
        .orElseThrow(() -> new IllegalStateException("File type of " + file.getAbsolutePath()
            + " could not be detected. It should probably not be in the media battle."));
  }

}
