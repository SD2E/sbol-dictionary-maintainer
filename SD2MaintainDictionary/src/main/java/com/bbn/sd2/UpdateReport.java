package com.bbn.sd2;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

class UpdateReport {
    private static Logger log = Logger.getGlobal();
    List<List<String>> reports = new ArrayList<>();
    int condition = 0; // +1 = good report, -1 = failure
    
    public UpdateReport() {
    }
    
    /** Create a new subsection of the report */
    public void subsection(String header) {
        List<String> section = new ArrayList<>();
        section.add(header);
        reports.add(section);
    }
    
    private void addToLastSubsection(String report) {
        if(reports.size()==0) reports.add(new ArrayList<>()); // insert at front if needed
        reports.get(reports.size()-1).add(report); // add to end of subsection
        
    }
    
    /** Report a success in a subsection */
    public void success(String report) { success(report,false); }
    /** Report a success, either in a subsection or standalone */
    public void success(String report, boolean standalone) {
        log.info(report);
        if(condition==0) condition = 1; // upgrade condition if neutral
        if(standalone) reports.add(new ArrayList<>());
        addToLastSubsection(report);
    }
    
    /** Report a failure in a subsection */
    public void failure(String report) { failure(report,false); }
    /** Report a failure, either in a subsection or standalone */
    public void failure(String report, boolean standalone) {
        log.warning(report);
        if(condition>=0) condition = -1; // note failure
        if(standalone) reports.add(new ArrayList<>());
        addToLastSubsection(report);
    }
    
    /** Report a note in a subsection */
    public void note(String report) { note(report,false); }
    /** Report a note, either in a subsection or standalone */
    public void note(String report, boolean standalone) {
        log.info(report);
        if(standalone) reports.add(new ArrayList<>());
        addToLastSubsection(report);
    }
    
    private String getCurrentTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }
    
    public String toString() {
        String report = "Updated "+getCurrentTimeStamp();
        for(int i=0;i<reports.size();i++) {
            if(i==0) report += ": "; else report += "; "; // add in separator punctuation
            List<String> subsection = reports.get(i);
            report += subsection.get(0); // add section header or standalone message
            for(int j=1;j<subsection.size();j++) {
                if(j==1) report += ": "; else report += ", ";// add in separator punctuation
                report += subsection.get(j); // add messages
            }
        }
        return report;
    }
}