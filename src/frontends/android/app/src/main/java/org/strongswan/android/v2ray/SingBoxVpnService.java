package org.strongswan.android.v2ray;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.util.Log;

import org.strongswan.android.R;
import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.logic.VpnStateService;
import org.strongswan.android.logic.VpnStateService.ErrorState;
import org.strongswan.android.logic.VpnStateService.State;
import org.strongswan.android.ui.MainActivity;
import org.strongswan.android.utils.TrafficMonitor;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.net.InterfaceAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.nekohasekai.libbox.BoxService;
import io.nekohasekai.libbox.InterfaceUpdateListener;
import io.nekohasekai.libbox.Libbox;
import io.nekohasekai.libbox.NetworkInterfaceIterator;
import io.nekohasekai.libbox.PlatformInterface;
import io.nekohasekai.libbox.RoutePrefix;
import io.nekohasekai.libbox.RoutePrefixIterator;
import io.nekohasekai.libbox.SetupOptions;
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
	private static volatile String sLink;
	private static boolean libboxReady;
	private static final java.util.ArrayDeque<String> logLines = new java.util.ArrayDeque<String>();

	private final Object lock = new Object();
	private final Object stateLock = new Object();
	private final TrafficMonitor traffic = new TrafficMonitor();
	private Handler handler;
	private VpnStateService stateService;
	private BoxService box;
	private ParcelFileDescriptor tun;
	private ConnectivityManager.NetworkCallback networkCallback;
	private long connectedAt;

	private final ServiceConnection stateConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder binder)
		{
			synchronized (stateLock)
			{
				stateService = ((VpnStateService.LocalBinder) binder).getService();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			synchronized (stateLock)
			{
				stateService = null;
			}
		}
	};

	private final Runnable tick = new Runnable()
	{
		@Override
		public void run()
		{
			if (!running)
			{
				return;
			}
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			if (pm == null || pm.isInteractive())
			{
				traffic.update();
				updateNotification();
			}
			handler.postDelayed(this, 1000);
		}
	};

	public static boolean isRunning()
	{
		return running;
	}

	public static boolean hasSession()
	{
		return sLink != null;
	}

	public static String getLink()
	{
		return sLink;
	}

	public static void start(Context context, String link)
	{
		Intent i = new Intent(context, SingBoxVpnService.class);
		i.setAction(ACTION_START);
		i.putExtra(EXTRA_LINK, link);
		ContextCompat.startForegroundService(context, i);
	}

	public static void stop(Context context)
	{
		if (!running && sLink == null)
		{
			return;
		}
		sLink = null;
		Intent i = new Intent(context, SingBoxVpnService.class);
		i.setAction(ACTION_STOP);
		context.startService(i);
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
		handler = new Handler(Looper.getMainLooper());
		createNotificationChannel();
		bindService(new Intent(this, VpnStateService.class), stateConnection, Service.BIND_AUTO_CREATE);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		String action = intent != null ? intent.getAction() : null;
		if (ACTION_STOP.equals(action))
		{
			sLink = null;
			shutdown();
			return START_NOT_STICKY;
		}
		if (ACTION_START.equals(action))
		{
			final String link = intent.getStringExtra(EXTRA_LINK);
			sLink = link;
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
		sLink = null;
		shutdown();
	}

	@Override
	public void onDestroy()
	{
		synchronized (lock)
		{
			closeBox();
		}
		try
		{
			unbindService(stateConnection);
		}
		catch (Exception e)
		{
			Log.e(TAG, "unbind failed", e);
		}
		super.onDestroy();
	}

	private void shutdown()
	{
		synchronized (lock)
		{
			closeBox();
		}
		setState(State.DISABLED);
		broadcastState(false, null);
		stopForeground(true);
		stopSelf();
	}

	private void startBox(String link)
	{
		try
		{
			awaitStateService();
			dropIkev2();
			synchronized (lock)
			{
				closeBox();
				startConnection(link);
				setupLibbox();
				String config = V2RayConfigBuilder.build(link, Libbox.version());
				BoxService service = Libbox.newService(config, this);
				service.start();
				box = service;
				running = true;
			}
			connectedAt = System.currentTimeMillis();
			traffic.reset();
			traffic.update();
			setState(State.CONNECTED);
			updateNotification();
			handler.post(tick);
			broadcastState(true, null);
		}
		catch (Throwable t)
		{
			Log.e(TAG, "start failed", t);
			synchronized (lock)
			{
				closeBox();
			}
			String version;
			try
			{
				version = " [libbox " + Libbox.version() + "]";
			}
			catch (Throwable ignored)
			{
				version = "";
			}
			setError(ErrorState.GENERIC_ERROR);
			broadcastState(false, t.getMessage() + version);
			stopForeground(true);
			stopSelf();
		}
	}

	private void awaitStateService() throws InterruptedException
	{
		for (int i = 0; i < 40; i++)
		{
			synchronized (stateLock)
			{
				if (stateService != null)
				{
					return;
				}
			}
			Thread.sleep(50);
		}
	}

	private void dropIkev2() throws InterruptedException
	{
		synchronized (stateLock)
		{
			if (stateService == null || running || stateService.getState() == State.DISABLED)
			{
				return;
			}
			stateService.disconnect();
		}
		for (int i = 0; i < 100; i++)
		{
			synchronized (stateLock)
			{
				if (stateService == null || stateService.getState() == State.DISABLED)
				{
					return;
				}
			}
			Thread.sleep(50);
		}
	}

	private void startConnection(String link)
	{
		synchronized (stateLock)
		{
			if (stateService != null)
			{
				VpnProfile profile = new VpnProfile();
				profile.setName(V2RayConfigBuilder.name(link));
				stateService.startConnection(profile);
			}
		}
	}

	private void setState(State state)
	{
		synchronized (stateLock)
		{
			if (stateService != null)
			{
				stateService.setState(state);
			}
		}
	}

	private void setError(ErrorState error)
	{
		synchronized (stateLock)
		{
			if (stateService != null)
			{
				stateService.setError(error);
			}
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
		SetupOptions options = new SetupOptions();
		options.setBasePath(getFilesDir().getPath());
		options.setWorkingPath(work.getPath());
		options.setTempPath(getCacheDir().getPath());
		Libbox.setup(options);
		Log.i(TAG, "libbox " + Libbox.version());
		libboxReady = true;
	}

	private void closeBox()
	{
		running = false;
		handler.removeCallbacks(tick);
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

	private void createNotificationChannel()
	{
		if (Build.VERSION.SDK_INT >= 26)
		{
			NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "V2Ray VPN", NotificationManager.IMPORTANCE_LOW);
			channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
			channel.setShowBadge(false);
			getSystemService(NotificationManager.class).createNotificationChannel(channel);
		}
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

	private void updateNotification()
	{
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, buildNotification());
	}

	private Notification buildNotification()
	{
		String link = sLink;
		String name = V2RayConfigBuilder.name(link == null ? "" : link);
		int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);

		Intent stop = new Intent(this, SingBoxVpnService.class).setAction(ACTION_STOP);
		PendingIntent stopIntent = PendingIntent.getService(this, 0, stop, piFlags);
		PendingIntent openIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), piFlags);

		Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
		b.setSmallIcon(R.drawable.ic_notification);
		b.setCategory(Notification.CATEGORY_SERVICE);
		b.setOngoing(true);
		b.setOnlyAlertOnce(true);
		b.setContentIntent(openIntent);
		b.addAction(R.drawable.ic_notification_disconnect, getString(R.string.disconnect), stopIntent);
		if (running)
		{
			b.setContentTitle(getString(R.string.state_connected));
			b.setContentText(traffic.getLine());
			b.setSubText(name);
			b.setColor(ContextCompat.getColor(this, R.color.success_text));
			b.setUsesChronometer(true);
			if (connectedAt > 0)
			{
				b.setWhen(connectedAt);
			}
		}
		else
		{
			b.setContentTitle(getString(R.string.state_connecting));
			b.setContentText(name);
			b.setColor(ContextCompat.getColor(this, R.color.warning_text));
		}
		return b.build();
	}

	public int openTun(TunOptions options) throws Exception
	{
		Builder builder = new Builder();
		builder.setSession(V2RayConfigBuilder.name(sLink == null ? "" : sLink));
		builder.setMtu(options.getMTU());
		builder.setConfigureIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
				PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)));

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
		applyAppFilter(builder);
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

	private void applyAppFilter(Builder builder)
	{
		SharedPreferences filter = getSharedPreferences("app_filter", MODE_PRIVATE);
		String mode = filter.getString("mode", "off");
		Set<String> packages = filter.getStringSet("packages", Collections.<String>emptySet());
		if (packages == null)
		{
			packages = Collections.<String>emptySet();
		}
		String self = getPackageName();
		if ("only".equals(mode) && !packages.isEmpty())
		{
			for (String app : packages)
			{
				if (app.equals(self))
				{
					continue;
				}
				try
				{
					builder.addAllowedApplication(app);
				}
				catch (PackageManager.NameNotFoundException e)
				{
					Log.w(TAG, "package not found: " + app);
				}
			}
			try
			{
				builder.addAllowedApplication(self);
			}
			catch (PackageManager.NameNotFoundException e)
			{
				Log.w(TAG, "own package not found");
			}
			return;
		}
		if ("exclude".equals(mode))
		{
			for (String app : packages)
			{
				if (app.equals(self))
				{
					continue;
				}
				try
				{
					builder.addDisallowedApplication(app);
				}
				catch (PackageManager.NameNotFoundException e)
				{
					Log.w(TAG, "package not found: " + app);
				}
			}
		}
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
		synchronized (logLines)
		{
			logLines.addLast(message);
			while (logLines.size() > 200)
			{
				logLines.removeFirst();
			}
		}
	}

	public static String recentLog()
	{
		StringBuilder sb = new StringBuilder();
		synchronized (logLines)
		{
			for (String line : logLines)
			{
				sb.append(line).append('\n');
			}
		}
		return sb.toString();
	}

	public boolean useProcFS()
	{
		return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
	}

	public int findConnectionOwner(int ipProtocol, String sourceAddress, int sourcePort, String destinationAddress, int destinationPort) throws Exception
	{
		throw new Exception("not supported");
	}

	public void sendNotification(io.nekohasekai.libbox.Notification notification) throws Exception
	{
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
		return getPackageManager().getApplicationInfo(packageName, 0).uid;
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
		final ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
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
			cm.registerBestMatchingNetworkCallback(request, networkCallback, new Handler(Looper.getMainLooper()));
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
			ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
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
