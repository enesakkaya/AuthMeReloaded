package fr.xephi.authme.util;

import com.maxmind.geoip.LookupService;
import fr.xephi.authme.AuthMe;
import fr.xephi.authme.ConsoleLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import org.bukkit.plugin.java.JavaPlugin;

public class GeoLiteAPI {
    private static final String LICENSE =
        "[LICENSE] This product uses data from the GeoLite API created by MaxMind, available at http://www.maxmind.com";
    private static final String GEOIP_URL =
        "http://geolite.maxmind.com/download/geoip/database/GeoLiteCountry/GeoIP.dat.gz";
    private static LookupService lookupService;
    private static Thread downloadTask;

    private GeoLiteAPI() {
    }

    /**
     * Download (if absent or old) the GeoIpLite data file and then try to load it.
     *
     * @return True if the data is available, false otherwise.
     */
    public synchronized static boolean isDataAvailable(JavaPlugin plugin) {
        if (downloadTask != null && downloadTask.isAlive()) {
            return false;
        }
        if (lookupService != null) {
            return true;
        }
        final File pluginFolder = plugin.getDataFolder();
        final File data = new File(pluginFolder, "GeoIP.dat");
        if (data.exists()) {
            boolean dataIsOld = (System.currentTimeMillis() - data.lastModified()) > TimeUnit.DAYS.toMillis(30);
            if (!dataIsOld) {
                try {
                    lookupService = new LookupService(data);
                    ConsoleLogger.info(LICENSE);
                    return true;
                } catch (IOException e) {
                    ConsoleLogger.logException("Failed to load GeoLiteAPI database", e);
                    return false;
                }
            } else {
                if (!data.delete()) {
                    ConsoleLogger.showError("Failed to delete GeoLiteAPI database");
                }
            }
        }
        // Ok, let's try to download the data file!
        downloadTask = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL downloadUrl = new URL(GEOIP_URL);
                    URLConnection conn = downloadUrl.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.connect();
                    InputStream input = conn.getInputStream();
                    if (conn.getURL().toString().endsWith(".gz")) {
                        input = new GZIPInputStream(input);
                    }
                    OutputStream output = new FileOutputStream(data);
                    byte[] buffer = new byte[2048];
                    int length = input.read(buffer);
                    while (length >= 0) {
                        output.write(buffer, 0, length);
                        length = input.read(buffer);
                    }
                    output.close();
                    input.close();
                } catch (IOException e) {
                    ConsoleLogger.logException("Could not download GeoLiteAPI database", e);
                }
            }
        });
        downloadTask.start();
        return false;
    }

    /**
     * Get the country code of the given IP address.
     *
     * @param ip textual IP address to lookup.
     *
     * @return two-character ISO 3166-1 alpha code for the country.
     */
    public static String getCountryCode(JavaPlugin plugin, String ip) {
        if (!"127.0.0.1".equals(ip) && isDataAvailable(plugin)) {
            return lookupService.getCountry(ip).getCode();
        }
        return "--";
    }

    /**
     * Get the country name of the given IP address.
     *
     * @param ip textual IP address to lookup.
     *
     * @return The name of the country.
     */
    public static String getCountryName(JavaPlugin plugin, String ip) {
        if (!"127.0.0.1".equals(ip) && isDataAvailable(plugin)) {
            return lookupService.getCountry(ip).getName();
        }
        return "N/A";
    }

}
