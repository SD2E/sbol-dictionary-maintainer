package com.bbn.sd2;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.sbolstandard.core2.SBOLDocument;

import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.ValueRange;

public class DictionaryEntry {
	private static Logger log = Logger.getGlobal();
	public String tab = null;
	public int row_index = -1;
	public StatusCode statusCode = StatusCode.VALID;
	public String statusLog = null;  // Store additional info for the user, such as when comparing entries across columns
	public String name = null;
	public String type = null;
	public URI uri = null;
	public Date lastNotifyTime = new Date(0);
	public Map<String,Set<String>> labUIDs = new HashMap<>();
	public enum StubStatus { YES, NO, UNDEFINED };
	public StubStatus stub = StubStatus.UNDEFINED;
	public boolean attribute = false;
	public URI attributeDefinition = null;
	public String definitionImport = "";
	public Map<String, Integer> header_map;
	public Set<String> aliasNames = new HashSet<String>();
	public boolean changed = false;
	public boolean dictionaryEntryChanged = false;
	public SBOLDocument document = null;
	public Color statusColor;
	public Integer definitionURIColumn = null;
	public UpdateReport report = new UpdateReport();
	public Date modifiedDate = null;
	private final String lastNotifyTag = "Last Notify ";
	private SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
	private Map<String, String> labUIDMap = DictionaryMaintainerApp.labUIDMap;
	private Map<String, String> reverseLabUIDMap = DictionaryMaintainerApp.reverseLabUIDMap;
	private SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
	private SimpleDateFormat sdfDate2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

	public List<ValueRange> spreadsheetUpdates = new ArrayList<ValueRange>();

	private boolean compareNull(Object o1, Object o2) {
		if(o1 == null) {
			if(o2 != null) {
				return false;
			}
		} else if(o2 == null) {
			return false;
		}

		return true;
	}

	public boolean equals(DictionaryEntry e) {
		if(!compareNull(e.name, name)) {
			return false;
		}

		if((name != null) && !e.name.equals(name)) {
			return false;
		}

		if(!compareNull(e.type, type)) {
			return false;
		}

		if((type != null) && !e.type.equals(type)) {
			return false;
		}

		if(!compareNull(e.uri, uri)) {
			return false;
		}

		if((uri != null) && !e.uri.equals(uri)) {
			return false;
		}

		if(!compareNull(e.attributeDefinition,
				attributeDefinition)) {
			return false;
		}

		if((attributeDefinition != null) &&
				!e.attributeDefinition.equals(attributeDefinition)) {
			return false;
		}
		if(!aliasNames.equals(e.aliasNames)) {
			return false;
		}
		for(String lab : e.labUIDs.keySet()) {
			Set<String> map1 = labUIDs.get(lab);
			Set<String> map2 = e.labUIDs.get(lab);

			if(!compareNull(map1, map2)) {
				return false;
			}

			if((map1 != null) && !map1.equals(map2)) {
				return false;
			}
		}

		if(e.labUIDs.size() != labUIDs.size()) {
			return false;
		}

		if(e.attribute != attribute) {
			return false;
		}

		return true;
	}

	public String stubString() {
		switch(stub) {
		case YES:
			return "YES";

		case NO:
			return "NO";

		default:
			return "";
		}

	}

