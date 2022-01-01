package vdrdataservice;

import devplugin.Channel;
import devplugin.ChannelGroup;
import devplugin.Date;
import devplugin.ProgressMonitor;
import devplugin.*;
import org.hampelratte.svdrp.Connection;
import org.hampelratte.svdrp.Response;
import org.hampelratte.svdrp.commands.LSTC;
import org.hampelratte.svdrp.commands.LSTE;
import org.hampelratte.svdrp.parsers.ChannelParser;
import org.hampelratte.svdrp.parsers.EPGParser;
import org.hampelratte.svdrp.responses.highlevel.*;
import org.hampelratte.svdrp.responses.highlevel.Stream.CONTENT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tvdataservice.MutableChannelDayProgram;
import tvdataservice.MutableProgram;
import tvdataservice.SettingsPanel;
import tvdataservice.TvDataUpdateManager;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static devplugin.ProgramFieldType.INFO_TYPE;
import static devplugin.ProgramFieldType.SHORT_DESCRIPTION_TYPE;

/**
 * @author <a href="hampelratte@users.sf.net">hampelratte@users.sf.net</a>
 */
public class VDRDataService extends AbstractTvDataService {

    private static final Logger LOG = LoggerFactory.getLogger(VDRDataService.class);

    private static final util.i18n.Localizer I18N = util.i18n.Localizer.getLocalizerFor(VDRDataService.class);

    private VDRDataServiceSettingsPanel settingsPanel;

    private Channel[] channels = new Channel[]{};

    private final Properties props = new Properties();

    private final ChannelGroup cg = new ChannelGroupImpl("vdr", "VDR", "Channels from VDR", "VDR");

    private final PluginInfo pluginInfo = new PluginInfo(VDRDataService.class, "VDR DataService", I18N.msg("desc",
            "Loads the EPG-Data from VDR (by Klaus Schmidinger) into TV-Browser"), "Henrik Niehaus (hampelratte@users.sf.net)");

