/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/db-preservation-toolkit
 */
package com.databasepreservation.modules.externalLobs;

import com.databasepreservation.model.data.Cell;
import com.databasepreservation.model.exception.ModuleException;

public interface ExternalLOBSCellHandler {
  Cell handleCell(String cellId, String cellValue) throws ModuleException;
}
