package vdrdataservice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.hampelratte.svdrp.responses.highlevel.EPGEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import devplugin.Channel;

public class EpgStore {
    private final File dataDirectory;

    private static final String FILE_DATE_FORMAT = "dd-MM-yyyy";

    private static transient Logger logger = LoggerFactory.getLogger(EpgStore.class);

    public EpgStore(File dataDir) {
        this.dataDirectory = dataDir;
    }

    protected void store(Channel channel, List<EPGEntry> epg) {
        if (epg == null || epg.size() == 0) {
            return;
        }

        // split the epg list into days
        Map<String, List<EPGEntry>> days = new HashMap<String, List<EPGEntry>>();
        SimpleDateFormat sdf = new SimpleDateFormat(FILE_DATE_FORMAT);
        for (EPGEntry entry : epg) {
            String day = sdf.format(entry.getStartTime().getTime());
            List<EPGEntry> daySchedule = days.get(day);
            if (daySchedule == null) {
                daySchedule = load(channel, entry.getStartTime());
                if (daySchedule == null) {
                    daySchedule = new ArrayList<EPGEntry>();
                }
                for (int i = 0; i < daySchedule.size(); i++) {
                    if (daySchedule.get(i).getStartTime().after(entry.getStartTime())) {
                        daySchedule = daySchedule.subList(0, i);
                    }
                }
                days.put(day, daySchedule);
            }
            daySchedule.add(entry);
        }

        // save each day schedule to a different file
        logger.info("Got EPG for {} days", days.size());
        for (Entry<String, List<EPGEntry>> entry : days.entrySet()) {
            File saveFile = getSaveFile(channel, entry.getKey());
            logger.info("Saving EPG to file {}", saveFile);
            FileOutputStream fout = null;
            try {
                fout = new FileOutputStream(saveFile);
                ObjectOutputStream oout = new ObjectOutputStream(fout);
                oout.writeObject(entry.getValue());

                for (EPGEntry epgEntry : entry.getValue()) {
                    logger.debug("{} - {}", epgEntry.getTitle(), epgEntry.getStartTime().getTime());
                }
                logger.debug("############################");
            } catch (Exception e) {
                logger.error("Couldn't save EPG to file " + saveFile, e);
            } finally {
                if (fout != null) {
                    try {
                        fout.close();
                    } catch (IOException e) {
                        logger.error("Couldn't close file " + saveFile, e);
                    }
                }
            }
        }

    }

    protected List<EPGEntry> load(Channel channel, Calendar time) {
        File saveFile = getSaveFile(channel, time);
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(new FileInputStream(saveFile));
            ois.readObject();
        } catch (Exception e) {
            logger.error("Couldn't read file " + saveFile, e);
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    logger.error("Couldn't close file " + saveFile, e);
                }
            }
        }
        return null;
    }

    protected File getSaveFile(Channel channel, Calendar date) {
        SimpleDateFormat sdf = new SimpleDateFormat(FILE_DATE_FORMAT);
        return new File(dataDirectory, channel.getId() + "_" + sdf.format(date.getTime()));
    }

    protected File getSaveFile(Channel channel, String date) {
        return new File(dataDirectory, channel.getId() + "_" + date);
    }

    protected void sweepDataDirectory() {
        File[] files = dataDirectory.listFiles();
        SimpleDateFormat sdf = new SimpleDateFormat(FILE_DATE_FORMAT);
        for (File file : files) {
            String name = file.getName();
            try {
                String[] parts = name.split("_");
                // String channel = parts[0];
                String date = parts[1];
                Calendar yesterday = CalendarUtil.yesterday();

                Calendar updatedOn = Calendar.getInstance();
                updatedOn.setTime(sdf.parse(date));

                if (updatedOn.before(yesterday)) {
                    logger.debug("Deleting old file {}", file);
                    if (!file.delete()) {
                        logger.warn("File {} couldn't be deleted. Data directory not clean.", file);
                    }
                }
            } catch (Exception e) {
                logger.error("Couldn't parse file name {}. Deleting file.", name);
                file.delete();
            }
        }

    }
}