    @Override
    public void updateTvData(TvDataUpdateManager database, Channel[] channels, devplugin.Date date, int dateCount, ProgressMonitor pm) {

        pm.setMaximum(channels.length);
        if (channels.length > 0) {
            Connection conn = null;
            try {
                conn = new Connection(VDRConnection.host, VDRConnection.port, 500, VDRConnection.charset);
                for (int i = 0; i < channels.length; i++) {
                    pm.setMessage(I18N.msg("getting_data", "Getting data from VDR for {0}", channels[i].getName()));
                    Response res = conn.send(new LSTE(channels[i].getId(), ""));
                    if (res != null && res.getCode() == 215) {
                        String data = res.getMessage();
                        pm.setMessage(I18N.msg("parsing_data", "Parsing data"));
                        MutableChannelDayProgram[] dayPrograms = parseData(data, channels[i], date, dateCount);
                        pm.setMessage(I18N.msg("updating_database", "Updating EPG database"));
                        for (int j = 0; j < dayPrograms.length; j++) {
                            if (dayPrograms[j].getProgramCount() > 0) {
                                LOG.debug("Adding mutable program: {}", dayPrograms[j]);
                                Iterator<Program> it = dayPrograms[j].getPrograms();

                                while (it.hasNext()) {
                                    Program p = it.next();
                                    LOG.debug("Adding Program {} {} {}", new Object[]{p.getTitle(), p.getDateString(), p.getTimeString()});
                                }

                                database.updateDayProgram(dayPrograms[j]);
                            }
                        }
                    } else {
                        StringBuilder sb = new StringBuilder(channels[i].getName());
                        sb.append(" Error ");
                        if (res != null) {
                            sb.append(res.getCode());
                            sb.append(": ");
                            sb.append(res.getMessage());
                        }
                        pm.setMessage(sb.toString());
                    }

                    pm.setValue(i);
                }
            } catch (Exception e) {
                LOG.error("Error while updating the EPG", e);
                var message = I18N.msg("couldnt_connect", "<html>Couldn't connect to VDR<br>{0}</html>",
                        e.getClass().getSimpleName() + ": " + e.getLocalizedMessage());
                JOptionPane.showMessageDialog(
                        getParentFrame(), message, I18N.msg(util.i18n.Localizer.I18N_ERROR, "Translation error"),
                        JOptionPane.ERROR_MESSAGE);
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (IOException e) {
                        LOG.error("Couldn't close SVDRP connection");
                    }
                }
            }
        }
        pm.setMessage(I18N.msg("success", "Successfully retrieved data from VDR"));
    }

    private MutableChannelDayProgram[] parseData(String data, Channel channel, Date date, int dateCount) {
        List<MutableChannelDayProgram> dayProgramList = new ArrayList<>();
        MutableChannelDayProgram dayProgram;
        MutableProgram program;

        // parse the data
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
            dayProgram = new MutableChannelDayProgram(currentDate, channel);
            for (EPGEntry entry : entries) {
                if ((entry.getStartTime().after(start) && entry.getEndTime().before(stop))
                        || entry.getStartTime().get(Calendar.DAY_OF_MONTH) == start.get(Calendar.DAY_OF_MONTH)) {
                    program = entryToProgram(channel, entry);
                    dayProgram.addProgram(program);
                }
            }

            // VDR only returns EPG from "now" on. So we have to copy programs to the new dayProgram, which ran today already
            if (i == 1) { // today
                Iterator<Program> iterator = getPluginManager().getChannelDayProgram(currentDate, channel);
                LOG.info("ChannelDayProgram {}", iterator);
                while (iterator.hasNext()) {
                    Calendar now = Calendar.getInstance();
                    int nowMinutesSinceMidnight = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
                    Program p = iterator.next();

                    // if the end time of the old program is in the past, we have to add this program to the
                    // new day program
                    int endTimeSinceMidnight = p.getStartTime() + p.getLength();
                    if (endTimeSinceMidnight <= nowMinutesSinceMidnight) {
                        dayProgram.addProgram(toMutableProgram(p));
                    }
                }
            }

            dayProgramList.add(dayProgram);
        }

        MutableChannelDayProgram[] progs = new MutableChannelDayProgram[]{};
        progs = dayProgramList.toArray(progs);
        return progs;
    }

    private MutableProgram entryToProgram(Channel channel, EPGEntry entry) {
        MutableProgram program = new MutableProgram(channel, new Date(entry.getStartTime()), entry.getStartTime().get(Calendar.HOUR_OF_DAY), entry
                .getStartTime().get(Calendar.MINUTE), false);

        // set the title
        program.setTitle(entry.getTitle());

        // set the description
        program.setDescription(entry.getDescription());

        // set the short info, if available
        if (entry.getShortText() != null && !entry.getShortText().trim().isEmpty()) {
            program.setShortInfo(entry.getShortText());
        } else {
            program.setShortInfo(entry.getDescription());
        }

        // this field accumulates different info flags, which will show up as small icons
        // flags are combined by OR-ing the different values
        int infoBits = 0;

        // set the genre, if available
        if (!entry.getGenres().isEmpty()) {
            // possible TVB categories
            // Program.INFO_CATEGORIE_ARTS
            // Program.INFO_CATEGORIE_CHILDRENS
            // Program.INFO_CATEGORIE_DOCUMENTARY
            // Program.INFO_CATEGORIE_MAGAZINE_INFOTAINMENT
            // Program.INFO_CATEGORIE_MOVIE
            // Program.INFO_CATEGORIE_NEWS
            // Program.INFO_CATEGORIE_OTHERS
            // Program.INFO_CATEGORIE_SERIES
            // Program.INFO_CATEGORIE_SHOW
            // Program.INFO_CATEGORIE_SPORTS

            for (Genre genre : entry.getGenres()) {
                int code = genre.getCode();
                if (code >= 0x10 && code <= 0x1F) {
                    infoBits |= Program.INFO_CATEGORIE_MOVIE;
                    if (code == 0x15) {
                        infoBits |= Program.INFO_CATEGORIE_SERIES;
                    }
                } else if (code >= 0x20 && code <= 0x2F) {
                    infoBits |= Program.INFO_CATEGORIE_NEWS;
                } else if (code >= 0x30 && code <= 0x3F) {
                    infoBits |= Program.INFO_CATEGORIE_SHOW;
                } else if (code >= 0x40 && code <= 0x4F) {
                    infoBits |= Program.INFO_CATEGORIE_SPORTS;
                } else if (code >= 0x50 && code <= 0x5F) {
                    infoBits |= Program.INFO_CATEGORIE_CHILDRENS;
                } else if (code >= 0x70 && code <= 0x7F) {
                    infoBits |= Program.INFO_CATEGORIE_ARTS;
                } else if (code >= 0x90 && code <= 0xAF) {
                    infoBits |= Program.INFO_CATEGORIE_MAGAZINE_INFOTAINMENT;
                }
            }

        }

        // analyze the available streams and set the format information
        if (!entry.getStreams().isEmpty()) {
            for (Stream stream : entry.getStreams()) {
                if (stream.getContent() == CONTENT.H264) {
                    if (stream.getType() >= 0x0B && stream.getType() <= 0x10) {
                        infoBits |= Program.INFO_VISION_HD;
                    }
                    switch (stream.getType()) {
                        case 0x01, 0x05 -> infoBits |= Program.INFO_VISION_4_TO_3;
                        case 0x03, 0x04, 0x07, 0x08, 0x0B, 0x0C, 0x0F, 0x10 -> infoBits |= Program.INFO_VISION_16_TO_9;
                        default -> { /* nothing */ }
                    }
                } else if (stream.getContent() == CONTENT.MP2V) {
                    if (stream.getType() >= 0x09 && stream.getType() <= 0x10) {
                        infoBits |= Program.INFO_VISION_HD;
                    }
                    switch (stream.getType()) {
                        case 0x01, 0x05, 0x09, 0x0D -> infoBits |= Program.INFO_VISION_4_TO_3;
                        case 0x02, 0x03, 0x04, 0x06, 0x07, 0x08, 0x0A, 0x0B, 0x0C, 0x0E, 0x0F, 0x10 -> infoBits |= Program.INFO_VISION_16_TO_9;
                        default -> { /* nothing */ }
                    }
                } else if (stream.getContent() == CONTENT.MP2A) {
                    if (stream.getType() == 0x03) {
                        infoBits |= Program.INFO_AUDIO_STEREO;
                    } else if (stream.getType() == 0x01) {
                        infoBits |= Program.INFO_AUDIO_MONO;
                    } else if (stream.getType() == 0x40) {
                        infoBits |= Program.INFO_AUDIO_DESCRIPTION;
                    }
                } else if (stream.getContent() == CONTENT.AC3) {
                    if (stream.getType() == 0x44) {
                        infoBits |= Program.INFO_AUDIO_DOLBY_DIGITAL_5_1;
                    }
                } else if (stream.getContent() == CONTENT.SUBTITLE && stream.getType() >= 0x20 && stream.getType() <= 0x24) {
                    infoBits |= Program.INFO_SUBTITLE_FOR_AURALLY_HANDICAPPED;
                }
            }

            program.setIntField(ProgramFieldType.INFO_TYPE, infoBits);
        }

        return program;
    }

    private MutableProgram toMutableProgram(Program entry) {
        MutableProgram program = new MutableProgram(entry.getChannel(), entry.getDate(), entry.getHours(), entry.getMinutes(), false);

        // set the title
        program.setTitle(entry.getTitle());

        // set the description
        program.setDescription(entry.getDescription());

        // set the short info, if available
        if (entry.getTextField(SHORT_DESCRIPTION_TYPE) != null && !entry.getTextField(SHORT_DESCRIPTION_TYPE).trim().isEmpty()) {
            program.setShortInfo(entry.getTextField(SHORT_DESCRIPTION_TYPE));
        } else {
            program.setShortInfo(entry.getDescription());
        }

        // set info bits like category, video and audio attributes etc.
        program.setIntField(INFO_TYPE, entry.getIntField(INFO_TYPE));

        return program;
    }

    @Override
    public void loadSettings(Properties p) {
        // set defaults
        props.setProperty("vdr.host", "htpc");
        props.setProperty("vdr.port", "2001");
        props.setProperty("charset", "ISO-8859-1");
        props.setProperty("max.channel.number", "100");

        // overwrite defaults with values from config file
        List<Channel> list = new ArrayList<>();
        Enumeration<?> en = p.keys();
        while (en.hasMoreElements()) {
            String key = (String) en.nextElement();
            props.setProperty(key, p.getProperty(key));
        }

        // create channel[]
        en = props.keys();
        while (en.hasMoreElements()) {
            String key = (String) (en.nextElement());
            if (key.startsWith("CHANNELID")) {
                String id = getChannelID(key);
                if (!id.equals("")) {
                    String channelID = props.getProperty("CHANNELID" + id);
                    String name = props.getProperty("CHANNELNAME" + id);
                    name = name == null ? "Channel(" + id + ")" : name;
                    int category = 0;
                    try {
                        category = Integer.parseInt(props.getProperty("CHANNELCATEGORY" + id));
                    } catch (NumberFormatException e) {
                    }
                    Channel chan = new Channel(this, name, channelID, TimeZone.getDefault(), "de", "", "", cg, null, category);
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
        if (m.matches()) {
            return m.group(2);
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
            if (channels[i].getBaseCountry() != null) {
                props.setProperty("CHANNELCOUNTRY" + i, channels[i].getBaseCountry());
            }

            props.setProperty("CHANNELCATEGORY" + i, "" + channels[i].getCategories());

            if (channels[i].getCopyrightNotice() != null) {
                props.setProperty("CHANNELCOPYRIGHT" + i, channels[i].getCopyrightNotice());
            }
        }
        return props;
    }

    private void sweepOffChannelsFromProps() {
        for (Iterator<?> iterator = props.keySet().iterator(); iterator.hasNext(); ) {
            String key = (String) iterator.next();
            if (key.startsWith("CHANNEL")) {
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
        if (settingsPanel == null) {
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
        return new ChannelGroup[]{cg};
    }

    @Override
    public Channel[] getAvailableChannels(ChannelGroup cg) {
        if (cg.equals(this.cg)) {
            LOG.info("Returning {} channels", channels.length);
            return this.channels;
        } else {
            return new Channel[]{};
        }
    }

    @Override
    public Channel[] checkForAvailableChannels(ChannelGroup cg, ProgressMonitor pm) {
        if (cg.getId().equals(this.cg.getId())) {
            pm.setMessage(I18N.msg("getting_channels", "Getting channels from VDR..."));
            // load channel list from vdr
            Response res = VDRConnection.send(new LSTC());
            if (res != null && res.getCode() == 250) {
                try {
                    // parse the channel list
                    List<org.hampelratte.svdrp.responses.highlevel.Channel> vdrChannelList = ChannelParser.parse(res.getMessage(), false, true);
                    List<Channel> channelList = new ArrayList<>();
                    for (org.hampelratte.svdrp.responses.highlevel.Channel c : vdrChannelList) {
                        int maxChannel = Integer.parseInt(props.getProperty("max.channel.number"));
                        if (maxChannel == 0 || maxChannel > 0 && c.getChannelNumber() <= maxChannel) {
                            // distinguish between radio and tv channels / pay-tv
                            int category = getChannelCategory(c);

                            String name = c.getName();
                            if ("true".equals(getProperty("add_source_to_channel_name"))) {
                                String source = getChannelSource(c);
                                name += " (" + source + ")";
                            }

                            // create a new tvbrowser channel object
                            Channel chan = new Channel(this, name, Integer.toString(c.getChannelNumber()), TimeZone.getDefault(), "de", "", "", cg, null,
                                    category, name);
                            channelList.add(chan);
                        }
                    }

                    // convert channel list to an array
                    channels = new Channel[channelList.size()];
                    channels = channelList.toArray(channels);

                } catch (NumberFormatException e) {
                    LOG.error("Couldn't parse number", e);
                } catch (ParseException e) {
                    LOG.error("Couldn't parse channel list", e);
                }
            }
            return channels;
        } else {
            return new Channel[]{};
        }
    }

    private String getChannelSource(org.hampelratte.svdrp.responses.highlevel.Channel c) {
        String source = I18N.msg("unknown_source", "unknown source");
        if (c instanceof BroadcastChannel brc) {
            String src = brc.getSource();
            if (src.length() > 0) {
                char fc = src.toUpperCase().charAt(0);
                switch (fc) {
                    case 'C' -> source = "DVB-C";
                    case 'S' -> source = "DVB-S";
                    case 'T' -> source = "DVB-T";
                    case 'V', 'P' -> source = "analog";
                    default -> { /* nothing */ }
                }
            }
        }
        return source;
    }

    private int getChannelCategory(org.hampelratte.svdrp.responses.highlevel.Channel c) {
        if (c instanceof DVBChannel chan) {
            String vpid = chan.getVPID();
            if ("0".equals(vpid) || "1".equals(vpid)) { // a radio station
                return Channel.CATEGORY_RADIO;
            } else { // a tv channel
                boolean payTv = false;
                List<Integer> ca = chan.getConditionalAccess();
                for (Integer caId : ca) {
                    if (caId != 0) {
                        payTv = true;
                        break;
                    }
                }
                if (payTv) {
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
    public ChannelGroup[] checkForAvailableChannelGroups(ProgressMonitor arg0) {
        return getAvailableGroups();
    }

    @Override
    public boolean supportsDynamicChannelGroups() {
        return false;
    }

    public static Version getVersion() {
        return new Version(0, 72);
    }

    @Override
    public void setWorkingDirectory(File dataDir) {
        // don't care
    }
}
