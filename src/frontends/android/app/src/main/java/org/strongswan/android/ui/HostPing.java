package org.strongswan.android.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HostPing
{
	private static final Pattern TIME = Pattern.compile("time[=<]([0-9.]+)");

	private HostPing() {}

	public static String ping(String host, Integer port)
	{
		if (host == null || host.isEmpty())
		{
			return "timeout";
		}
		try
		{
			InetAddress addr = InetAddress.getByName(host);
			String ip = addr.getHostAddress();
			String icmp = pingIcmp(ip);
			if (icmp != null)
			{
				return icmp;
			}
			if (port != null && port > 0)
			{
				long t0 = System.currentTimeMillis();
				try (Socket socket = new Socket())
				{
					socket.connect(new InetSocketAddress(addr, port), 2000);
					return (System.currentTimeMillis() - t0) + " ms";
				}
			}
		}
		catch (Exception ignored)
		{
		}
		return "timeout";
	}

	private static String pingIcmp(String ip)
	{
		Process process = null;
		try
		{
			process = new ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "2", ip)
				.redirectErrorStream(true)
				.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			String result = null;
			while ((line = reader.readLine()) != null)
			{
				Matcher m = TIME.matcher(line);
				if (m.find())
				{
					result = Math.round(Double.parseDouble(m.group(1))) + " ms";
				}
			}
			process.waitFor();
			return result;
		}
		catch (Exception e)
		{
			return null;
		}
		finally
		{
			if (process != null)
			{
				process.destroy();
			}
		}
	}
}
