package vdrdataservice;

import org.hampelratte.svdrp.Command;
import org.hampelratte.svdrp.Connection;
import org.hampelratte.svdrp.Response;

import javax.swing.*;


/**
 * @author <a href="hampelratte@users.sf.net>hampelratte@users.sf.net </a>
 */
public class VDRConnection {
    private static final util.i18n.Localizer mLocalizer = util.i18n.Localizer.getLocalizerFor(VDRConnection.class);
    protected static String host;
    protected static int port;
    protected static String charset;

    public static Response send(Command cmd) {
        Response res = null;
        try {
            Connection connection = new Connection(VDRConnection.host, VDRConnection.port, 500, charset);
            res = connection.send(cmd);
            connection.close();
        } catch (Exception e1) {
            JOptionPane.showMessageDialog(null, mLocalizer.msg("couldnt_connect", "Couldn't connect to VDR"));
        }
        return res;
    }
}
