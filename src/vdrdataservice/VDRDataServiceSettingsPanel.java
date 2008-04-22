package vdrdataservice;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import tvdataservice.SettingsPanel;
import util.ui.Localizer;


/**
* This code was edited or generated using CloudGarden's Jigloo
* SWT/Swing GUI Builder, which is free for non-commercial
* use. If Jigloo is being used commercially (ie, by a corporation,
* company or business for any purpose whatever) then you
* should purchase a license for each developer using Jigloo.
* Please visit www.cloudgarden.com for details.
* Use of Jigloo implies acceptance of these licensing terms.
* A COMMERCIAL LICENSE HAS NOT BEEN PURCHASED FOR
* THIS MACHINE, SO JIGLOO OR THIS CODE CANNOT BE USED
* LEGALLY FOR ANY CORPORATE OR COMMERCIAL PURPOSE.
*/
public class VDRDataServiceSettingsPanel extends SettingsPanel {

    private static final long serialVersionUID = -3226740110392185858L;

    private static final Localizer mLocalizer = Localizer
      .getLocalizerFor(VDRDataServiceSettingsPanel.class);

    VDRDataService vdrDataService;

	private JLabel lHost = new JLabel(mLocalizer.msg("host", "Host"));

	private JTextField host;

	private JLabel lPort = new JLabel(mLocalizer.msg("port", "Port"));

	private JTextField port;
	private JPanel panel;
    private JComboBox comboCharset;
    private JLabel lCharset;
	private JSpinner spinMaxChannel;
	private JLabel lMaxChannel;

	public VDRDataServiceSettingsPanel() {
		initGUI();
	}
  
    protected void loadSettings() {
    	host.setText(vdrDataService.getProperty("vdr.host"));
    	port.setText(vdrDataService.getProperty("vdr.port"));
    	int maxChan = Integer.parseInt(vdrDataService.getProperty("max.channel.number"));
    	String charset = vdrDataService.getProperty("charset");
    	if(charset == null) {
    	    comboCharset.setSelectedIndex(0);
    	} else {
    	    comboCharset.setSelectedItem(charset);
    	}
    	spinMaxChannel.setValue(maxChan);
    }

  private void initGUI() {
		panel = new JPanel();

		host = new JTextField(20);
		port = new JTextField(20);

		GridBagLayout thisLayout = new GridBagLayout();
		panel.setLayout(thisLayout);
		thisLayout.rowWeights = new double[] {0.1, 0.1, 0.1, 0.1};
		thisLayout.rowHeights = new int[] {7, 7, 7, 7};
		thisLayout.columnWeights = new double[] { 0.1, 0.1 };
		thisLayout.columnWidths = new int[] { 7, 7 };
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.anchor = GridBagConstraints.NORTHWEST;

		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(lHost, gbc);

		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(host, gbc);

		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(lPort, gbc);

		gbc.gridx = 1;
		gbc.gridy = 1;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(port, gbc);
		{
			lMaxChannel = new JLabel(mLocalizer.msg("maxChannel",
					"Limit Channel"));
			panel.add(lMaxChannel, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0,
					GridBagConstraints.WEST, GridBagConstraints.NONE,
					new Insets(5, 5, 5, 5), 0, 0));

		}
		{
			SpinnerNumberModel spinMaxChannelModel = new SpinnerNumberModel(0,0, 10000, 1);
			spinMaxChannel = new JSpinner();
			panel.add(spinMaxChannel, new GridBagConstraints(1, 2, 1, 1, 0.0,
					0.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
					new Insets(5, 5, 5, 5), 0, 0));
			spinMaxChannel.setModel(spinMaxChannelModel);
		}
		{
		    lCharset = new JLabel();
		    panel.add(lCharset, new GridBagConstraints(0, 3, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));
		    lCharset.setText(mLocalizer.msg("charset", "Charset"));
		}
		{
		    ComboBoxModel comboCharsetModel = 
		        new DefaultComboBoxModel(
		                new String[] { "ISO-8859-1", "ISO-8859-15", "UTF-8" });
		    comboCharset = new JComboBox();
		    panel.add(comboCharset, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));
		    comboCharset.setModel(comboCharsetModel);
		}

		setLayout(new FlowLayout());
        this.setPreferredSize(new java.awt.Dimension(591, 138));
		add(panel);
	}

	public void ok() {
		String h = host.getText();
		int p = Integer.parseInt(port.getText());
		String charset = comboCharset.getSelectedItem().toString();
		VDRConnection.host = h;
		VDRConnection.port = p;
		VDRConnection.charset = charset;
		
		vdrDataService.setProperty("vdr.host", h);
		vdrDataService.setProperty("vdr.port", port.getText());
		vdrDataService.setProperty("charset", charset);
		vdrDataService.setProperty("max.channel.number", spinMaxChannel.getValue().toString());
		
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