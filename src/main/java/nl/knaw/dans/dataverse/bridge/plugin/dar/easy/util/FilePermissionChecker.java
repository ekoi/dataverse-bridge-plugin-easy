package nl.knaw.dans.dataverse.bridge.plugin.dar.easy.util;

import nl.knaw.dans.dataverse.bridge.plugin.exception.BridgeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/*
 * @author: Eko Indarto
 */
public class FilePermissionChecker {
    private static final Logger LOG = LoggerFactory.getLogger(FilePermissionChecker.class);

    public static PermissionStatus check(String url) throws BridgeException {
        URL validUrl;
        try {
            validUrl = new URL(url);
            HttpURLConnection huc = (HttpURLConnection) validUrl.openConnection();
            int rc = huc.getResponseCode();
            if (rc == HttpURLConnection.HTTP_OK)
                return PermissionStatus.PUBLIC;
            else if (rc == HttpURLConnection.HTTP_FORBIDDEN)
                return PermissionStatus.RESTRICTED;
            else {
                LOG.error(url + " response gives status other then 200. Response code: " + rc);
                throw new BridgeException(url + " response gives status other then 200. Response code: " + rc, FilePermissionChecker.class);
            }
        } catch (MalformedURLException e) {
            String errMsg = "MalformedURLException, message: " + e.getMessage();
            LOG.error(errMsg);
            throw new BridgeException(errMsg, e, FilePermissionChecker.class);
        } catch (IOException e) {
            String errMsg = "IOException, message: " + e.getMessage();
            LOG.error(errMsg);
            throw new BridgeException(errMsg, e, FilePermissionChecker.class);
        }
    }

    public enum PermissionStatus {
       PUBLIC, RESTRICTED;

        public String toString() {
            switch (this) {
                case PUBLIC:
                    return "PUBLIC";
                case RESTRICTED:
                    return "RESTRICTED";
            }
            return "OTHER";//default
        }

    }

}
