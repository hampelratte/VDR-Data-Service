package vdrdataservice;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;

import org.hampelratte.svdrp.Connection;
import org.hampelratte.svdrp.Response;
import org.hampelratte.svdrp.commands.LSTC;
import org.hampelratte.svdrp.commands.LSTE;
import org.hampelratte.svdrp.parsers.ChannelParser;
import org.hampelratte.svdrp.parsers.EPGParser;
import org.hampelratte.svdrp.responses.highlevel.DVBChannel;
import org.hampelratte.svdrp.responses.highlevel.EPGEntry;
import org.hampelratte.svdrp.responses.highlevel.PvrInputChannel;

import tvdataservice.MutableChannelDayProgram;
import tvdataservice.MutableProgram;
import tvdataservice.SettingsPanel;
import tvdataservice.TvDataUpdateManager;
import util.exc.TvBrowserException;
import util.ui.Localizer;
import devplugin.AbstractTvDataService;
import devplugin.Channel;
import devplugin.ChannelGroup;
import devplugin.ChannelGroupImpl;
import devplugin.Date;
import devplugin.PluginInfo;
import devplugin.ProgressMonitor;
import devplugin.Version;

/**
 * @author <a href="hampelratte@users.sf.net">hampelratte@users.sf.net</a>
 * 
 */
public class VDRDataService extends AbstractTvDataService {

    private static Logger logger = Logger.getLogger(VDRDataService.class.getName());

    private final Localizer localizer = Localizer.getLocalizerFor(VDRDataService.class);

    private VDRDataServiceSettingsPanel settingsPanel;

    private Channel[] channels = new Channel[] {};

    private final Properties props = new Properties();

    private final ChannelGroup cg = new ChannelGroupImpl("vdr", "VDR", "Channels from VDR", "VDR");

    private final PluginInfo pluginInfo = new PluginInfo(
            VDRDataService.class,
            "VDR DataService",
            localizer.msg("desc", "Loads the EPG-Data from VDR (by Klaus Schmidinger) into TV-Browser"),
            "Henrik Niehaus (hampelratte@users.sf.net)");


    @Override
    public void updateTvData(TvDataUpdateManager database, Channel[] channels,
            devplugin.Date date, int dateCount, ProgressMonitor pm)
                    throws TvBrowserException {

        pm.setMaximum(channels.length);
        if(channels.length > 0) {
            Connection conn = null;
            try {
                conn = new Connection(VDRConnection.host, VDRConnection.port, 500, VDRConnection.charset);
                for (int i = 0; i < channels.length; i++) {
                    //pm.setMessage(localizer.msg("getting_data","Getting data from VDR for " + channels[i].getName()));
                    pm.setMessage(localizer.msg("getting_data", "Getting data from VDR for {0}", channels[i].getName()));
                    Response res = conn.send(new LSTE(channels[i].getId(), ""));
                    if (res != null && res.getCode() == 215) {
                        String data = res.getMessage();
                        pm.setMessage(localizer.msg("parsing_data","Parsing data"));
                        MutableChannelDayProgram[] dayPrograms = parseData(data, channels[i], date, dateCount);
                        pm.setMessage(localizer.msg("updating_database","Updating EPG database"));
                        for (int j = 0; j < dayPrograms.length; j++) {
                            if(dayPrograms[j].getProgramCount() > 0) {
                                logger.info("Adding mutable program: " + dayPrograms[j]);
                                database.updateDayProgram(dayPrograms[j]);
                            }
                        }
                    } else {
                        StringBuffer sb = new StringBuffer(channels[i].getName());
                        sb.append(" Error ");
                        if(res != null) {
                            sb.append(res.getCode());
                            sb.append(": ");
                            sb.append(res.getMessage());
                        }
                        pm.setMessage(sb.toString());
                    }

                    pm.setValue(i);
                }
            } catch (Exception e) {
                logger.severe(e.getLocalizedMessage());
                JOptionPane.showMessageDialog(getParentFrame(),
                        localizer.msg("couldnt_connect", "<html>Couldn't connect to VDR<br>{0}</html>",
                                e.getClass().getSimpleName() + ": " + e.getLocalizedMessage()),
                                Localizer.getLocalization(Localizer.I18N_ERROR), JOptionPane.ERROR_MESSAGE);
            } finally {
                if(conn != null) {
                    try {
                        conn.close();
                    } catch (IOException e) {
                        logger.severe("Couldn't close SVDRP connection");
                    }
                }
            }
        }
        pm.setMessage(localizer.msg("success","Successfully retrieved data from VDR"));
    }

