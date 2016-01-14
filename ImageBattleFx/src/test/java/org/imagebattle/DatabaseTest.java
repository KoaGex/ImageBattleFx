package org.imagebattle;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

import org.hamcrest.collection.IsCollectionWithSize;
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
  public void constructorCreatesTables() {
    Collection<String> tables = database.tables();

    assertThat(tables, hasItem("media_objects"));
    assertThat(tables, hasItem("files"));

  }

  @Test
  public void addMediaObject() throws IOException, SQLException {

    // prepare

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

  @Test
  public void addFile() {
    database.addMediaObject("fhaildfaADLSD12dLJASd", MediaType.IMAGE);
  }

}