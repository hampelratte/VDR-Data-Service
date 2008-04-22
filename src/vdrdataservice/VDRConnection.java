package vdrdataservice;

import javax.swing.JOptionPane;

import org.hampelratte.svdrp.Command;
import org.hampelratte.svdrp.Connection;
import org.hampelratte.svdrp.Response;



/**
 * @author <a href="hampelratte@users.sf.net>hampelratte@users.sf.net </a>
 *  
 */
public class VDRConnection {
  	private static final util.ui.Localizer mLocalizer = util.ui.Localizer
  		.getLocalizerFor(VDRConnection.class);
    private static Connection connection;
    protected static String host;
    protected static int port;
    protected static String charset;

    public static Response send(Command cmd) {
        Response res = null;
        try {
            connection = new Connection(VDRConnection.host, VDRConnection.port, 500, charset);
            res = connection.send(cmd);
            connection.close();
        } catch (Exception e1) {
            JOptionPane.showMessageDialog(null, mLocalizer.msg("couldnt_connect","Couldn't connect to VDR"));
        } 
        return res;
    }
}