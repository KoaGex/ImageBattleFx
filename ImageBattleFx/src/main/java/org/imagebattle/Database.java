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
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.util.Pair;
import javax.sql.DataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A database to store everything the media battle application wants to save.
 * 
 * @author KoaGex
 *
 */
final class Database {
  private static final Logger LOG = LogManager.getLogger();

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
  Database(final DataSource dataSource) {
    this.dataSource = dataSource;

    final Collection<String> tables = tables();

    final BiConsumer<String, Runnable> createIfMissing = (tableName, createMethod) -> {
      if (!tables.contains(tableName)) {
        createMethod.run();
      }
    };

    createIfMissing.accept(MEDIA_OBJECTS, this::createMediaObjectsTable);
    createIfMissing.accept(FILES, this::createFilesTable);
    createIfMissing.accept(IGNORED, this::createIgnoredTable);
    createIfMissing.accept(EDGES, this::createEdgesTable);
    createIfMissing.accept("folders", this::createFoldersTable);

  }

  protected void addEdge(final File winner, final File loser) {
    final int winnerId = mediaId(winner);
    final int loserId = mediaId(loser);

    if (winnerId == loserId) {
      throw new IllegalArgumentException(
          "These files have the same content. An edge between them is forbidden: " + winner + " ,  "
              + loser);
    }

    final String insert = "insert into " + EDGES + " values (" + winnerId + "," + loserId + ")";
    executeSql(insert);
  }

  protected void removeFromEdges(final File file) {
    final int id = mediaId(file);

    final String delete = "delete from " + EDGES + " where winner =  " + id + " or loser = " + id;
    executeSql(delete);
  }

  TransitiveDiGraph queryEdges() {
    LOG.debug("start");

    final String query = "select f_win.absolute_path, f_los.absolute_path " + //
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

    final RowMapper<Pair<String, String>> rowMapper = resultSet -> new Pair<String, String>(
        resultSet.getString(1), resultSet.getString(2));
    final List<Pair<String, String>> filesPaths = query(query, rowMapper);
    LOG.debug(filesPaths.size());

    final TransitiveDiGraph result = new TransitiveDiGraph();
    result.addNormalEdges(filesPaths);
    LOG.debug("finished building graph");

    return result;
  }

