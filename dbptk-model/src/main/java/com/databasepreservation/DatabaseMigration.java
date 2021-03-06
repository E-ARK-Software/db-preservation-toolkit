/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/db-preservation-toolkit
 */
package com.databasepreservation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.databasepreservation.model.exception.ModuleException;
import com.databasepreservation.model.modules.DatabaseImportModule;
import com.databasepreservation.model.modules.DatabaseModuleFactory;
import com.databasepreservation.model.modules.SinkModule;
import com.databasepreservation.model.modules.filters.DatabaseFilterFactory;
import com.databasepreservation.model.modules.filters.DatabaseFilterModule;
import com.databasepreservation.model.modules.filters.ExecutionOrder;
import com.databasepreservation.model.parameters.Parameter;
import com.databasepreservation.model.reporters.Reporter;

/**
 * @author Bruno Ferreira <bferreira@keep.pt>
 */
public class DatabaseMigration {
  // the same reporter is used for all modules
  private Reporter reporter;

  private DatabaseModuleFactory importModuleFactory;
  private HashMap<String, String> importModuleFactoryStringParameters = new HashMap<>();

  private DatabaseModuleFactory exportModuleFactory;
  private HashMap<String, String> exportModuleFactoryStringParameters = new HashMap<>();

  private List<DatabaseFilterFactory> filterFactories = new ArrayList<>();
  private List<HashMap<String, String>> filterFactoriesStringParameters = new ArrayList<>();

  private List<DatabaseFilterModule> filterModules = new ArrayList<>();

  private DatabaseMigration() {

  }

  public static DatabaseMigration newInstance() {
    return new DatabaseMigration();
  }

  public DatabaseImportModule getImportModule() throws ModuleException {
    Map<Parameter, String> importParameters = buildParametersFromStringParameters(importModuleFactory,
      importModuleFactoryStringParameters);
    return importModuleFactory.buildImportModule(importParameters, reporter);
  }

  public void migrate() throws ModuleException {
    validate();

    // get import module and export module instance
    Map<Parameter, String> importParameters = buildParametersFromStringParameters(importModuleFactory,
      importModuleFactoryStringParameters);
    Map<Parameter, String> exportParameters = buildParametersFromStringParameters(exportModuleFactory,
      exportModuleFactoryStringParameters);

    DatabaseImportModule importModule = importModuleFactory.buildImportModule(importParameters, reporter);
    DatabaseFilterModule exportModule = exportModuleFactory.buildExportModule(exportParameters, reporter);

    List<DatabaseFilterModule> beforeFilterModules = new ArrayList<>();
    List<DatabaseFilterModule> afterFilterModules = new ArrayList<>();
    for (int i = 0; i < filterFactories.size(); i++) {
      Map<Parameter, String> filterParameters = new HashMap<>();
      if (!filterFactoriesStringParameters.isEmpty()) {
        filterParameters = buildParametersFromStringParameters(filterFactories.get(i),
          filterFactoriesStringParameters.get(i));
      }

      if (filterFactories.get(i).getExecutionOrder().equals(ExecutionOrder.AFTER)) {
        afterFilterModules.add(filterFactories.get(i).buildFilterModule(filterParameters, reporter));
      } else {
        beforeFilterModules.add(filterFactories.get(i).buildFilterModule(filterParameters, reporter));
      }
    }

    // set reporters
    importModule.setOnceReporter(reporter);
    for (DatabaseFilterModule filterModule : beforeFilterModules) {
      filterModule.setOnceReporter(reporter);
    }

    for (DatabaseFilterModule filterModule : afterFilterModules) {
      filterModule.setOnceReporter(reporter);
    }
    for (DatabaseFilterModule filterModule : filterModules) {
      filterModule.setOnceReporter(reporter);
    }
    exportModule.setOnceReporter(reporter);

    // create module chain with filters in the middle
    Collections.reverse(filterModules);
    Collections.reverse(beforeFilterModules);
    Collections.reverse(afterFilterModules);

    DatabaseFilterModule sinkModule = new SinkModule();

    for (DatabaseFilterModule filterModule : afterFilterModules) {
      sinkModule = filterModule.migrateDatabaseTo(sinkModule);
    }

    sinkModule = exportModule.migrateDatabaseTo(sinkModule);

    for (DatabaseFilterModule filterModule : beforeFilterModules) {
      sinkModule = filterModule.migrateDatabaseTo(sinkModule);
    }

    for (DatabaseFilterModule filterModule : filterModules) {
      sinkModule = filterModule.migrateDatabaseTo(sinkModule);
    }

    importModule.migrateDatabaseTo(sinkModule);
  }

  /**
   * Sets the import module factory that will be used to produce the import module
   */
  public DatabaseMigration importModule(DatabaseModuleFactory importModuleFactory) {
    this.importModuleFactory = importModuleFactory;
    return this;
  }

  /**
   * Adds the specified parameter to be used in the import module during the
   * migration
   */
  public DatabaseMigration importModuleParameter(String parameterLongName, String parameterValue) {
    importModuleFactoryStringParameters.put(parameterLongName, parameterValue);
    return this;
  }

