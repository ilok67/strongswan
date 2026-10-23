package org.strongswan.android.v2ray;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;

import io.nekohasekai.libbox.BoxService;
import io.nekohasekai.libbox.Libbox;


public final class V2RayConfigTester
{
    private static final String TAG = "V2RayConfigTester";
    private static final String TEST_URL = "https://www.gstatic.com/generate_204";

    private V2RayConfigTester()
    {
    }

    /**
     * @return "123 ms" یا "timeout"
     */
    public static String ping(Context context, String link)
    {
        int delay = test(context, link, 5000);
        return delay >= 0 ? delay + " ms" : "timeout";
    }

    /**
     * @return میلی‌ثانیه یا -1
     */
    public static int test(Context context, String link, int timeoutMs)
    {
        try
        {
            V2RayLibbox.setup(context);
        }
        catch (Throwable t)
        {
            Log.e(TAG, "setup failed: " + t);
            return -1;
        }

        int port;
        try (ServerSocket ss = new ServerSocket(0, 1,
                InetAddress.getByName("127.0.0.1")))
        {
            port = ss.getLocalPort();
        }
        catch (Exception e)
        {
            Log.e(TAG, "port alloc failed: " + e);
            return -1;
        }

        BoxService box = null;
        try
        {
            String config = buildTestConfig(link, port);
            box = Libbox.newService(config, new NoopPlatform());
            box.start();

            if (!waitForApi(port, 4000))
            {
                return -1;
            }
            return requestDelay(port, "proxy", timeoutMs);
        }
        catch (Throwable t)
        {
            Log.e(TAG, "test failed: " + t);
            return -1;
        }
        finally
        {
            if (box != null)
            {
                try
                {
                    box.close();
                }
                catch (Throwable ignored)
                {
                }
            }
        }
    }

    private static String buildTestConfig(String link, int apiPort) throws Exception
    {
        JSONObject root = new JSONObject();
        root.put("log", new JSONObject().put("level", "error"));

        JSONObject proxy = V2RayConfigBuilder.parseOutboundForEditor(link);
        proxy.put("tag", "proxy");

        root.put("outbounds", new JSONArray()
                .put(proxy)
                .put(new JSONObject().put("type", "direct").put("tag", "direct")));

        root.put("route", new JSONObject().put("final", "proxy"));

        JSONObject exp = new JSONObject();
        exp.put("clash_api", new JSONObject()
                .put("external_controller", "127.0.0.1:" + apiPort));
        root.put("experimental", exp);

        return root.toString();
    }

    private static boolean waitForApi(int port, int timeoutMs)
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline)
        {
            try (Socket s = new Socket())
            {
                s.connect(new InetSocketAddress("127.0.0.1", port), 200);
                return true;
            }
            catch (Exception ignored)
            {
                try
                {
                    Thread.sleep(100);
                }
                catch (InterruptedException e)
                {
                    return false;
                }
            }
        }
        return false;
    }

    private static int requestDelay(int port, String tag, int timeoutMs)
    {
        HttpURLConnection conn = null;
        try
        {
            String u = URLEncoder.encode(TEST_URL, "UTF-8");
            String t = URLEncoder.encode(tag, "UTF-8");
            String api = "http://127.0.0.1:" + port + "/proxies/" + t
                    + "/delay?url=" + u + "&timeout=" + timeoutMs;

            conn = (HttpURLConnection) new URL(api).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(timeoutMs + 2000);

            if (conn.getResponseCode() != 200)
            {
                return -1;
            }

            BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null)
            {
                sb.append(line);
            }
            r.close();

            String body = sb.toString();
            int idx = body.indexOf("\"delay\"");
            if (idx < 0)
            {
                return -1;
            }
            int colon = body.indexOf(':', idx);
            int end = body.indexOf('}', colon);
            if (colon < 0 || end < 0)
            {
                return -1;
            }
            return Integer.parseInt(body.substring(colon + 1, end).trim());
        }
        catch (Exception e)
        {
            return -1;
        }
        finally
        {
            if (conn != null)
            {
                conn.disconnect();
            }
        }
    }

    private static final class NoopPlatform
            implements io.nekohasekai.libbox.PlatformInterface
    {
        public void autoDetectInterfaceControl(int fd)
        {
        }

        public boolean usePlatformAutoDetectInterfaceControl()
        {
            return false;
        }

        public boolean usePlatformDefaultInterfaceMonitor()
        {
            return false;
        }

        public boolean usePlatformInterfaceGetter()
        {
            return false;
        }

        public void clearDNSCache()
        {
        }

        public void writeLog(String m)
        {
            Log.d(TAG, m);
        }

        public boolean useProcFS()
        {
            return false;
        }

        public int findConnectionOwner(int p, String s, int sp, String d, int dp)
                throws Exception
        {
            return -1;
        }

        public void sendNotification(io.nekohasekai.libbox.Notification n)
        {
        }

        public String packageNameByUid(int uid) throws Exception
        {
            return "";
        }

        public int uidByPackageName(String n) throws Exception
        {
            return -1;
        }

        public boolean includeAllNetworks()
        {
            return false;
        }

        public boolean underNetworkExtension()
        {
            return false;
        }

        public io.nekohasekai.libbox.WIFIState readWIFIState()
        {
            return null;
        }

        public io.nekohasekai.libbox.StringIterator systemCertificates()
        {
            return null;
        }

        public void startDefaultInterfaceMonitor(
                io.nekohasekai.libbox.InterfaceUpdateListener l)
        {
        }

        public void closeDefaultInterfaceMonitor(
                io.nekohasekai.libbox.InterfaceUpdateListener l)
        {
        }

        public io.nekohasekai.libbox.NetworkInterfaceIterator getInterfaces()
                throws Exception
        {
            return new io.nekohasekai.libbox.NetworkInterfaceIterator()
            {
                public boolean hasNext()
                {
                    return false;
                }

                public io.nekohasekai.libbox.NetworkInterface next()
                {
                    return null;
                }
            };
        }
    }
}
