package vdrdataservice;

import tvdataservice.SettingsPanel;
import util.i18n.Localizer;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;

public class VDRDataServiceSettingsPanel extends SettingsPanel {

    @Serial
    private static final long serialVersionUID = -3226740110392185858L;
    private static final String CHARSET = "charset";

    private static final Localizer mLocalizer = Localizer.getLocalizerFor(VDRDataServiceSettingsPanel.class);

    transient VDRDataService vdrDataService;

    private final JLabel lHost = new JLabel(mLocalizer.msg("host", "Host"));
    private JTextField host;

    private final JLabel lPort = new JLabel(mLocalizer.msg("port", "Port"));
    private JTextField port;

    private final JLabel lCharset = new JLabel(mLocalizer.msg(CHARSET, "Charset"));
    private JComboBox<String> comboCharset;

    private final JLabel lMaxChannel = new JLabel(mLocalizer.msg("maxChannel", "Limit Channel"));
    private JSpinner spinMaxChannel;

    private final JLabel lSourceInChannelName = new JLabel(mLocalizer.msg("add_channel_source_to_name", "Add the source of a channel to its name"));
    private final String checkSourceInChannelNameTooltip = mLocalizer.msg("add_channel_source_to_name_tooltip",
            "The source (e.g. DVB-S) will be added to the channel name. E.g. \"ZDF (DVB-S)\"");
    private final String sourceInChannelNameHintApplyChange = mLocalizer.msg("channel_source_to_name_apply_hint",
            "To apply this setting, you have to refresh the channel list.");
    private JCheckBox checkSourceInChannelName;

    public VDRDataServiceSettingsPanel() {
        initGUI();
    }

    protected void loadSettings() {
        host.setText(vdrDataService.getProperty("vdr.host"));
        port.setText(vdrDataService.getProperty("vdr.port"));
        int maxChan = Integer.parseInt(vdrDataService.getProperty("max.channel.number"));
        String charset = vdrDataService.getProperty(CHARSET);
        if (charset == null) {
            comboCharset.setSelectedIndex(0);
        } else {
            comboCharset.setSelectedItem(charset);
        }
        spinMaxChannel.setValue(maxChan);
        checkSourceInChannelName.setSelected(Boolean.parseBoolean(vdrDataService.getProperty("add_source_to_channel_name")));
    }

    private void initGUI() {
        JPanel panel = new JPanel();

        host = new JTextField(20);
        port = new JTextField(20);

        GridBagLayout thisLayout = new GridBagLayout();
        panel.setLayout(thisLayout);
        thisLayout.rowWeights = new double[]{0.1, 0.1, 0.1, 0.1};
        thisLayout.rowHeights = new int[]{7, 7, 7, 7};
        thisLayout.columnWeights = new double[]{0.1, 0.1};
        thisLayout.columnWidths = new int[]{7, 7};
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = 1;
        gbc.gridheight = 1;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(lHost, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(host, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(lPort, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(port, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(lMaxChannel, gbc);

        SpinnerNumberModel spinMaxChannelModel = new SpinnerNumberModel(0, 0, 10000, 1);
        spinMaxChannel = new JSpinner(spinMaxChannelModel);
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(spinMaxChannel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(lCharset, gbc);

        ComboBoxModel<String> comboCharsetModel = new DefaultComboBoxModel<>(new String[]{"ISO-8859-1", "ISO-8859-15", "UTF-8"});
        comboCharset = new JComboBox<>(comboCharsetModel);
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(comboCharset, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(lSourceInChannelName, gbc);

        checkSourceInChannelName = new JCheckBox();
        checkSourceInChannelName.setToolTipText(checkSourceInChannelNameTooltip);
        checkSourceInChannelName.addActionListener(e -> JOptionPane.showMessageDialog(VDRDataServiceSettingsPanel.this, sourceInChannelNameHintApplyChange));
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(checkSourceInChannelName, gbc);

        setLayout(new FlowLayout());
        this.setPreferredSize(new java.awt.Dimension(591, 138));
        add(panel);
    }

    @Override
    public void ok() {
        String h = host.getText();
        int p = Integer.parseInt(port.getText());
        String charset = comboCharset.getSelectedItem().toString();
        VDRConnection.host = h;
        VDRConnection.port = p;
        VDRConnection.charset = charset;

        vdrDataService.setProperty("vdr.host", h);
        vdrDataService.setProperty("vdr.port", port.getText());
        vdrDataService.setProperty(CHARSET, charset);
        vdrDataService.setProperty("max.channel.number", spinMaxChannel.getValue().toString());
        vdrDataService.setProperty("add_source_to_channel_name", Boolean.toString(checkSourceInChannelName.isSelected()));
    }

    /**
     * This method should return an instance of this class which does
     * NOT initialize it's GUI elements. This method is ONLY required by
     * Jigloo if the superclass of this class is abstract or non-public. It
     * is not needed in any other situation.
     */
    public static Object getGUIBuilderInstance() {
        return new VDRDataServiceSettingsPanel(Boolean.FALSE);
    }

    /**
     * This constructor is used by the getGUIBuilderInstance method to
     * provide an instance of this class which has not had it's GUI elements
     * initialized (ie, initGUI is not called in this constructor).
     */
    public VDRDataServiceSettingsPanel(Boolean initGUI) {
        super();
    }

    public VDRDataService getVdrDataService() {
        return vdrDataService;
    }

    public void setVdrDataService(VDRDataService plugin) {
        this.vdrDataService = plugin;
    }
}
