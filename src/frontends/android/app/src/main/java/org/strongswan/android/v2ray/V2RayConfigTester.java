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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.nekohasekai.libbox.BoxService;
import io.nekohasekai.libbox.Libbox;


public final class V2RayConfigTester
{
    private static final String TAG = "V2RayConfigTester";
    private static final String TEST_URL = "https://www.gstatic.com/generate_204";
    private static final int CHUNK = 60;

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

public static int test(Context context, String link, int timeoutMs)
{
	synchronized (V2RayLibbox.GATE)
	{
		if (SingBoxVpnService.isRunning())
		{
			return -1;
		}
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
		try (ServerSocket ss = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")))
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
}
    
        
    

    public interface Callback
    {
        void onResult(String link, int delayMs);
    }

    public static void pingAll(Context context, List<String> links, int timeoutMs, int parallel, Callback callback)
    {
        for (int from = 0; from < links.size(); from += CHUNK)
        {
            List<String> chunk = links.subList(from, Math.min(links.size(), from + CHUNK));
            List<String> valid = new ArrayList<>();
            List<JSONObject> outbounds = new ArrayList<>();

            for (String link : chunk)
            {
                try
                {
                    JSONObject ob = V2RayConfigBuilder.parseOutboundForEditor(link);
                    ob.put("tag", "p" + valid.size());
                    outbounds.add(ob);
                    valid.add(link);
                }
                catch (Throwable t)
                {
                    callback.onResult(link, -1);
                }
            }

            if (valid.isEmpty())
            {
                continue;
            }

            if (!runBatch(context, valid, outbounds, timeoutMs, parallel, callback))
            {
                for (String link : valid)
                {
                    callback.onResult(link, test(context, link, timeoutMs));
                }
            }
        }
    }

    private static boolean runBatch(Context context, final List<String> valid, List<JSONObject> outbounds,
                                    final int timeoutMs, int parallel, final Callback callback)
    {
        synchronized (V2RayLibbox.GATE)
        {
            if (SingBoxVpnService.isRunning())
            {
                for (String link : valid)
                {
                    callback.onResult(link, -1);
                }
                return true;
            }
            try
            {
                V2RayLibbox.setup(context);
            }
            catch (Throwable t)
            {
                Log.e(TAG, "setup failed: " + t);
                return false;
            }

            final int port;
            try (ServerSocket ss = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")))
            {
                port = ss.getLocalPort();
            }
            catch (Exception e)
            {
                Log.e(TAG, "port alloc failed: " + e);
                return false;
            }

            BoxService box = null;
            try
            {
                box = Libbox.newService(buildBatchConfig(outbounds, port), new NoopPlatform());
                box.start();
                if (!waitForApi(port, 4000))
                {
                    return false;
                }

                ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(parallel, valid.size())));
                for (int i = 0; i < valid.size(); i++)
                {
                    final String link = valid.get(i);
                    final String tag = "p" + i;
                    pool.execute(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            callback.onResult(link, requestDelay(port, tag, timeoutMs));
                        }
                    });
                }
                pool.shutdown();
                try
                {
                    pool.awaitTermination(10, TimeUnit.MINUTES);
                }
                catch (InterruptedException e)
                {
                    pool.shutdownNow();
                }
                return true;
            }
            catch (Throwable t)
            {
                Log.e(TAG, "batch failed: " + t);
                return false;
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
    }

    private static String buildBatchConfig(List<JSONObject> outbounds, int apiPort) throws Exception
    {
        JSONObject root = new JSONObject();
        root.put("log", new JSONObject().put("level", "error"));

        JSONArray array = new JSONArray();
        for (JSONObject ob : outbounds)
        {
            array.put(ob);
        }
        array.put(new JSONObject().put("type", "direct").put("tag", "direct"));
        root.put("outbounds", array);

        root.put("route", new JSONObject().put("final", "direct"));

        JSONObject exp = new JSONObject();
        exp.put("clash_api", new JSONObject()
                .put("external_controller", "127.0.0.1:" + apiPort));
        root.put("experimental", exp);

        return root.toString();
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
    public int openTun(io.nekohasekai.libbox.TunOptions options) throws Exception
    {
        throw new UnsupportedOperationException("tun not supported in tester");
    }

    public void autoDetectInterfaceControl(int fd) {}
    public boolean usePlatformAutoDetectInterfaceControl() { return false; }
    public boolean usePlatformDefaultInterfaceMonitor() { return false; }
    public boolean usePlatformInterfaceGetter() { return false; }
    public void clearDNSCache() {}
    public void writeLog(String m) { Log.d(TAG, m); }
    public boolean useProcFS() { return false; }
    public int findConnectionOwner(int p, String s, int sp, String d, int dp) throws Exception { return -1; }
    public void sendNotification(io.nekohasekai.libbox.Notification n) {}
    public String packageNameByUid(int uid) throws Exception { return ""; }
    public int uidByPackageName(String n) throws Exception { return -1; }
    public boolean includeAllNetworks() { return false; }
    public boolean underNetworkExtension() { return false; }
    public io.nekohasekai.libbox.WIFIState readWIFIState() { return null; }
    public io.nekohasekai.libbox.StringIterator systemCertificates() { return null; }
    public void startDefaultInterfaceMonitor(io.nekohasekai.libbox.InterfaceUpdateListener l) {}
    public void closeDefaultInterfaceMonitor(io.nekohasekai.libbox.InterfaceUpdateListener l) {}
    public io.nekohasekai.libbox.NetworkInterfaceIterator getInterfaces() throws Exception
    {
        return new io.nekohasekai.libbox.NetworkInterfaceIterator()
        {
            public boolean hasNext() { return false; }
            public io.nekohasekai.libbox.NetworkInterface next() { return null; }
        };
    }
}




}
