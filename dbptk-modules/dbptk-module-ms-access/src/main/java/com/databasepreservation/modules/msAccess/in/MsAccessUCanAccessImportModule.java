/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/db-preservation-toolkit
 */
package com.databasepreservation.modules.msAccess.in;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.databasepreservation.model.data.Cell;
import com.databasepreservation.model.data.NullCell;
import com.databasepreservation.model.data.SimpleCell;
import com.databasepreservation.model.exception.InvalidDataException;
import com.databasepreservation.model.exception.ModuleException;
import com.databasepreservation.model.structure.PrivilegeStructure;
import com.databasepreservation.model.structure.RoutineStructure;
import com.databasepreservation.model.structure.SchemaStructure;
import com.databasepreservation.model.structure.TableStructure;
import com.databasepreservation.model.structure.type.Type;
import com.databasepreservation.modules.jdbc.in.JDBCImportModule;
import com.databasepreservation.modules.msAccess.MsAccessHelper;
import com.databasepreservation.modules.msAccess.MsAccessUCanAccessModuleFactory;
import com.databasepreservation.utils.MapUtils;
import com.healthmarketscience.jackcess.Database;

import net.ucanaccess.complex.SingleValue;
import net.ucanaccess.jdbc.UcanaccessConnection;

/**
 * @author Bruno Ferreira <bferreira@keep.pt>
 * @author Luis Faria <lfaria@keep.pt>
 */
public class MsAccessUCanAccessImportModule extends JDBCImportModule {
  private static final Logger LOGGER = LoggerFactory.getLogger(MsAccessUCanAccessImportModule.class);

  private static String INVALID_CHARACTERS_IN_TABLE_NAME = "\'";
  private String password = null;

  public MsAccessUCanAccessImportModule(String moduleName, File msAccessFile, Map<String, String> properties)
    throws ModuleException {
    super("net.ucanaccess.jdbc.UcanaccessDriver",
      "jdbc:ucanaccess://" + msAccessFile.getAbsolutePath() + ";showSchema=true;", new MsAccessHelper(),
      new MsAccessUCanAccessDatatypeImporter(), moduleName, properties);
  }

  public MsAccessUCanAccessImportModule(String moduleName, File msAccessFile, String password) throws ModuleException {
    this(moduleName, msAccessFile, MapUtils.buildMapFromObjects(MsAccessUCanAccessModuleFactory.PARAMETER_FILE,
      msAccessFile, MsAccessUCanAccessModuleFactory.PARAMETER_PASSWORD, password));
    this.password = password;
  }

  public MsAccessUCanAccessImportModule(String moduleName, String accessFilePath) throws ModuleException {
    this(moduleName, new File(accessFilePath),
      MapUtils.buildMapFromObjects(MsAccessUCanAccessModuleFactory.PARAMETER_FILE, accessFilePath));
  }

  public MsAccessUCanAccessImportModule(String moduleName, String accessFilePath, String password)
    throws ModuleException {
    this(moduleName, new File(accessFilePath), password);
  }

  @Override
  protected Connection createConnection() throws ModuleException {
    Connection connection;
    try {
      if (password == null) {
        connection = DriverManager.getConnection(connectionURL);
      } else {
        connection = DriverManager.getConnection(connectionURL + "jackcessOpener=" + CryptCodecOpener.class.getName(),
          null /* username is ignored */, password);
      }
    } catch (SQLException e) {
      throw normalizeException(e, null);
    }
    LOGGER.debug("Connected");
    return connection;
  }

  private Database getInternalDatabase() throws ModuleException {
    return ((UcanaccessConnection) getConnection()).getDbIO();

  }

  private Set<String> getTableNames() throws ModuleException {
    try {
      Database db = getInternalDatabase();
      return db.getTableNames();
    } catch (IOException e) {
      throw new ModuleException().withMessage("could not get table names").withCause(e);
    }
  }

  /**
   * @param schemaName
   * @return
   * @throws SQLException
   */
  @Override
  protected List<RoutineStructure> getRoutines(String schemaName) throws SQLException, ModuleException {
    // TODO add optional fields to routine (use getProcedureColumns)
    Set<RoutineStructure> routines = new HashSet<RoutineStructure>();

    ResultSet rset = getMetadata().getProcedures(dbStructure.getName(), schemaName, "%");
    while (rset.next()) {
      String routineName = rset.getString(3);
      RoutineStructure routine = new RoutineStructure();
      routine.setName(routineName);
      if (rset.getString(7) != null) {
        routine.setDescription(rset.getString(7));
      } else {
        if (rset.getShort(8) == 1) {
          routine.setDescription("Procedure does not " + "return a result");
        } else if (rset.getShort(8) == 2) {
          routine.setDescription("Procedure returns a result");
        }
      }
      routines.add(routine);
    }
    List<RoutineStructure> newRoutines = new ArrayList<RoutineStructure>(routines);
    return newRoutines;
  }

