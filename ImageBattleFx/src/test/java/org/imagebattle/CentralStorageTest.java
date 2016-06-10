package org.imagebattle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * Testing {@link CentralStorage}.
 * 
 * @author KoaGex
 *
 */
public class CentralStorageTest {
  private static final Logger LOG = LogManager.getLogger();

  @Rule
  public TemporaryFolder tf = new TemporaryFolder();

  @Rule
  public CentralStorageRule centralStorageRule = new CentralStorageRule();

}