	public DictionaryEntry(DictionaryEntry src) {
		row_index = src.row_index;
		statusCode = src.statusCode;
		statusLog = src.statusLog;
		tab = src.tab;
		if(src.statusColor != null) {
			statusColor = src.statusColor.clone();
		}
		name = src.name;
		type = src.type;
		uri = src.uri;
		report = new UpdateReport( src.report );
		lastNotifyTime = new Date( lastNotifyTime.getTime() );

		for(ValueRange v : src.spreadsheetUpdates) {
			spreadsheetUpdates.add( v.clone() );
		}

		labUIDs = new HashMap<>();
		for(String key : src.labUIDs.keySet()) {
			Set<String> uidSet = null;
			Set<String> srcSet = src.labUIDs.get(key);

			if(srcSet != null) {
				uidSet = new TreeSet<>();

				for(String labUID : srcSet) {
					uidSet.add(labUID);
				}
			}

			labUIDs.put(key, uidSet);
		}

		for(String alias : src.aliasNames) {
			aliasNames.add(alias);
		}

		stub = src.stub;
		attribute = src.attribute;
		attributeDefinition = src.attributeDefinition;
		definitionImport = src.definitionImport;
		header_map = src.header_map;
		changed = src.changed;
		modifiedDate = src.modifiedDate;

		// Should this be a deep copy?
		document = src.document;
	}

	public void addNotificationDateToStatus() {
		report.success(lastNotifyTag + dateFormatter.format(lastNotifyTime));
	}

	private boolean fullbox(List<Object> row,int i) {
		return row.size()>i && row.get(i).toString().length()>0;
	}

	public DictionaryEntry(String tab, Map<String, Integer> header_map, int row_number, List<Object> row) throws IOException, GeneralSecurityException {
		this.tab = tab;
		row_index = row_number;

		if (fullbox(row, header_map.get("Common Name")))
			name = row.get(header_map.get("Common Name")).toString();
		else
			statusCode = StatusCode.MISSING_NAME;
		log.info("Scanning entry " + name);

		if(fullbox(row, header_map.get("Type"))) {
			type = row.get(header_map.get("Type")).toString();
			// if type is restricted, watch out for it
			if(!MaintainDictionary.validType(tab, type))
				statusCode = StatusCode.INVALID_TYPE;
		}
		else
			statusCode = StatusCode.MISSING_TYPE;

		if(fullbox(row, header_map.get("Status"))) {
			statusLog = row.get(header_map.get("Status")).toString();
			int idx = statusLog.indexOf(lastNotifyTag);
			if(idx >= 0) {
				idx += lastNotifyTag.length();
				try {
					String dateStr = statusLog.substring(idx);
					lastNotifyTime = dateFormatter.parse(dateStr);
				} catch(Exception e) {
				}
			}
		}

		if(fullbox(row, header_map.get("Last Updated"))) {
			try {
				String dateString = (String)row.get(header_map.get("Last Updated"));
				modifiedDate = sdfDate.parse( dateString );
			} catch(Exception e) {
			}
		}

		if("Attribute".equals(type)) attribute = true; // check if it's an attribute
		if(fullbox(row, header_map.get("SynBioHub URI"))) uri = URI.create(row.get(header_map.get("SynBioHub URI")).toString());

		String attributeStr = null;
		Integer col = header_map.get("Definition URI / CHEBI ID");
		if(col == null) {
			col = header_map.get("Definition URI");
		}
		definitionURIColumn = col;

		if((col != null) && (fullbox(row, col))) {
			attributeStr = (String)row.get(col);

			try {
				attributeDefinition = new URI(attributeStr);
			} catch(Exception e) {
			}
		}

		col = header_map.get("Alias Names");
		if(col != null) {
			if(fullbox(row, col)) {
				try {
					String aliasName_cell = row.get(col).toString();
					String[] aliasList = aliasName_cell.split(";");
					for(String aliasVal : aliasList) {
						String trimmedVal = aliasVal.trim();
						if(trimmedVal.length() != 0) {
							aliasNames.add(trimmedVal);
						}
					}
				} catch (Exception e) {
					// TODO: handle exception
				}

			}
		}

		col = header_map.get("Definition Import");
		if(col != null) {
			try {
				URI definitionImportURI = new URI((String)row.get(col));
				definitionImport = definitionImportURI.toString();
			} catch(Exception e) {
			}
		}

		for(String uidLabel : labUIDMap.keySet()) {
			String uidTag = labUIDMap.get(uidLabel);

			Integer column = header_map.get(uidLabel);
			if(column == null) {
				continue;
			}

			if(fullbox(row, column)) {
				String cellValue = (String)row.get(header_map.get(uidLabel));
				Set<String> uidSet = new TreeSet<>();
				uidSet.addAll(Arrays.asList(cellValue.split("\\s*,\\s*")));
				labUIDs.put(uidTag, uidSet);
			} else {
				labUIDs.put(uidTag, null);
			}
		}

		if (header_map.get("Stub Object?") != null && fullbox(row, header_map.get("Stub Object?"))) {
			String value = row.get(header_map.get("Stub Object?")).toString();
			if(value.equals("YES")) {
				stub = StubStatus.YES;
			} else if(value.equals("NO")) {
				stub = StubStatus.NO;
			}
		}

		this.header_map = header_map;
	}

