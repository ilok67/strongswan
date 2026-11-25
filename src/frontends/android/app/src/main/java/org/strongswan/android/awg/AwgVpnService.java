package org.strongswan.android.awg;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.amnezia.awg.GoBackend;
import org.json.JSONObject;
import org.strongswan.android.R;
import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.logic.VpnStateService;
import org.strongswan.android.logic.VpnStateService.ErrorState;
import org.strongswan.android.logic.VpnStateService.State;
import org.strongswan.android.ui.MainActivity;
import org.strongswan.android.utils.TrafficMonitor;
import org.strongswan.android.v2ray.SingBoxVpnService;

import java.net.Inet6Address;
import java.net.InetAddress;

public class AwgVpnService extends VpnService
{
	public static final String ACTION_START = "org.strongswan.android.AWG_START";
	public static final String ACTION_STOP = "org.strongswan.android.AWG_STOP";
	public static final String ACTION_STATE = "org.strongswan.android.AWG_STATE";
	public static final String EXTRA_PROFILE = "profile";
	public static final String EXTRA_RUNNING = "running";
	public static final String EXTRA_ERROR = "error";

	private static final String TAG = "AwgVpnService";
	private static final String CHANNEL_ID = "awg_vpn";
	private static final int NOTIFICATION_ID = 7202;

	private static volatile boolean running;
	private static volatile String sId;
	private static volatile String sName = "";

	private final Object lock = new Object();
	private final Object stateLock = new Object();
	private final TrafficMonitor traffic = new TrafficMonitor();
	private Handler handler;
	private VpnStateService stateService;
	private int handle = -1;
	private long connectedAt;
	private volatile int generation;

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

	public static boolean isActive()
	{
		return sId != null;
	}

	public static boolean isActive(String id)
	{
		return sId != null && sId.equals(id);
	}

	public static void start(Context context, JSONObject profile)
	{
		Intent i = new Intent(context, AwgVpnService.class);
		i.setAction(ACTION_START);
		i.putExtra(EXTRA_PROFILE, profile.toString());
		ContextCompat.startForegroundService(context, i);
	}

	public static void stop(Context context)
	{
		if (!running && sId == null)
		{
			return;
		}
		sId = null;
		Intent i = new Intent(context, AwgVpnService.class);
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
			sId = null;
			shutdown();
			return START_NOT_STICKY;
		}
		if (ACTION_START.equals(action))
		{
			final String json = intent.getStringExtra(EXTRA_PROFILE);
			try
			{
				JSONObject p = new JSONObject(json);
				sId = p.optString("id", "awg");
				sName = p.optString("name", "AmneziaWG");
			}
			catch (Exception e)
			{
				sId = null;
				stopSelf();
				return START_NOT_STICKY;
			}
			final int gen = ++generation;
			goForeground();
			new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					startTunnel(json, gen);
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
		sId = null;
		shutdown();
	}

