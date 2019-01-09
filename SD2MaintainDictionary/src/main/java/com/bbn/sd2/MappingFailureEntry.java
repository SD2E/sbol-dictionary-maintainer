package com.bbn.sd2;

import java.io.IOException;
import java.util.Map;

public class MappingFailureEntry {
    public String experiment;
    public String lab;
    public String item;
    public String status;

    MappingFailureEntry(Map<String, String> rowEntries) throws IOException {
        experiment = rowEntries.get("Experiment");
        if(experiment == null) {
            experiment = "";
        }

        lab = rowEntries.get("Lab");
        if(lab == null) {
            lab = "";
        }

        item = rowEntries.get("Item");
        if(item == null) {
            item = "";
        }

        status = "";
    }

    public String getExperiement() {
        return experiment;
    }

    public String getLab() {
        return lab;
    }

    public String getItem() {
        return item;
    }

    public String getStatus() {
        return status;
    }
}