	public Map<String, String> generateFieldMap() {
		Map<String, String> fieldMap = new TreeMap<String, String>();

		// Add Lab UIDs
		for(String key : labUIDs.keySet()) {
			String uidsString = null;

			Set<String> uidSet = labUIDs.get(key);

			List<String> uidList = new ArrayList<>();
			if(uidSet != null) {
				uidList.addAll(uidSet);
			}

			if(uidList.isEmpty()) {
				uidsString = "";
			} else {
				// The uid string is a comma-separated list.
				// Sort the list so the string can be compared later
				Collections.sort(uidList);
				for(String uidElement : uidList) {
					if(uidsString == null) {
						uidsString = uidElement;
					} else {
						uidsString = uidsString + ", " + uidElement;
					}
				}
			}

			fieldMap.put(reverseLabUIDMap.get(key), uidsString);
		}


		fieldMap.put("Common Name", name);
		fieldMap.put("Stub Object?", stubString());
		fieldMap.put("Type", type);
		if(attributeDefinition != null) {
			fieldMap.put("Definition URI", attributeDefinition.toString() );
		}

		return fieldMap;
	}

	public Set<String> itemIdsForLabUID(String labUID) {
		Set<String> itemIds = new TreeSet<>();

		String key = labUID + " UID";
		String uidKey = labUIDMap.get(key);

		if(uidKey != null) {
			Set<String> _itemIds = labUIDs.get(uidKey);

			if(_itemIds != null) {
				itemIds.addAll( _itemIds );
			}
		}

		return itemIds;
	}

	public Request setColor(String columnName, Color color) throws Exception {
		char col = (char) ('A' + header_map.get(columnName));

		Integer sheetId =
				DictionaryAccessor.getCachedSheetProperties(tab).getProperties().getSheetId();

		return DictionaryAccessor.setStatusColor(this.row_index - 1, col, sheetId, color);
	}

	public boolean setModifiedDate(String dateString) {
		try {
			modifiedDate = sdfDate.parse(dateString);
		} catch(Exception e) {
			try {
				modifiedDate = sdfDate2.parse(dateString);
			} catch(Exception e2) {
				return false;
			}
		}

		return true;
	}

	public String getModifiedDate() {
		if(modifiedDate == null) {
			return null;
		}
		return sdfDate.format(modifiedDate);
	}

	//    public boolean validType() {
	//        if(allowedTypes==null) return true; // if we don't have restrictions, don't worry about it
	//        for(String type : allowedTypes) {
	//            if(type.equals(this.type))
	//                return true;
	//        }
	//        return false;
	//    }
	//
	//    public String allowedTypes() {
	//        String s = "";
	//        if(allowedTypes.length==0) s+="(INTERNAL ERROR: no valid types available)";
	//        if(allowedTypes.length>1) s+="one of ";
	//        for(int i=0;i<allowedTypes.length;i++) {
	//            if(i>0 && allowedTypes.length>2) s+= ", ";
	//            if(i>0 && i==allowedTypes.length-1) s+="or ";
	//            s+="'"+allowedTypes[i]+"'";
	//        }
	//        return s;
	//    }
}