  /**
   * Drops money currency
   */
  @Override
  protected Cell rawToCellSimpleTypeNumericApproximate(String id, String columnName, Type cellType, ResultSet rawData)
    throws SQLException {
    Cell cell = null;
    if ("DOUBLE".equalsIgnoreCase(cellType.getOriginalTypeName())) {
      String data = rawData.getString(columnName);
      if (data != null) {
        String parts[] = data.split("E");
        if (parts.length > 1 && parts[1] != null) {
          LOGGER.warn("Double exponent lost: " + parts[1] + ". From " + data + " -> " + parts[0]);
        }
        cell = new SimpleCell(id, parts[0]);
      } else {
        cell = new NullCell(id);
      }
    } else {
      String value;
      if ("float4".equalsIgnoreCase(cellType.getOriginalTypeName())) {
        Float f = rawData.getFloat(columnName);
        value = f.toString();
      } else {
        Double d = rawData.getDouble(columnName);
        value = d.toString();
      }
      if (rawData.wasNull()) {
        cell = new NullCell(id);
      } else {
        cell = new SimpleCell(id, value);
      }
    }
    return cell;
  }

  /**
   * @return the database privileges
   * @throws SQLException
   */
  @Override
  protected List<PrivilegeStructure> getPrivileges() throws SQLException {
    reporter.notYetSupported("roles importing", "MS Access import module");
    return new ArrayList<PrivilegeStructure>();
  }

  @Override
  protected Set<String> getIgnoredImportedSchemas() {
    HashSet ignore = new HashSet<String>();
    ignore.add("INFORMATION_SCHEMA");
    ignore.add("SYSTEM_LOBS");
    ignore.add("UCA_METADATA");
    return ignore;
  }

  /**
   * @param schema
   *          the schema structure @return the database tables of a given
   *          schema @throws SQLException @throws
   */
  @Override
  protected List<TableStructure> getTables(SchemaStructure schema) throws SQLException, ModuleException {
    List<TableStructure> tables = new ArrayList<>();

    Set<String> tableNames = null;
    try {
      tableNames = getTableNames();
    } catch (ModuleException e) {
      tableNames = new HashSet<>();
      LOGGER.debug("Could not obtain table names list, exporting everything", e);
    }

    ResultSet rset = getMetadata().getTables(dbStructure.getName(), schema.getName(), "%", new String[] {"TABLE"});
    int tableIndex = 1;
    while (rset.next()) {
      String tableName = rset.getString(3);

      // add only "real" tables, or all if table info is not available
      if (!tableNames.isEmpty() && !tableNames.contains(tableName)) {
        continue;
      }

      String tableDescription = rset.getString(5);

      if (StringUtils.containsAny(tableName, INVALID_CHARACTERS_IN_TABLE_NAME)) {
        LOGGER.warn("Ignoring table " + tableName + " in schema " + schema.getName()
          + " because it contains one of these non-supported characters: " + INVALID_CHARACTERS_IN_TABLE_NAME);
        reporter.ignored("table " + tableName + " in schema " + schema.getName(),
          "it contains one of these non-supported characters: " + INVALID_CHARACTERS_IN_TABLE_NAME);
      } else if (getModuleConfiguration().isSelectedTable(schema.getName(), tableName)) {
        LOGGER.info("Obtaining table structure for " + schema.getName() + "." + tableName);
        tables.add(getTableStructure(schema, tableName, tableIndex, tableDescription, false));
        tableIndex++;
      } else {
        LOGGER.info("Ignoring table " + schema.getName() + "." + tableName);
      }
    }
    return tables;
  }

  @Override
  protected Cell rawToCellUnsupportedDataType(String id, String columnName, Type cellType, ResultSet rawData)
    throws InvalidDataException {
    Cell cell;
    try {
      SingleValue[] data = (SingleValue[]) rawData.getObject(columnName);
      if (data == null || data.length == 0) {
        cell = new NullCell(id);
      } else {
        cell = new SimpleCell(id, singleValueArrayToString(data));
      }
    } catch (ClassCastException | SQLException e) {
      LOGGER.debug("Could not export cell of unsupported datatype: OTHER", e);
      cell = new NullCell(id);
    }
    return cell;
  }

  private String singleValueArrayToString(SingleValue[] values) {
    StringBuilder str = new StringBuilder(values[0].getValue().toString());
    for (int i = 1; i < values.length; i++) {
      str.append(", ").append(values[i].getValue().toString());
    }
    return str.toString();
  }
}
