package org.imagebattle;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import com.google.common.io.Files;
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
  public void addFile() throws IOException {

    File file = tf.newFile("a.jpg");
    Files.write("hello", file, Charset.forName("UTF-8"));
    String hash = "fhaildfaADLSD12dLJASd";
    database.addMediaObject(hash, MediaType.IMAGE);
    Optional<Integer> id = database.lookupMediaItemId(hash);
    database.addFile(id.get(), file);

    Optional<Integer> lookupFile = database.lookupFile(file);
    assertThat(lookupFile.isPresent(), is(true));
    Integer lookedUpId = lookupFile.get();
    assertThat(lookedUpId, is(1));
  }

  @Test
  public void addToIgnore() throws IOException {
    File file = tf.newFile("a.jpg");
    file.createNewFile();
    database.addToIgnore(file);

    Set<File> ignored = database.queryIgnored();

    assertThat(ignored, hasItem(file));

  }

  @Test
  public void removeFromIgnore() throws IOException {
    File file = tf.newFile("a.jpg");
    Files.write("hello", file, Charset.forName("UTF-8"));
    database.addToIgnore(file);
    final Set<File> ignoredBefore = database.queryIgnored();

    // act
    database.removeFromIgnore(file);

    // assert
    Set<File> ignoredAfter = database.queryIgnored();
    assertThat(ignoredBefore, hasItem(file));
    assertThat(ignoredAfter, not(hasItem(file)));

  }

  @Test
  public void queryIgnoredCorrectMediaType() throws IOException {
    File file = tf.newFile("a.jpg");
    File root = tf.getRoot();
    file.createNewFile();
    database.addToIgnore(file);

    Set<File> ignored = database.queryIgnored(root, MediaType.IMAGE, true);

    assertThat(ignored, hasItem(file));

  }

  @Test
  public void queryIgnoredWrongMediaType() throws IOException {
    File file = tf.newFile("a.mp3");
    File root = tf.getRoot();
    file.createNewFile();
    database.addToIgnore(file);

    Set<File> ignored = database.queryIgnored(root, MediaType.IMAGE, true);

    assertThat(ignored, not(hasItem(file)));
  }

}