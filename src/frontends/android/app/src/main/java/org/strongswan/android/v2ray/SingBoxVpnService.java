package org.strongswan.android.v2ray;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.strongswan.android.R;

import java.io.File;
import java.net.InterfaceAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nekohasekai.libbox.BoxService;
import io.nekohasekai.libbox.ConnectionOwner;
import io.nekohasekai.libbox.InterfaceUpdateListener;
import io.nekohasekai.libbox.Libbox;
import io.nekohasekai.libbox.NetworkInterfaceIterator;
import io.nekohasekai.libbox.PlatformInterface;
import io.nekohasekai.libbox.RoutePrefix;
import io.nekohasekai.libbox.RoutePrefixIterator;
import io.nekohasekai.libbox.StringIterator;
import io.nekohasekai.libbox.TunOptions;
import io.nekohasekai.libbox.WIFIState;

public class SingBoxVpnService extends VpnService implements PlatformInterface
{
	public static final String ACTION_START = "org.strongswan.android.V2RAY_START";
	public static final String ACTION_STOP = "org.strongswan.android.V2RAY_STOP";
	public static final String ACTION_STATE = "org.strongswan.android.V2RAY_STATE";
	public static final String EXTRA_LINK = "link";
	public static final String EXTRA_RUNNING = "running";
	public static final String EXTRA_ERROR = "error";

	private static final String TAG = "SingBoxVpnService";
	private static final String CHANNEL_ID = "v2ray_vpn";
	private static final int NOTIFICATION_ID = 7201;

	private static volatile boolean running;
	private static boolean libboxReady;

	private final Object lock = new Object();
	private BoxService box;
	private ParcelFileDescriptor tun;
	private ConnectivityManager.NetworkCallback networkCallback;