	@Override
	public void onDestroy()
	{
		synchronized (lock)
		{
			closeTunnel();
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
		generation++;
		synchronized (lock)
		{
			closeTunnel();
		}
		setState(State.DISABLED);
		broadcastState(false, null);
		stopForeground(true);
		stopSelf();
	}

	private void startTunnel(String json, int gen)
	{
		try
		{
			awaitStateService();
			SingBoxVpnService.stop(this);
			for (int i = 0; i < 60 && SingBoxVpnService.isRunning(); i++)
			{
				Thread.sleep(50);
			}
			dropIkev2();
			JSONObject p = new JSONObject(json);
			String name = p.optString("name", "AmneziaWG");
			InetAddress endpoint = InetAddress.getByName(p.getString("host"));
			String endpointIp = endpoint instanceof Inet6Address ? stripScope(endpoint.getHostAddress()) : endpoint.getHostAddress();
			String uapi = AwgConfig.toUapi(p, endpointIp);
			synchronized (lock)
			{
				if (gen != generation)
				{
					return;
				}
				closeTunnel();
				startConnection(name);
				Builder builder = new Builder();
				configureBuilder(builder, p, name);
				ParcelFileDescriptor pfd = builder.establish();
				if (pfd == null)
				{
					throw new IllegalStateException("VPN permission not granted");
				}
				int fd = pfd.detachFd();
				Log.i(TAG, "awg-go " + GoBackend.awgVersion());
				handle = GoBackend.awgTurnOn("awg0", fd, uapi);
				if (handle < 0)
				{
					handle = -1;
					throw new IllegalStateException("tunnel creation failed");
				}
				if (!protect(GoBackend.awgGetSocketV4(handle)) || !protect(GoBackend.awgGetSocketV6(handle)))
				{
					throw new IllegalStateException("could not protect tunnel sockets");
				}
			}
			boolean handshake = false;
			for (int i = 0; i < 40 && !handshake; i++)
			{
				Thread.sleep(500);
				synchronized (lock)
				{
					if (gen != generation || handle == -1)
					{
						return;
					}
					handshake = hasHandshake(GoBackend.awgGetConfig(handle));
				}
			}
			if (!handshake)
			{
				throw new IllegalStateException("no handshake from server");
			}
			running = true;
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
			if (gen != generation)
			{
				return;
			}
			synchronized (lock)
			{
				closeTunnel();
			}
			sId = null;
			setError(ErrorState.GENERIC_ERROR);
			broadcastState(false, t.getMessage());
			stopForeground(true);
			stopSelf();
		}
	}

	private static String stripScope(String address)
	{
		int i = address.indexOf('%');
		return i >= 0 ? address.substring(0, i) : address;
	}

	private static boolean hasHandshake(String config)
	{
		if (config == null)
		{
			return false;
		}
		for (String line : config.split("\n"))
		{
			if (line.startsWith("last_handshake_time_sec="))
			{
				try
				{
					return Long.parseLong(line.substring(24).trim()) > 0;
				}
				catch (NumberFormatException e)
				{
					return false;
				}
			}
		}
		return false;
	}

	private void configureBuilder(Builder builder, JSONObject p, String name) throws Exception
	{
		builder.setSession(name);
		int mtu = 1280;
		try
		{
			mtu = Integer.parseInt(p.optString("mtu", "1280").trim());
		}
		catch (NumberFormatException ignored)
		{
		}
		builder.setMtu(mtu);
		builder.setConfigureIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
			PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)));
		for (String a : AwgConfig.split(p.getString("client_ip")))
		{
			String[] cidr = splitCidr(a);
			builder.addAddress(InetAddress.getByName(cidr[0]), Integer.parseInt(cidr[1]));
		}
		for (String r : AwgConfig.split(p.optString("allowed_ips", "0.0.0.0/0, ::/0")))
		{
			String[] cidr = splitCidr(r);
			builder.addRoute(InetAddress.getByName(cidr[0]), Integer.parseInt(cidr[1]));
		}
		boolean dns = false;
		String d1 = p.optString("dns1", "").trim();
		String d2 = p.optString("dns2", "").trim();
		if (!d1.isEmpty())
		{
			builder.addDnsServer(InetAddress.getByName(d1));
			dns = true;
		}
		if (!d2.isEmpty())
		{
			builder.addDnsServer(InetAddress.getByName(d2));
			dns = true;
		}
		if (!dns)
		{
			builder.addDnsServer("1.1.1.1");
		}
	}

	private static String[] splitCidr(String value)
	{
		String v = value.trim();
		int slash = v.indexOf('/');
		if (slash >= 0)
		{
			return new String[]{v.substring(0, slash), v.substring(slash + 1)};
		}
		return new String[]{v, v.contains(":") ? "128" : "32"};
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

	private void startConnection(String name)
	{
		synchronized (stateLock)
		{
			if (stateService != null)
			{
				VpnProfile profile = new VpnProfile();
				profile.setName(name);
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

	private void closeTunnel()
	{
		running = false;
		handler.removeCallbacks(tick);
		if (handle != -1)
		{
			int h = handle;
			handle = -1;
			try
			{
				GoBackend.awgTurnOff(h);
			}
			catch (Throwable t)
			{
				Log.e(TAG, "turn off failed", t);
			}
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
			NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "AmneziaWG VPN", NotificationManager.IMPORTANCE_LOW);
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
			startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
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
		int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
		Intent stop = new Intent(this, AwgVpnService.class).setAction(ACTION_STOP);
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
			b.setSubText(sName);
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
			b.setContentText(sName);
			b.setColor(ContextCompat.getColor(this, R.color.warning_text));
		}
		return b.build();
	}
}
