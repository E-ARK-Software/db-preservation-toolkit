package com.databasepreservation.modules.siard.in.content;

import com.databasepreservation.model.data.*;
import com.databasepreservation.model.exception.InvalidDataException;
import com.databasepreservation.model.exception.ModuleException;
import com.databasepreservation.model.structure.DatabaseStructure;
import com.databasepreservation.model.structure.SchemaStructure;
import com.databasepreservation.model.structure.TableStructure;
import com.databasepreservation.model.structure.type.SimpleTypeBinary;
import com.databasepreservation.model.structure.type.SimpleTypeString;
import com.databasepreservation.model.structure.type.Type;
import com.databasepreservation.modules.DatabaseHandler;
import com.databasepreservation.modules.siard.SIARDHelper;
import com.databasepreservation.modules.siard.common.SIARDArchiveContainer;
import com.databasepreservation.modules.siard.in.path.ContentPathImportStrategy;
import com.databasepreservation.modules.siard.in.read.ReadStrategy;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.util.ArrayList;
import java.util.Stack;

/**
 * @author Bruno Ferreira <bferreira@keep.pt>
 */
public class SIARD1ContentImportStrategy extends DefaultHandler implements ContentImportStrategy {
	private final Logger logger = Logger.getLogger(SIARD1ContentImportStrategy.class);

	// Keywords
	private static final String TABLE_KEYWORD = "table";
	private static final String COLUMN_KEYWORD = "c";
	private static final String ROW_KEYWORD = "row";
	private static final String FILE_KEYWORD = "file";

	// ImportStrategy
	private final ContentPathImportStrategy contentPathStrategy;
	private final ReadStrategy readStrategy;
	private SIARDArchiveContainer contentContainer;
	private DatabaseHandler databaseHandler;

	// SAXHandler settings
	static final String JAXP_SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
	static final String W3C_XML_SCHEMA = "http://www.w3.org/2001/XMLSchema";
	static final String JAXP_SCHEMA_SOURCE = "http://java.sun.com/xml/jaxp/properties/schemaSource";
	private SAXErrorHandler errorHandler;
	private SAXParser saxParser;

	// SAXHandler state
	private TableStructure currentTable;
	private InputStream currentTableStream;
	private BinaryCell currentBinaryCell;
	private final Stack<String> tagsStack = new Stack<String>();
	private final StringBuilder tempVal = new StringBuilder();
	private Row row;
	private int rowIndex;

	public SIARD1ContentImportStrategy(ReadStrategy readStrategy, ContentPathImportStrategy contentPathStrategy) {
		this.contentPathStrategy = contentPathStrategy;
		this.readStrategy = readStrategy;
	}

