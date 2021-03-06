/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/db-preservation-toolkit
 */
package com.databasepreservation.model.exception;

/**
 *  Exception about problems parsing the metadata SIARD paths.
 *
 *
 * @author Miguel Guimarães <mguimaraes@keep.pt>
 */
public class EditDatabaseMetadataParserException extends ModuleException {

  private String faultyArgument = null;

  public EditDatabaseMetadataParserException() {
    super();
  }

  public EditDatabaseMetadataParserException(String message) {
    this();
    withMessage(message);
  }

  public EditDatabaseMetadataParserException withFaultyArgument(String faultyArgument) {
    this.faultyArgument = faultyArgument;
    return this;
  }

  public String getFaultyArgument() { return faultyArgument; }
}