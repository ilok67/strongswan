package org.strongswan.android.v2ray;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;

import java.net.InetSocketAddress;
import java.net.Socket;

public final class V2RayPing
{
	private V2RayPing()
	{
	}

	public static String ping(Context context, String host, int port)
	{
		if (host == null || host.isEmpty() || port <= 0)
		{
			return "timeout";
		}
		Socket socket = new Socket();
		try
		{
			InetSocketAddress address = new InetSocketAddress(host, port);
			Network network = underlying(context);
			if (network != null)
			{
				network.bindSocket(socket);
			}
			long start = SystemClock.elapsedRealtime();
			socket.connect(address, 3000);
			return (SystemClock.elapsedRealtime() - start) + " ms";
		}
		catch (Exception e)
		{
			return "timeout";
		}
		finally
		{
			try
			{
				socket.close();
			}
			catch (Exception ignored)
			{
			}
		}
	}

	@SuppressWarnings("deprecation")
	private static Network underlying(Context context)
	{
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		for (Network n : cm.getAllNetworks())
		{
			NetworkCapabilities nc = cm.getNetworkCapabilities(n);
			if (nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
					&& !nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
			{
				return n;
			}
		}
		return null;
	}
}