  TransitiveDiGraph queryEdges(//
      final File chosenDirectory, //
      final Predicate<? super File> matchesFileRegex, //
      final Boolean recursive //
  ) {
    LOG.debug("start");

    /*
     * Regex can not be used in sqlite by default:
     * http://stackoverflow.com/questions/5071601/how-do-i-use-regex-in-a-sqlite-query#8338515
     */

    final String query = "select f_win.absolute_path, f_los.absolute_path " + //
        " from " + EDGES + " e " + //
        " join  " + MEDIA_OBJECTS + " m_win" + //
        " on m_win.id = e.winner " + //
        " join " + FILES + " f_win " + //
        " on f_win.media_object = m_win.id " + //
        " join  " + MEDIA_OBJECTS + " m_los" + //
        " on m_los.id = e.loser " + //
        " join " + FILES + " f_los " + //
        " on f_los.media_object = m_los.id " + //
        " where f_win.absolute_path like '" + chosenDirectory.getAbsolutePath() + "%'"//
    ;

    final RowMapper<Pair<String, String>> rowMapper = resultSet -> new Pair<String, String>(
        resultSet.getString(1), //
        resultSet.getString(2)//
    );
    final List<Pair<String, String>> filesPaths = query(query, rowMapper);
    LOG.debug("query finished");

    final Predicate<File> containedRecursively = file -> {
      return file.getAbsolutePath().startsWith(chosenDirectory.getAbsolutePath());
    };
    final Predicate<File> containedDirectly = file -> {
      final List<File> directorFiles = Arrays.asList(chosenDirectory.listFiles());
      return directorFiles.contains(file);
    };
    final Predicate<File> matchesChosenDirectory = recursive ? containedRecursively
        : containedDirectly;

    final Predicate<File> acceptFile = matchesChosenDirectory.and(matchesFileRegex)
        .and(File::exists);

    final List<Pair<String, String>> matchingPairs = filesPaths.stream()//
        .filter(pair -> {
          final File winner = new File(pair.getKey());
          final File loser = new File(pair.getValue());
          return acceptFile.test(winner) && acceptFile.test(loser);
        })//
        .collect(Collectors.toList());

    LOG.info("finished matching pairs. count: {}", matchingPairs.size());

    final Set<File> duplicateFiles = Stream
        .concat(matchingPairs.stream().map(pair -> pair.getKey()),

            // TODO why are we calculating file hashes of image files when audio battle was started?
            matchingPairs.stream().map(pair -> pair.getKey()))//
        .distinct()//
        .map(File::new)//
        .filter(File::isFile)//
        .collect(Collectors.groupingBy(file -> new FileContentHash(file).hash()))//
        .entrySet()//
        .stream()//
        .map(Entry::getValue)//
        .flatMap(list -> list.stream().skip(1))//
        .collect(Collectors.toSet());

    LOG.info("duplicate files {}", duplicateFiles);

    final TransitiveDiGraph result = new TransitiveDiGraph();
    for (Pair<String, String> pair : matchingPairs) {
      final String winnerString = pair.getKey();
      final String loserString = pair.getValue();
      final File winner = new File(winnerString);
      final File loser = new File(loserString);
      if (!duplicateFiles.contains(winner) && !duplicateFiles.contains(loser)) {
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

    final int id = mediaId(file);

    final String insert = "insert into " + IGNORED + " values (" + id + ")";
    executeSql(insert);
  }

  private int mediaId(File file) {
    final Optional<Integer> lookupIdByFile = lookupFile(file);

    int id;
    if (lookupIdByFile.isPresent()) {
      id = lookupIdByFile.get();
    } else {

      final String hash = new FileContentHash(file).hash();
      final Optional<Integer> lookupMediaItemId = lookupMediaItemId(hash);

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
    final int id = mediaId(file);
    final String delete = "delete from " + IGNORED + " where  media_object = " + id;
    executeSql(delete);
  }

  /**
   * @return Set containing all existing files that are ignored.
   */
  Set<File> queryIgnored() {
    LOG.debug("start");

    final String query = "select files.absolute_path from " + FILES + " join  " + MEDIA_OBJECTS
        + " on " + MEDIA_OBJECTS + ".id =" + FILES + ".media_object join " + IGNORED + " on "
        + IGNORED + ".media_object = " + MEDIA_OBJECTS + ".id";

    final List<String> filesPaths = query(query, resultSet -> resultSet.getString(1));
    LOG.debug(filesPaths.size());

    return filesPaths.stream()//
        .map(File::new)//
        .filter(File::exists)//
        .collect(Collectors.groupingBy(file -> new FileContentHash(file).hash()))//
        .entrySet()//
        .stream()//
        .map(Entry::getValue)//
        .map(list -> list.get(0))//
        .collect(Collectors.toSet());
  }

  Set<File> queryIgnored(File chosenDirectory, MediaType mediaType, Boolean recursive) {
    final String query = "select files.absolute_path from " + FILES + //
        " join  " + MEDIA_OBJECTS + //
        " on " + MEDIA_OBJECTS + ".id =" + FILES + ".media_object " + //
        " join " + IGNORED + //
        " on " + IGNORED + ".media_object = " + MEDIA_OBJECTS + ".id" + //
        " where " + MEDIA_OBJECTS + ".media_type = '" + mediaType.name() + "'";

    final RowMapper<String> rowMapper = resultSet -> resultSet.getString(1);

    // block copied from CentralStorage
    final Predicate<File> containedRecursively = file -> {
      return file.getAbsolutePath().startsWith(chosenDirectory.getAbsolutePath());
    };
    final Predicate<File> containedDirectly = file -> {
      final List<File> directorFiles = Arrays.asList(chosenDirectory.listFiles());
      return directorFiles.contains(file);
    };
    final Predicate<File> matchesChosenDirectory = recursive ? containedRecursively
        : containedDirectly;

    final List<String> filesPaths = query(query, rowMapper);
    return filesPaths.stream()//
        .map(File::new)//
        .filter(File::exists)//
        .filter(matchesChosenDirectory)//
        .collect(Collectors.toSet());

  }

  /**
   * @return Names of all tables in the sqlite database.
   */
  protected Collection<String> tables() {
    final String queryAllTableNames = "SELECT name FROM sqlite_master WHERE type='table'";
    return query(queryAllTableNames, resultSet -> resultSet.getString(1));
  }

  /**
   * This table stores the hashes of files. The table storing the edges will use the integer ids of
   * this table.
   * 
   */
  private void createMediaObjectsTable() {
    final String createTable = " create table " + MEDIA_OBJECTS + "("
        + "id INTEGER PRIMARY KEY, hash TEXT, media_type TEXT) ";
    executeSql(createTable);
  }

  /**
   * Depends on {@link #createMediaObjectsTable(Connection)}.
   * 
   */
  private void createFilesTable() {
    final String createTable = " create table " + FILES + "(" + //
        " media_object INTEGER NON NULL," + //
        " absolute_path TEXT," + //
        " FOREIGN KEY(media_object) REFERENCES " + MEDIA_OBJECTS + "(id)  ) ";
    executeSql(createTable);
  }

  private void createIgnoredTable() {
    final String createTable = " create table " + IGNORED + "(" + //
        " media_object INTEGER NON NULL," + //
        " FOREIGN KEY(media_object) REFERENCES " + MEDIA_OBJECTS + "(id)  ) ";
    executeSql(createTable);
  }

  private void createEdgesTable() {
    final String createTable = " create table " + EDGES + "(" + //
        " winner INTEGER NON NULL," + //
        " loser  INTEGER NON NULL," + //
        " FOREIGN KEY(winner) REFERENCES " + MEDIA_OBJECTS + "(id) ,  " + //
        " FOREIGN KEY(loser)  REFERENCES " + MEDIA_OBJECTS + "(id)  ) ";
    executeSql(createTable);
  }

  private void createFoldersTable() {
    final String createTable = //
        " CREATE TABLE folders ( " + //
            " name TEXT NOT NULL, " + //
            " media_type TEXT NOT NULL, " + //
            " directory TEXT NOT NULL, " + //
            " recursive TEXT NOT NULL " + //
            " ) "//
    ;
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
  void addMediaObject(final String hash, final MediaType mediaType) {
    final String insert = " insert into " + MEDIA_OBJECTS + "(hash,media_type) values ('" + hash
        + "','" + mediaType.name() + "')";
    executeSql(insert);
  }

  /**
   * @param mediaObjectId
   * @param file
   */
  void addFile(final int mediaObjectId, final File file) {

    // TODO preparedStatement? test performance
    final String insert = "insert into " + FILES + " (media_object, absolute_path) values ("
        + mediaObjectId + ",'" + file.getAbsolutePath().replace("'", "''") + "')";

    executeSql(insert);
  }

  /**
   * @param file
   * @return
   */
  protected Optional<Integer> lookupFile(final File file) {

    final String query = "select media_object from " + FILES + " where absolute_path = '"
        + file.getAbsolutePath().replace("'", "''") + "'";

    LOG.trace(query);
    final List<Integer> files = query(query, rs -> rs.getInt(1));
    return files.stream()//
        .findAny();

  }

  Optional<Integer> lookupMediaItemId(String hash) {
    final String query = "select id from " + MEDIA_OBJECTS + " where hash = '" + hash + "'";
    final List<Integer> ids = query(query, resultSet -> resultSet.getInt(1));
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
   * @param files
   *          for each determine hash and create it in the files and media_object table if not
   *          already present.
   */
  void registerFiles(final Collection<File> files) {
    for (final File file : files) {
      mediaId(file);
    }
  }

  List<ImageBattleFolder> queryFolders(CentralStorage centralStorage) {
    String query = "select * from folders";

    RowMapper<ImageBattleFolder> mediaObjectMapper = resultSet -> {
      String name = resultSet.getString("name");
      String mediaTypeString = resultSet.getString("media_type");
      MediaType mediaType = MediaType.valueOf(mediaTypeString);
      String directory = resultSet.getString("directory");
      File chosenDirectory = new File(directory);
      boolean recursive = Boolean.parseBoolean(resultSet.getString("recursive"));
      return new ImageBattleFolder(centralStorage, chosenDirectory, mediaType, recursive, name);
    };

    return query(query, mediaObjectMapper);
  }

  void addFolder(ImageBattleFolder folder) {

    final String insert = "insert into folders values ("//
        + "'" + folder.getName() + "'," //
        + "'" + folder.getMediaType().name() + "'," //
        + "'" + folder.getDirectory().getAbsolutePath() + "'," //
        + "'" + folder.isRecursive() + "')";
    LOG.debug(insert);
    executeSql(insert);
  }

  /**
   * @param connection
   *          Use {@link #getSqliteConnection(File)}.
   * @param sql
   *          Any sql statement you want to be executed and don't expect an result from.
   */
  private void executeSql(final String sql) {
    try (Connection connection = dataSource.getConnection()) {
      final Statement statement = connection.createStatement();
      statement.execute(sql);
      statement.close();
    } catch (SQLException e) {
      throw new IllegalStateException("sql execute error", e);
    }
  }

  private <T> List<T> query(String query, RowMapper<T> rowMapper) {
    final List<T> result = new LinkedList<>();
    try (Connection connection = dataSource.getConnection()) {
      try (Statement statement = connection.createStatement()) {
        try (ResultSet resultSet = statement.executeQuery(query)) {
          while (resultSet.next()) {
            result.add(rowMapper.map(resultSet));
          }
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("sql query error", e);
    }
    return result;
  }

  private MediaType detectMediaType(final File file) {
    // TODO im not 100% happy with this. Create a class MediaFile that detects type while creating?
    return Arrays.stream(MediaType.values()).filter(type -> type.matches(file))//
        .findAny()//
        .orElseThrow(() -> new IllegalStateException("File type of " + file.getAbsolutePath()
            + " could not be detected. It should probably not be in the media battle."));
  }

}