    private MutableChannelDayProgram[] parseData(String data, Channel channel, Date date, int dateCount) {
        List<MutableChannelDayProgram> dayProgramList = new ArrayList<MutableChannelDayProgram>();
        MutableChannelDayProgram dayProgram = null;
        MutableProgram program = null;

        List<EPGEntry> entries = new EPGParser().parse(data);
        for (int i = 0; i < dateCount; i++) {
            Calendar start = date.getCalendar();
            start.add(Calendar.DAY_OF_MONTH, i);
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            Calendar stop = date.getCalendar();
            stop.add(Calendar.DAY_OF_MONTH, i + 1);
            stop.set(Calendar.HOUR_OF_DAY, 0);
            stop.set(Calendar.MINUTE, 0);
            stop.set(Calendar.SECOND, 0);
            Date currentDate = new Date(start);
            logger.fine("##############################\nParsing program for " + channel.getName() + " from "
                    +
                    DateFormat.getDateTimeInstance().format(start.getTime()) +
                    " to " +
                    DateFormat.getDateTimeInstance().format(stop.getTime()));

            dayProgram = new MutableChannelDayProgram(currentDate, channel);
            for (EPGEntry entry : entries) {
                if ((entry.getStartTime().after(start) && entry.getEndTime().before(stop))
                        || entry.getStartTime().get(Calendar.DAY_OF_MONTH) == start.get(Calendar.DAY_OF_MONTH)) {
                    program = new MutableProgram(channel, new Date(entry.getStartTime()),
                            entry.getStartTime().get(Calendar.HOUR_OF_DAY),
                            entry.getStartTime().get(Calendar.MINUTE), false);
                    program.setTitle(entry.getTitle());
                    program.setShortInfo(entry.getDescription());
                    program.setDescription(entry.getDescription());
                    dayProgram.addProgram(program);
                    logger.fine("Program: " + entry.getTitle() + " " + entry.getStartTime().getTime() + "-" + entry.getEndTime().getTime());
                }
            }
            dayProgramList.add(dayProgram);
        }

        MutableChannelDayProgram[] progs = new MutableChannelDayProgram[] {};
        progs = dayProgramList.toArray(progs);
        return progs;
    }


    @Override
    public void loadSettings(Properties p) {
        // set defaults
        props.setProperty("vdr.host", "htpc");
        props.setProperty("vdr.port", "2001");
        props.setProperty("charset", "ISO-8859-1");
        props.setProperty("max.channel.number", "100");

        // overwrite defaults with values from configfile
        List<Channel> list = new ArrayList<Channel>();
        Enumeration<?> en = p.keys();
        while (en.hasMoreElements()) {
            String key = (String) en.nextElement();
            props.setProperty(key, p.getProperty(key));
        }

        // create channel[]
        en = props.keys();
        while(en.hasMoreElements()) {
            String key = (String)(en.nextElement());
            if(key.startsWith("CHANNELID")) {
                String id = getChannelID(key);
                if(!id.equals("")) {
                    String channelID = props.getProperty("CHANNELID"+id);
                    String name = props.getProperty("CHANNELNAME"+id);
                    name = name == null ? "Channel("+id+")" : name;
                    String url = props.getProperty("CHANNELURL"+id);
                    url = url == null ? "" : url;
                    String country = props.getProperty("CHANNELCOUNTRY"+id);
                    country = country == null ? Locale.getDefault().getCountry() : country;
                    String copy = props.getProperty("CHANNELCOPYRIGHT"+id);
                    int category = 0;
                    try {
                        category = Integer.parseInt(props.getProperty("CHANNELCATEGORY"+id));
                    } catch (NumberFormatException e) {}
                    copy = copy == null ? "" : copy;
                    Channel chan = new Channel(this, name, channelID, TimeZone.getDefault(), "de", "", "", cg, null, category);
                    //new Channel(this, name, channelID, TimeZone.getDefault(), country, copy, "", cg);
                    list.add(chan);
                }
            }
        }

        channels = list.toArray(channels);

        VDRConnection.host = props.getProperty("vdr.host");
        VDRConnection.port = Integer.parseInt(props.getProperty("vdr.port"));
        VDRConnection.charset = props.getProperty("charset") == null ? "ISO-8859-1" : props.getProperty("charset");
    }


    public String getProperty(String key) {
        return props.getProperty(key);
    }

    public void setProperty(String key, String value) {
        props.setProperty(key, value);
    }

    private String getChannelID(String s) {
        Pattern p = Pattern.compile("^([A-Z]+)(\\d+)$");
        Matcher m = p.matcher(s);
        if(m.matches()) {
            String id = m.group(2);
            return id;
        }
        return "";
    }

