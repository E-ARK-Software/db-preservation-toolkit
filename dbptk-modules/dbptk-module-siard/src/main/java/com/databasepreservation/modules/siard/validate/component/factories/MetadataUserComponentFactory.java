/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/db-preservation-toolkit
 */
package com.databasepreservation.modules.siard.validate.component.factories;

import com.databasepreservation.Constants;
import com.databasepreservation.model.reporters.Reporter;
import com.databasepreservation.model.modules.validate.components.ValidatorComponent;
import com.databasepreservation.model.modules.validate.components.ValidatorComponentFactory;
import com.databasepreservation.model.exception.ModuleException;
import com.databasepreservation.modules.siard.validate.component.metadata.MetadataUserValidator;

/**
 * @author Gabriel Barros <gbarros@keep.pt>
 */
public class MetadataUserComponentFactory implements ValidatorComponentFactory {
  private final String MODULE_NAME = Constants.COMPONENT_METADATA_USER;

  @Override
  public String getComponentName() {
    return MODULE_NAME;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public boolean isFirst() {
    return false;
  }

  @Override
  public String next() {
    return Constants.COMPONENT_METADATA_ROLE;
  }

  @Override
  public ValidatorComponent buildComponent(Reporter reporter) throws ModuleException {
    return new MetadataUserValidator(MODULE_NAME);
  }
}