	@Override
	public void importContent(DatabaseHandler handler, SIARDArchiveContainer container, DatabaseStructure databaseStructure) throws ModuleException {
		// set instance state
		this.databaseHandler = handler;
		this.contentContainer = container;

		// pre-setup parser and validation
		SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		SAXParserFactory saxParserFactory = null;
		saxParserFactory = SAXParserFactory.newInstance();
		saxParserFactory.setValidating(true);
		saxParserFactory.setNamespaceAware(true);
		SAXParser saxParser = null;

		// process tables
		for (SchemaStructure schema : databaseStructure.getSchemas()) {
			for (TableStructure table : schema.getTables()) {

				// setup a new validating parser
				InputStream xsdStream = readStrategy.createInputStream(container,
						contentPathStrategy.getTableXSDFilePath(schema.getName(), table.getId()));

				try {
					saxParser = saxParserFactory.newSAXParser();
					saxParser.setProperty(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
					saxParser.setProperty(JAXP_SCHEMA_SOURCE, xsdStream);
				} catch (SAXException e) {
					logger.error("Error validating schema", e);
				} catch (ParserConfigurationException e) {
					logger.error("Error creating XML SAXparser", e);
				}

				// import values from XML
				currentTableStream = readStrategy.createInputStream(container,
						contentPathStrategy.getTableXMLFilePath(schema.getName(), table.getId()));

				currentTable = table;

				SAXErrorHandler errorHandler = new SAXErrorHandler();

				try {
					XMLReader xmlReader = saxParser.getXMLReader();
					xmlReader.setContentHandler(this);
					xmlReader.setErrorHandler(errorHandler);
					xmlReader.parse(new InputSource(currentTableStream));
				} catch (SAXException e) {
					throw new ModuleException("A SAX error occurred during processing of XML table file", e);
				} catch (IOException e) {
					throw new ModuleException("Error while reading XML table file", e);
				}

				if(errorHandler.hasError()){
					throw new ModuleException("Parsing or validation error occurred while reading XML table file (details are above)");
				}

				try {
					currentTableStream.close();
				} catch (IOException e) {
					throw new ModuleException("Could not close XML table input stream");
				}

				try {
					xsdStream.close();
				} catch (IOException e) {
					throw new ModuleException("Could not close table XSD schema input stream");
				}
			}
		}
	}

	private void pushTag(String tag) {
		tagsStack.push(tag);
	}

	private String popTag() {
		return tagsStack.pop();
	}

	private String peekTag() {
		return tagsStack.peek();
	}

	@Override
	public void startDocument() throws SAXException {
		pushTag("");
	}

	@Override
	public void endDocument() throws SAXException {
		// nothing to do
	}

	@Override
	public void startElement(String uri, String localName, String qName,
							 Attributes attr) {
		pushTag(qName);
		tempVal.setLength(0);

		if (qName.equalsIgnoreCase(TABLE_KEYWORD)) {
			this.rowIndex = 0;
			try {
				databaseHandler.handleDataOpenTable(currentTable.getSchema(), currentTable.getId());
			} catch (ModuleException e) {
				logger.error("An error occurred while handling data open table", e);
			}
		} else if (qName.equalsIgnoreCase(ROW_KEYWORD)) {
			row = new Row();
			row.setCells(new ArrayList<Cell>());
			for (int i = 0; i < currentTable.getColumns().size(); i++) {
				row.getCells().add(null);
			}
		} else if (qName.startsWith(COLUMN_KEYWORD)) {
			if (attr.getValue(FILE_KEYWORD) != null) {
				String lobDir = attr.getValue(FILE_KEYWORD);
				int columnIndex = Integer.parseInt(qName.substring(1));

				try {
					FileItem fileItem = new FileItem(readStrategy.createInputStream(contentContainer, lobDir));
					currentBinaryCell = new BinaryCell( //TODO: what about CLOBs? are they also created as BinaryCells?
							String.format("%s.%d", currentTable.getColumns().get(columnIndex - 1).getId(), rowIndex),
							fileItem);
				} catch (ModuleException e) {
					errorHandler.error("Failed to open lob at " + lobDir, e);
				}

				logger.debug(String.format("Binary cell %s on row #%d with lob dir %s",
						currentBinaryCell.getId(), rowIndex, lobDir));
			} else {
				currentBinaryCell = null;
			}
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		String tag = peekTag();
		if (!qName.equals(tag)) {
			throw new InternalError();
		}

		popTag();
		String trimmedVal = tempVal.toString().trim();

		if (tag.equalsIgnoreCase(TABLE_KEYWORD)) {
			try {
				logger.debug("before handle data close");
				databaseHandler.handleDataCloseTable(currentTable.getSchema(), currentTable.getId());
			} catch (ModuleException e) {
				logger.error("An error occurred while handling data close table", e);
			}
		} else if (tag.equalsIgnoreCase(ROW_KEYWORD)) {
			row.setIndex(rowIndex);
			rowIndex++;
			try {
				databaseHandler.handleDataRow(row);
			} catch (InvalidDataException e) {
				logger.error("An error occurred while handling data row", e);
			} catch (ModuleException e) {
				logger.error("An error occurred while handling data row", e);
			}
		} else if (tag.contains(COLUMN_KEYWORD)) {
			// TODO Support other cell types
			String[] subStrings = tag.split(COLUMN_KEYWORD);
			Integer columnIndex = Integer.valueOf(subStrings[1]);
			Type type = currentTable.getColumns().get(columnIndex-1).getType();

			if (type instanceof SimpleTypeString) {
				trimmedVal = SIARDHelper.decode(trimmedVal);
			}

			Cell cell = null;
			if (currentBinaryCell != null) {
				cell = currentBinaryCell;
			} else {
				String id = String.format("%s.%d", currentTable.getColumns().get(columnIndex - 1).getId(), rowIndex);

				if (type instanceof SimpleTypeBinary) {
					// binary data with less than 2000 bytes does not have its own file
					try {
						InputStream is = new ByteArrayInputStream(Hex.decodeHex(trimmedVal.toCharArray()));
						cell = new BinaryCell(id, new FileItem(is));
					} catch (ModuleException e) {
						logger.error("An error occurred while importing in-table binary cell");
					} catch (DecoderException e) {
						logger.error(String.format("Illegal characters in hexadecimal string \"%s\"", trimmedVal), e);
					}
				} else {
					cell = new SimpleCell(id);
					if (trimmedVal.length() > 0) {
						// logger.debug("trimmed: " + trimmedVal);
						((SimpleCell) cell).setSimpledata(trimmedVal);
					} else {
						((SimpleCell) cell).setSimpledata(null);
					}
				}
			}
			row.getCells().set(columnIndex - 1, cell);
		}
	}

	@Override
	public void characters(char buf[], int offset, int len) {
		tempVal.append(buf, offset, len);
	}

	/**
	 * Class to handle SAX Parsing errors
	 */
	private static class SAXErrorHandler implements ErrorHandler {
		private boolean hasError = false;
		private final Logger logger = Logger.getLogger(SAXErrorHandler.class);

		public boolean hasError() {
			return hasError;
		}

		private String getParseExceptionInfo(SAXParseException e) {
			StringBuilder buf = new StringBuilder();

			if (e.getPublicId() != null) {
				buf.append("publicId: ").append(e.getPublicId()).append("; ");
			}

			if (e.getSystemId() != null) {
				buf.append("systemId: ").append(e.getSystemId()).append("; ");
			}

			if (e.getLineNumber() != -1) {
				buf.append("line: ").append(e.getLineNumber()).append("; ");
			}

			if (e.getColumnNumber() != -1) {
				buf.append("column: ").append(e.getColumnNumber()).append("; ");
			}

			if (e.getLocalizedMessage() != null) {
				buf.append(e.getLocalizedMessage());
			}

			return buf.toString();
		}

		@Override
		public void warning(SAXParseException e) throws SAXException {
			logger.warn(getParseExceptionInfo(e));
		}

		public void error(String message, Throwable e){
			logger.error(message);
			hasError = true;
		}

		@Override
		public void error(SAXParseException e) throws SAXException {
			logger.error(getParseExceptionInfo(e));
			hasError = true;
		}

		@Override
		public void fatalError(SAXParseException e) throws SAXException {
			hasError = true;
			throw new SAXException(String.format("Fatal Error: %s", getParseExceptionInfo(e)));
		}
	}
}