	public static boolean isRunning()
	{
		return running;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		String action = intent != null ? intent.getAction() : null;
		if (ACTION_STOP.equals(action))
		{
			stopBox();
			stopForeground(true);
			stopSelf();
			return START_NOT_STICKY;
		}
		if (ACTION_START.equals(action))
		{
			final String link = intent.getStringExtra(EXTRA_LINK);
			goForeground();
			new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					startBox(link);
				}
			}).start();
			return START_NOT_STICKY;
		}
		stopSelf();
		return START_NOT_STICKY;
	}

	@Override
	public void onRevoke()
	{
		stopBox();
		stopForeground(true);
		stopSelf();
	}

	@Override
	public void onDestroy()
	{
		stopBox();
		super.onDestroy();
	}

	private void goForeground()
	{
		Notification n = buildNotification();
		if (Build.VERSION.SDK_INT >= 34)
		{
			startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
		}
		else
		{
			startForeground(NOTIFICATION_ID, n);
		}
	}

	private Notification buildNotification()
	{
		if (Build.VERSION.SDK_INT >= 26)
		{
			NotificationManager nm = getSystemService(NotificationManager.class);
			nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "V2Ray VPN", NotificationManager.IMPORTANCE_LOW));
		}
		Intent stop = new Intent(this, SingBoxVpnService.class).setAction(ACTION_STOP);
		int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
		PendingIntent stopIntent = PendingIntent.getService(this, 0, stop, piFlags);
		Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
		b.setContentTitle("BardiaVPN");
		b.setContentText("V2Ray");
		b.setSmallIcon(R.drawable.ic_notification);
		b.setOngoing(true);
		b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent);
		return b.build();
	}

	private void startBox(String link)
	{
		try
		{
			synchronized (lock)
			{
				if (box != null)
				{
					return;
				}
				setupLibbox();
				String config = V2RayConfigBuilder.build(link);
				BoxService service = Libbox.newService(config, this);
				service.start();
				box = service;
				running = true;
			}
			broadcastState(true, null);
		}
		catch (Throwable t)
		{
			Log.e(TAG, "start failed", t);
			stopBox();
			broadcastState(false, String.valueOf(t.getMessage()));
			stopForeground(true);
			stopSelf();
		}
	}

	private void setupLibbox() throws Exception
	{
		if (libboxReady)
		{
			return;
		}
		File work = new File(getFilesDir(), "singbox");
		work.mkdirs();
		Libbox.setup(getFilesDir().getPath(), work.getPath(), getCacheDir().getPath(), false);
		libboxReady = true;
	}

	private void stopBox()
	{
		synchronized (lock)
		{
			running = false;
			if (box != null)
			{
				try
				{
					box.close();
				}
				catch (Throwable t)
				{
					Log.e(TAG, "close failed", t);
				}
				box = null;
			}
			if (tun != null)
			{
				try
				{
					tun.close();
				}
				catch (Exception e)
				{
					Log.e(TAG, "tun close failed", e);
				}
				tun = null;
			}
		}
		broadcastState(false, null);
	}

	private void broadcastState(boolean isRunning, String error)
	{
		Intent i = new Intent(ACTION_STATE);
		i.setPackage(getPackageName());
		i.putExtra(EXTRA_RUNNING, isRunning);
		if (error != null)
		{
			i.putExtra(EXTRA_ERROR, error);
		}
		sendBroadcast(i);
	}

	public int openTun(TunOptions options) throws Exception
	{
		Builder builder = new Builder();
		builder.setSession("BardiaVPN");
		builder.setMtu(options.getMTU());

		RoutePrefixIterator addr4 = options.getInet4Address();
		while (addr4.hasNext())
		{
			RoutePrefix p = addr4.next();
			builder.addAddress(p.address(), p.prefix());
		}
		boolean hasV6 = false;
		RoutePrefixIterator addr6 = options.getInet6Address();
		while (addr6.hasNext())
		{
			RoutePrefix p = addr6.next();
			builder.addAddress(p.address(), p.prefix());
			hasV6 = true;
		}
		if (options.getAutoRoute())
		{
			builder.addDnsServer(options.getDNSServerAddress().getValue());
			builder.addRoute("0.0.0.0", 0);
			if (hasV6)
			{
				builder.addRoute("::", 0);
			}
		}
		if (Build.VERSION.SDK_INT >= 29)
		{
			builder.setMetered(false);
		}
		ParcelFileDescriptor pfd = builder.establish();
		if (pfd == null)
		{
			throw new Exception("vpn permission not granted");
		}
		synchronized (lock)
		{
			tun = pfd;
		}
		return pfd.getFd();
	}

	public void autoDetectInterfaceControl(int fd)
	{
		protect(fd);
	}

	public boolean usePlatformAutoDetectInterfaceControl()
	{
		return true;
	}

	public boolean usePlatformDefaultInterfaceMonitor()
	{
		return true;
	}

	public boolean usePlatformInterfaceGetter()
	{
		return true;
	}

	public void clearDNSCache()
	{
	}

	public void writeLog(String message)
	{
		Log.d(TAG, message);
	}

	public boolean useProcFS()
	{
		return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
	}

	public ConnectionOwner findConnectionOwner(int ipProtocol, String sourceAddress, int sourcePort, String destinationAddress, int destinationPort) throws Exception
	{
		throw new Exception("not supported");
	}

	public String packageNameByUid(int uid) throws Exception
	{
		String[] names = getPackageManager().getPackagesForUid(uid);
		if (names == null || names.length == 0)
		{
			throw new Exception("package not found");
		}
		return names[0];
	}

	public int uidByPackageName(String packageName) throws Exception
	{
		return getPackageManager().getPackageUid(packageName, 0);
	}

	public boolean includeAllNetworks()
	{
		return false;
	}

	public boolean underNetworkExtension()
	{
		return false;
	}

	public WIFIState readWIFIState()
	{
		return null;
	}

	public StringIterator systemCertificates()
	{
		return new StringList(new ArrayList<String>());
	}

	public void startDefaultInterfaceMonitor(final InterfaceUpdateListener listener) throws Exception
	{
		final ConnectivityManager cm = getSystemService(ConnectivityManager.class);
		NetworkRequest request = new NetworkRequest.Builder()
				.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
				.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
				.build();
		networkCallback = new ConnectivityManager.NetworkCallback()
		{
			@Override
			public void onAvailable(Network network)
			{
				pushInterface(listener, cm, network);
			}

			@Override
			public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties)
			{
				pushInterface(listener, cm, network);
			}

			@Override
			public void onLost(Network network)
			{
				listener.updateDefaultInterface("", -1);
			}
		};
		if (Build.VERSION.SDK_INT >= 31)
		{
			cm.registerBestMatchingNetworkCallback(request, networkCallback, null);
		}
		else
		{
			cm.requestNetwork(request, networkCallback);
		}
	}

	public void closeDefaultInterfaceMonitor(InterfaceUpdateListener listener) throws Exception
	{
		if (networkCallback != null)
		{
			ConnectivityManager cm = getSystemService(ConnectivityManager.class);
			cm.unregisterNetworkCallback(networkCallback);
			networkCallback = null;
		}
	}

	private void pushInterface(InterfaceUpdateListener listener, ConnectivityManager cm, Network network)
	{
		try
		{
			LinkProperties lp = cm.getLinkProperties(network);
			if (lp == null || lp.getInterfaceName() == null)
			{
				return;
			}
			String name = lp.getInterfaceName();
			int index = java.net.NetworkInterface.getByName(name).getIndex();
			listener.updateDefaultInterface(name, index);
		}
		catch (Exception e)
		{
			Log.e(TAG, "interface update failed", e);
		}
	}

	public NetworkInterfaceIterator getInterfaces() throws Exception
	{
		List<io.nekohasekai.libbox.NetworkInterface> items = new ArrayList<io.nekohasekai.libbox.NetworkInterface>();
		for (java.net.NetworkInterface ni : Collections.list(java.net.NetworkInterface.getNetworkInterfaces()))
		{
			io.nekohasekai.libbox.NetworkInterface item = new io.nekohasekai.libbox.NetworkInterface();
			item.setName(ni.getName());
			item.setIndex(ni.getIndex());
			item.setMTU(ni.getMTU());
			List<String> addresses = new ArrayList<String>();
			for (InterfaceAddress a : ni.getInterfaceAddresses())
			{
				String host = a.getAddress().getHostAddress();
				int scope = host.indexOf('%');
				if (scope >= 0)
				{
					host = host.substring(0, scope);
				}
				addresses.add(host + "/" + a.getNetworkPrefixLength());
			}
			item.setAddresses(new StringList(addresses));
			int f = 0;
			if (ni.isUp())
			{
				f |= 1;
			}
			if (ni.isLoopback())
			{
				f |= 4;
			}
			if (ni.isPointToPoint())
			{
				f |= 8;
			}
			if (ni.supportsMulticast())
			{
				f |= 16;
			}
			item.setFlags(f);
			items.add(item);
		}
		return new InterfaceList(items);
	}

	private static final class StringList implements StringIterator
	{
		private final List<String> items;
		private int index;

		StringList(List<String> items)
		{
			this.items = items;
		}

		public int len()
		{
			return items.size();
		}

		public boolean hasNext()
		{
			return index < items.size();
		}

		public String next()
		{
			return items.get(index++);
		}
	}

	private static final class InterfaceList implements NetworkInterfaceIterator
	{
		private final List<io.nekohasekai.libbox.NetworkInterface> items;
		private int index;

		InterfaceList(List<io.nekohasekai.libbox.NetworkInterface> items)
		{
			this.items = items;
		}

		public boolean hasNext()
		{
			return index < items.size();
		}

		public io.nekohasekai.libbox.NetworkInterface next()
		{
			return items.get(index++);
		}
	}
}
