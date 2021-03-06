/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/db-preservation-toolkit
 */
package com.databasepreservation.common.io.providers;

import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.databasepreservation.model.exception.ModuleException;

/**
 * Wrapper around a Blob SQL object.
 *
 * @author Bruno Ferreira <bferreira@keep.pt>
 */
public class BlobInputStreamProvider implements InputStreamProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(BlobInputStreamProvider.class);

  private Blob blob;

  /**
   * Saves the blob object to later obtain its data using #createInputStream
   *
   * @param blob
   *          the SQL Blob as provided by JDBC
   */
  public BlobInputStreamProvider(Blob blob) {
    this.blob = blob;
  }

  @Override
  public InputStream createInputStream() throws ModuleException {
    try {
      return blob.getBinaryStream();
    } catch (SQLException e) {
      throw new ModuleException().withMessage("Could not obtain BLOB data as stream").withCause(e);
    }
  }

  @Override
  public void cleanResources() {
    try {
      blob.free();
    } catch (SQLException e) {
      LOGGER.debug("Could not free BLOB object", e);
    }
  }

  @Override
  public long getSize() throws ModuleException {
    try {
      return blob.length();
    } catch (SQLException e) {
      throw new ModuleException().withMessage("Could not get blob size").withCause(e);
    }
  }
}
