package com.bbn.sd2;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class MappingFailureEntry {
    private String experiment;
    private String lab;
    private String item;
    private String status;
    private String itemId;
    private String itemType;
    private Date lastNotificationTime;
    private SimpleDateFormat dateFormatter;
    private int row;
    private final String statusDatePrefix = "Notification sent at ";
    private boolean notified;
    private boolean valid;
    private final String experimentColumnTag = "Experiment/Run";
    private final String labColumnTag = "Lab";
    private final String itemColumnTag = "Item Name";
    private final String itemIdColumnTag = "Item ID";
    private final String itemTypeColumnTag = "Item Type (Strain or Reagent Tab)";
    private final String statusColumnTag = "Status";

    MappingFailureEntry(Map<String, String> rowEntries, int row) throws IOException {
        this.status = "";
        this.valid = true;
        this.lastNotificationTime = null;
        this.dateFormatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z");
        this.row = row + 1; // Index from 1 instead of 0
        this.notified = false;

        itemType = rowEntries.get(itemTypeColumnTag);

        experiment = rowEntries.get(experimentColumnTag);
        if(experiment == null) {
            experiment = "";
        }

        lab = rowEntries.get(labColumnTag);
        if(lab == null) {
            lab = "";
        }

        item = rowEntries.get(itemColumnTag);
        if(item == null) {
            item = "";
        }

        itemId = rowEntries.get(itemIdColumnTag);
        if(itemId == null) {
            itemId = "";
        }

        if(experiment.length() == 0) {
            status = "Missing " + experimentColumnTag + " value";
            valid = false;
            return;
        }

        if(item.length() == 0) {
            status = "Missing " + itemColumnTag + " value";
            valid = false;
            return;
        }

        if(lab.length() == 0) {
            status = "Missing " + labColumnTag + " value";
            valid = false;
            return;
        }

        if(itemId.length() == 0) {
            status = "Missing " + itemIdColumnTag + " value";
            valid = false;
            return;
        }

        String statusString = rowEntries.get(statusColumnTag);
        if(statusString == null) {
            statusString = "";
        }

        // Parse status field
        String[] statusFields = statusString.split(",");
        for(String statusField : statusFields) {
            if(statusField.startsWith(statusDatePrefix)) {
                String dateString = statusField.substring(statusDatePrefix.length());
                try {
                    lastNotificationTime = dateFormatter.parse(dateString);
                } catch(ParseException e) {
                    throw new IOException("Failed to parse status date");
                }
            }
        }
    }

    public String getExperiment() {
        return experiment;
    }

    public String getLab() {
        return lab;
    }

    public String getItem() {
        return item;
    }

    public String getItemId() {
        return itemId;
    }

    public String getItemType() {
        return itemType;
    }

    public String getStatus() {
        if(!valid) {
            return status;
        }

        String newStatusString = "";

        if(lastNotificationTime != null) {
            newStatusString = statusDatePrefix;
            newStatusString += dateFormatter.format(lastNotificationTime);
        }

        return newStatusString;
    }

    public int getRow() {
        return row;
    }

    public void decrementRow(int delta) {
        row -= delta;
    }

    public boolean getNotified() {
        return notified;
    }

    public void setLastNotificationTime(Date lastEmailDate) {
        this.lastNotificationTime = lastEmailDate;
        this.notified = true;
    }

    public long secondsSinceLastNotification(Date date) {
        if(lastNotificationTime == null) {
            return date.getTime() / 1000L;
        }

        return (date.getTime() - lastNotificationTime.getTime()) / 1000L;
    }

    public boolean getValid() {
        return valid;
    }

    JSONObject toJSON() {
        JSONObject jo = new JSONObject();

        jo.put(experimentColumnTag, experiment);
        jo.put(itemColumnTag, item);
        jo.put(labColumnTag, lab);
        jo.put(itemIdColumnTag, itemId);
        if(itemType != null) {
            jo.put(itemTypeColumnTag, itemType);
        }

        return jo;
    }
}
