package org.imagebattle;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashSet;

import org.hamcrest.collection.IsCollectionWithSize;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsCollectionContaining;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DatabaseTest {
  @Rule
  public TemporaryFolder tf = new TemporaryFolder();

  private Database database;

  private SqliteDatabase dataSource;

  private Connection connection;

  @Before
  public void setUp() throws IOException, SQLException {
    dataSource = new SqliteDatabase(tf.newFile());
    database = new Database(dataSource);
    connection = dataSource.getConnection();
  }

  @After
  public void tearDown() throws SQLException {
    connection.close();
  }

  // TODO move this test to SqliteDatabaseTest ?
  // @Test
  // public void getSqliteConnection() throws IOException, SQLException {
  // // prepare
  // File file = tf.newFile();
  // file.delete();
  //
  // // act
  // Connection sqliteConnection = CentralStorage.getSqliteConnection(file);
  //
  // // assert
  // assertThat(file.exists(), is(true));
  // assertThat(sqliteConnection.isClosed(), is(false));
  // sqliteConnection.close(); // exception would fail the test
  // }

  @Test
  public void createMediaObjectsTable() throws IOException, SQLException {

    Statement statement = connection.createStatement();
    String queryAllTableNames = "SELECT name FROM sqlite_master WHERE type='table'";
    ResultSet resultSet = statement.executeQuery(queryAllTableNames);
    final boolean anyTableExistedBefore = resultSet.next();

    HashSet<Object> tableNames = new HashSet<>();
    while (resultSet.next()) {
      String tableName = resultSet.getString(1);
      tableNames.add(tableName);
    }

    // act
    database.createMediaObjectsTable();

    // assert
    assertThat(anyTableExistedBefore, Is.is(false));
    ResultSet resultSet2 = statement.executeQuery(queryAllTableNames);
    String tableName = resultSet2.getString(1);
    assertThat(tableName, Is.is("media_objects"));
  }

  @Test
  public void createFilesTable() throws IOException, SQLException {

    // act
    database.createMediaObjectsTable();
    database.createFilesTable();

    // assert
    Statement statement = connection.createStatement();
    String queryAllTableNames = "SELECT name FROM sqlite_master WHERE type='table'";
    ResultSet resultSet = statement.executeQuery(queryAllTableNames);

    HashSet<String> tableNames = new HashSet<>();
    while (resultSet.next()) {
      String tableName = resultSet.getString(1);
      tableNames.add(tableName);
    }

    assertThat(tableNames, IsCollectionContaining.hasItem("media_objects"));
    assertThat(tableNames, IsCollectionContaining.hasItem("files"));
  }

  @Test
  public void addMediaObject() throws IOException, SQLException {

    // prepare
    database.createMediaObjectsTable();

    String hash = "DAJSDLLJ21lasdVNKASJUD2749324";

    // act
    database.addMediaObject(hash, MediaType.IMAGE);

    // assert
    Collection<MediaObject> queryResults = database.queryMediaObjects();

    assertThat(queryResults, IsCollectionWithSize.hasSize(1));
    MediaObject mediaObject = queryResults.stream().findAny().get();
    assertThat(mediaObject.id(), is(1));
    assertThat(mediaObject.hash(), is(hash));
    assertThat(mediaObject.mediaType(), is(MediaType.IMAGE));
  }

}