  /**
   * Adds the specified parameters to be used in the import module during the
   * migration
   */
  @SafeVarargs
  public final DatabaseMigration importModuleParameters(Pair<String, String>... parameters) {
    for (Pair<String, String> parameter : parameters) {
      importModuleParameter(parameter.getKey(), parameter.getValue());
    }
    return this;
  }

  /**
   * Adds the specified parameters to be used in the import module during the
   * migration
   */
  public DatabaseMigration importModuleParameters(Map<Parameter, String> parameters) {
    for (Map.Entry<Parameter, String> entry : parameters.entrySet()) {
      importModuleParameter(entry.getKey().longName(), entry.getValue());
    }
    return this;
  }

  /**
   * Sets the export module factory that will be used to produce the import module
   */
  public DatabaseMigration exportModule(DatabaseModuleFactory exportModuleFactory) {
    this.exportModuleFactory = exportModuleFactory;
    return this;
  }

  /**
   * Adds the specified parameter to be used in the export module during the
   * migration
   */
  public DatabaseMigration exportModuleParameter(String parameterLongName, String parameterValue) {
    exportModuleFactoryStringParameters.put(parameterLongName, parameterValue);
    return this;
  }

  /**
   * Adds the specified parameters to be used in the export module during the
   * migration
   */
  @SafeVarargs
  public final DatabaseMigration exportModuleParameters(Pair<String, String>... parameters) {
    for (Pair<String, String> parameter : parameters) {
      exportModuleParameter(parameter.getKey(), parameter.getValue());
    }
    return this;
  }

  /**
   * Adds the specified parameters to be used in the export module during the
   * migration
   */
  public DatabaseMigration exportModuleParameters(Map<Parameter, String> parameters) {
    for (Map.Entry<Parameter, String> entry : parameters.entrySet()) {
      exportModuleParameter(entry.getKey().longName(), entry.getValue());
    }
    return this;
  }

  /**
   * Sets the filter factories that will be used to produce the user specified
   * filters
   */
  public DatabaseMigration filterFactories(List<DatabaseFilterFactory> filterFactories) {
    this.filterFactories = filterFactories;

    return this;
  }

  /**
   * Adds the specified parameter to be used in the specific filter during the
   * migration
   */
  public DatabaseMigration filterParameter(String parameterLongName, String parameterValue, int filterIndex) {
    if (filterFactoriesStringParameters.isEmpty()) {
      filterFactoriesStringParameters.add(new HashMap<>());
    } else if (filterFactoriesStringParameters.size() - 1 < filterIndex) {
      filterFactoriesStringParameters.add(filterIndex, new HashMap<>());
    }
    filterFactoriesStringParameters.get(filterIndex).put(parameterLongName, parameterValue);
    return this;
  }

  /**
   * Adds the specified parameters to be used in the export module during the
   * migration
   */
  public DatabaseMigration filterParameters(List<Map<Parameter, String>> parameters) {
    for (int i = 0; i < parameters.size(); i++) {
      if (parameters.get(i).isEmpty()) {
        filterParameter("", "", i);
      } else {
        for (Map.Entry<Parameter, String> entry : parameters.get(i).entrySet()) {
          filterParameter(entry.getKey().longName(), entry.getValue(), i);
        }
      }
    }
    return this;
  }

  /**
   * Adds the specified filter to this database migration. Multiple filters can be
   * added, and the database information will go through them in the same order
   * they were added here
   */
  public DatabaseMigration filter(DatabaseFilterModule filter) {
    filterModules.add(filter);
    return this;
  }

  /**
   * Sets the reporter to be used by all modules (import, export and filter)
   * during the migration
   */
  public DatabaseMigration reporter(Reporter reporter) {
    this.reporter = reporter;
    return this;
  }

  // auxiliary internal methods

  private void validate() throws ModuleException {
    // import and export modules exist
    if (importModuleFactory == null) {
      throw new ModuleException().withMessage("Import module was not defined");
    }
    if (exportModuleFactory == null) {
      throw new ModuleException().withMessage("Export module was not defined");
    }

    // reporter is present
    if (reporter == null) {
      throw new ModuleException().withMessage("Reporter was not defined");
    }
  }

  private static Map<Parameter, String> buildParametersFromStringParameters(DatabaseModuleFactory moduleFactory,
    HashMap<String, String> stringModuleFactoryParameters) {
    Map<Parameter, String> parameters = new HashMap<>();

    for (Map.Entry<String, String> entry : stringModuleFactoryParameters.entrySet()) {
      Parameter key = moduleFactory.getAllParameters().get(entry.getKey());
      if (key != null) {
        parameters.put(key, entry.getValue());
      }
    }

    return parameters;
  }

  private static Map<Parameter, String> buildParametersFromStringParameters(DatabaseFilterFactory filterFactory,
    HashMap<String, String> stringFilterFactoryParameters) {
    Map<Parameter, String> parameters = new HashMap<>();

    for (Map.Entry<String, String> entry : stringFilterFactoryParameters.entrySet()) {
      Parameter key = filterFactory.getAllParameters().get(entry.getKey());
      if (key != null) {
        parameters.put(key, entry.getValue());
      }
    }

    return parameters;
  }
}
