package org.imagebattle;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * {@link FunctionalInterface} to be used by {@link Database}.
 * 
 * @author KoaGex
 *
 * @param <T>
 *          Maps one row of a {@link ResultSet} to any Object you want.
 */
@FunctionalInterface
public interface RowMapper<T> {

  public T map(ResultSet resultSet) throws SQLException;

}
