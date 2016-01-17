package org.imagebattle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileContentHash {
  private static final Logger LOG = LogManager.getLogger();

  private final File file;

  public FileContentHash(File file) {
    this.file = file;
  }

  /**
   * @return A hash calculated by using the whole file content.
   */
  public String hash() {
    BufferedReader br = null;
    try {
      try {
        final long start = System.currentTimeMillis();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        br = new BufferedReader(new InputStreamReader(new DigestInputStream(//
            new FileInputStream(file), md)));
        br.lines().count();

        byte[] digest = md.digest();
        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : digest) {
          String hexString2 = Integer.toHexString(0xff & b);
          stringBuilder.append(hexString2);
        }
        String hash = stringBuilder.toString();
        long end = System.currentTimeMillis();
        LOG.debug("hash time for file   {}   with size:  {}   took:  {}", file.getAbsolutePath(),
            file.length(), (end - start));
        System.err.println();
        return hash;

      } finally {
        if (br != null) {
          br.close();
        }
      }
    } catch (NoSuchAlgorithmException | IOException e) {
      throw new RuntimeException(e);
    }
  }
}
