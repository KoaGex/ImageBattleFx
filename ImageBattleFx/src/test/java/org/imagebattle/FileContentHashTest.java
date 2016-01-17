package org.imagebattle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;

import com.google.common.io.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileContentHashTest {

  @Rule
  public TemporaryFolder tf = new TemporaryFolder();
  private File file;
  private FileContentHash fileContentHash;

  @Before
  public void setUp() throws IOException {
    file = tf.newFile();
    fileContentHash = new FileContentHash(file);
  }

  @Test
  public void hashLength() throws IOException {

    byte[] content = new byte[] { 1, 2, 3, 4 };
    Files.write(content, file);

    String hash = fileContentHash.hash();

    int length = hash.length();
    assertThat(length, is(62));

  }

  @Test
  public void sameContentSameHash() throws IOException {

    byte[] content = new byte[] { 1, 2, 3, 4 };
    Files.write(content, file);

    String hash = fileContentHash.hash();

    assertThat(hash, is("9f64a747e1b97f131fabb6b447296c9b6f21e79fb3c5356e6c77e89b6a806a"));

  }

  @Test
  public void differentContentDifferentHash() throws IOException {

    byte[] content = new byte[] { 1, 2, 3, 4 };
    Files.write(content, file);
    byte[] content2 = new byte[] { 1, 2, 5, 4 };
    File file2 = tf.newFile();
    Files.write(content2, file2);

    String hash = fileContentHash.hash();
    String hash2 = new FileContentHash(file2).hash();

    assertThat(hash, is(not(hash2)));

  }

}