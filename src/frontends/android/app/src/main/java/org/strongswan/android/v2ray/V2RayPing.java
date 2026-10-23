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