    @Override
    public Properties storeSettings() {
        // delete old channels
        sweepOffChannelsFromProps();

        // save the current channels
        for (int i = 0; i < channels.length; i++) {
            if (channels[i].getId() != null) {
                props.setProperty("CHANNELID" + i, channels[i].getId());
            }
            if (channels[i].getName() != null) {
                props.setProperty("CHANNELNAME" + i, channels[i].getName());
            }
            if (channels[i].getWebpage() != null) {
                props.setProperty("CHANNELURL" + i, channels[i].getWebpage());
            }
            if (channels[i].getCountry() != null) {
                props.setProperty("CHANNELCOUNTRY" + i, channels[i]
                        .getCountry());
            }

            props.setProperty("CHANNELCATEGORY" + i, ""+channels[i].getCategories());

            if (channels[i].getCopyrightNotice() != null) {
                props.setProperty("CHANNELCOPYRIGHT" + i, channels[i]
                        .getCopyrightNotice());
            }
        }
        return props;
    }

    private void sweepOffChannelsFromProps() {
        for (Iterator<?> iterator = props.keySet().iterator(); iterator.hasNext();) {
            String key = (String) iterator.next();
            if(key.startsWith("CHANNEL")) {
                iterator.remove();
            }
        }
    }

    @Override
    public boolean hasSettingsPanel() {
        return true;
    }

    @Override
    public SettingsPanel getSettingsPanel() {
        if(settingsPanel == null) {
            settingsPanel = new VDRDataServiceSettingsPanel();
            settingsPanel.setVdrDataService(this);
            settingsPanel.loadSettings();
        }
        return settingsPanel;
    }

    @Override
    public boolean supportsDynamicChannelList() {
        return true;
    }

    @Override
    public PluginInfo getInfo() {
        return pluginInfo;
    }

    @Override
    public ChannelGroup[] getAvailableGroups() {
        ChannelGroup[] cgs = new ChannelGroup[] {cg};
        return cgs;
    }


    @Override
    public Channel[] getAvailableChannels(ChannelGroup cg) {
        if(cg.equals(this.cg)) {
            logger.info("Returning " + channels.length + " channels");
            return this.channels;
        } else {
            return new Channel[] {};
        }
    }

    @Override
    public Channel[] checkForAvailableChannels(ChannelGroup cg, ProgressMonitor pm) throws TvBrowserException {
        if (cg.getId().equals(this.cg.getId())) {
            pm.setMessage(localizer.msg("getting_data", "Getting data from VDR..."));
            // load channel list from vdr
            Response res = VDRConnection.send(new LSTC());
            if (res.getCode() == 250) {
                try {
                    // parse the channel list
                    List<org.hampelratte.svdrp.responses.highlevel.Channel> vdrChannelList = ChannelParser.parse(res.getMessage(), false);
                    List<Channel> channelList = new ArrayList<Channel>();
                    for (Iterator<org.hampelratte.svdrp.responses.highlevel.Channel> iterator = vdrChannelList.iterator(); iterator.hasNext();) {
                        org.hampelratte.svdrp.responses.highlevel.Channel c = iterator.next();

                        int maxChannel = Integer.parseInt(props.getProperty("max.channel.number"));
                        if (maxChannel == 0 || maxChannel > 0 && c.getChannelNumber() <= maxChannel) {
                            // distinguish between radio and tv channels /
                            // pay-tv
                            int category = getChannelCategory(c);
                            // create a new tvbrowser channel object
                            Channel chan = new Channel(this, c.getName(), Integer.toString(c.getChannelNumber()), TimeZone.getDefault(), "de", "", "", cg, null, category, c.getName());
                            channelList.add(chan);
                        }
                    }

                    // convert channel list to an array
                    channels = new Channel[channelList.size()];
                    channels = channelList.toArray(channels);

                } catch (NumberFormatException e) {
                    logger.severe(e.getMessage());
                } catch (ParseException e) {
                    logger.severe(e.getMessage());
                }
            }
            return channels;
        } else {
            return new Channel[] {};
        }
    }

    private int getChannelCategory(org.hampelratte.svdrp.responses.highlevel.Channel c) {
        if(c instanceof DVBChannel) {
            DVBChannel chan = (DVBChannel) c;
            String vpid = chan.getVPID();
            if("0".equals(vpid) || "1".equals(vpid)) { // a radio station
                return Channel.CATEGORY_RADIO;
            } else { // a tv channel
                if(!"0".equals(chan.getConditionalAccess())) {
                    return Channel.CATEGORY_TV | Channel.CATEGORY_PAY_TV;
                } else {
                    return Channel.CATEGORY_TV | Channel.CATEGORY_DIGITAL;
                }
            }
        } else if (c instanceof PvrInputChannel) {
            return Channel.CATEGORY_TV;
        } else {
            return Channel.CATEGORY_NONE;
        }
    }

    @Override
    public ChannelGroup[] checkForAvailableChannelGroups(ProgressMonitor arg0) throws TvBrowserException {
        return getAvailableGroups();
    }

    @Override
    public boolean supportsDynamicChannelGroups() {
        return false;
    }

    @Override
    public void setWorkingDirectory(File dataDir) {}

    public static Version getVersion() {
        return new Version(0, 53);
    }
}