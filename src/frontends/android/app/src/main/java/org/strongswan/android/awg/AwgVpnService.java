package org.strongswan.android.awg;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.amnezia.awg.GoBackend;
import org.json.JSONObject;
import org.strongswan.android.R;
import org.strongswan.android.ui.MainActivity;
import org.strongswan.android.utils.TrafficMonitor;
import org.strongswan.android.v2ray.SingBoxVpnService;

import java.net.Inet6Address;
import java.net.InetAddress;

public class AwgVpnService extends VpnService
{
	public static final String ACTION_START = "org.strongswan.android.AWG_START";
	public static final String ACTION_STOP = "org.strongswan.android.AWG_STOP";
	public static final String ACTION_QUERY = "org.strongswan.android.AWG_QUERY";
	public static final String ACTION_STATE = "org.strongswan.android.AWG_STATE";
	public static final String ACTION_INTERNAL = "org.strongswan.android.AWG_INTERNAL";
	public static final String EXTRA_PROFILE = "profile";
	public static final String EXTRA_RUNNING = "running";
	public static final String EXTRA_ERROR = "error";
	public static final String EXTRA_PHASE = "phase";
	public static final String EXTRA_ID = "id";
	public static final String EXTRA_NAME = "name";
	public static final String EXTRA_UPDATE_STATE = "update_state";

	private static final String TAG = "AwgVpnService";
	private static final String CHANNEL_ID = "awg_vpn";
	private static final int NOTIFICATION_ID = 7202;

	private final Object lock = new Object();
	private final TrafficMonitor traffic = new TrafficMonitor();
	private Handler handler;
	private int handle = -1;
	private long connectedAt;
	private volatile int generation;
	private volatile boolean foreground;

	private final Runnable tick = new Runnable()
	{
		@Override
		public void run()
		{
			if (!AwgState.running)
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
		return AwgState.running;
	}

	public static boolean isActive()
	{
		return AwgState.id != null;
	}

	public static boolean isActive(String id)
	{
		return AwgState.id != null && AwgState.id.equals(id);
	}

	public static String getLastProfile()
	{
		return AwgState.lastProfile;
	}

	public static void start(final Context context, final JSONObject profile)
	{
		final Context app = context.getApplicationContext();
		AwgState.lastProfile = profile.toString();
		AwgState.id = profile.optString("id", "awg");
		AwgState.name = profile.optString("name", "AmneziaWG");
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					if (SingBoxVpnService.hasSession() || SingBoxVpnService.isRunning())
					{
						SingBoxVpnService.stopBlocking(app);
					}
					else
					{
						AwgBridge.dropIkev2();
					}
				}
				catch (Throwable t)
				{
					Log.e(TAG, "handover failed", t);
				}
				Intent i = new Intent(app, AwgVpnService.class);
				i.setAction(ACTION_START);
				i.putExtra(EXTRA_PROFILE, profile.toString());
				try
				{
					ContextCompat.startForegroundService(app, i);
				}
				catch (Throwable t)
				{
					Log.e(TAG, "start failed", t);
					AwgState.id = null;
				}
			}
		}).start();
	}

	public static void stop(Context context)
	{
		stopBlocking(context, true, false);
	}

	public static void stopBlocking(Context context)
	{
		stopBlocking(context, false, true);
	}

	public static void stopBlocking(Context context, boolean updateState)
	{
		stopBlocking(context, updateState, true);
	}

	private static void stopBlocking(Context context, boolean updateState, boolean wait)
	{
		if (!isActive())
		{
			return;
		}
		Intent i = new Intent(context.getApplicationContext(), AwgVpnService.class);
		i.setAction(ACTION_STOP);
		i.putExtra(EXTRA_UPDATE_STATE, updateState);
		try
		{
			context.getApplicationContext().startService(i);
		}
		catch (Throwable t)
		{
			Log.e(TAG, "stop failed", t);
			AwgState.id = null;
			AwgState.running = false;
			return;
		}
		if (!wait || Looper.myLooper() == Looper.getMainLooper())
		{
			return;
		}
		long deadline = System.currentTimeMillis() + 6000;
		while (isActive() && System.currentTimeMillis() < deadline)
		{
			try
			{
				Thread.sleep(50);
			}
			catch (InterruptedException e)
			{
				break;
			}
		}
		if (isActive())
		{
			AwgState.id = null;
			AwgState.running = false;
		}
		try
		{
			Thread.sleep(150);
		}
		catch (InterruptedException ignored)
		{
		}
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
		handler = new Handler(Looper.getMainLooper());
		createNotificationChannel();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		String action = intent != null ? intent.getAction() : null;
		if (ACTION_STOP.equals(action))
		{
			AwgState.id = null;
			shutdown(intent.getBooleanExtra(EXTRA_UPDATE_STATE, true));
			return START_NOT_STICKY;
		}
		if (ACTION_QUERY.equals(action))
		{
			if (AwgState.running)
			{
				sendInternal("connected", false, null);
			}
			else if (AwgState.id != null)
			{
				sendInternal("connecting", false, null);
			}
			else
			{
				sendInternal("stopped", false, null);
				stopSelf();
			}
			return START_NOT_STICKY;
		}
		if (ACTION_START.equals(action))
		{
			final String json = intent.getStringExtra(EXTRA_PROFILE);
			try
			{
				JSONObject p = new JSONObject(json);
				AwgState.id = p.optString("id", "awg");
				AwgState.name = p.optString("name", "AmneziaWG");
			}
			catch (Exception e)
			{
				AwgState.id = null;
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
		AwgState.id = null;
		shutdown(true);
	}

	@Override
	public void onDestroy()
	{
		synchronized (lock)
		{
			closeTunnel();
		}
		super.onDestroy();
	}

	private void shutdown(boolean updateState)
	{
		generation++;
		synchronized (lock)
		{
			closeTunnel();
		}
		AwgState.id = null;
		sendInternal("stopped", updateState, null);
		broadcastState(false, null);
		removeNotification();
		stopSelf();
	}

	private void removeNotification()
	{
		foreground = false;
		try
		{
			stopForeground(true);
		}
		catch (Throwable ignored)
		{
		}
		try
		{
			((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
		}
		catch (Throwable ignored)
		{
		}
	}

	private void startTunnel(String json, int gen)
	{
		try
		{
			JSONObject p = new JSONObject(json);
			String name = p.optString("name", "AmneziaWG");
			sendInternal("connecting", false, null);
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
			AwgState.running = true;
			connectedAt = System.currentTimeMillis();
			traffic.reset();
			traffic.update();
			updateNotification();
			handler.post(tick);
			sendInternal("connected", false, null);
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
			AwgState.id = null;
			sendInternal("error", false, t.getMessage());
			broadcastState(false, t.getMessage());
			removeNotification();
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

	private void closeTunnel()
	{
		AwgState.running = false;
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

	private void sendInternal(String phase, boolean updateState, String error)
	{
		Intent i = new Intent(ACTION_INTERNAL);
		i.setPackage(getPackageName());
		i.putExtra(EXTRA_PHASE, phase);
		i.putExtra(EXTRA_ID, AwgState.id);
		i.putExtra(EXTRA_NAME, AwgState.name);
		i.putExtra(EXTRA_UPDATE_STATE, updateState);
		if (error != null)
		{
			i.putExtra(EXTRA_ERROR, error);
		}
		sendBroadcast(i);
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
		foreground = true;
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
		if (!foreground)
		{
			return;
		}
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, buildNotification());
	}

	private Notification buildNotification()
	{
		int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
		Intent stop = new Intent(this, AwgVpnService.class).setAction(ACTION_STOP).putExtra(EXTRA_UPDATE_STATE, true);
		PendingIntent stopIntent = PendingIntent.getService(this, 0, stop, piFlags);
		PendingIntent openIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), piFlags);

		Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
		b.setSmallIcon(R.drawable.ic_notification);
		b.setCategory(Notification.CATEGORY_SERVICE);
		b.setOngoing(true);
		b.setOnlyAlertOnce(true);
		b.setContentIntent(openIntent);
		b.addAction(R.drawable.ic_notification_disconnect, getString(R.string.disconnect), stopIntent);
		if (AwgState.running)
		{
			b.setContentTitle(getString(R.string.state_connected));
			b.setContentText(traffic.getLine());
			b.setSubText(AwgState.name);
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
			b.setContentText(AwgState.name);
			b.setColor(ContextCompat.getColor(this, R.color.warning_text));
		}
		return b.build();
	}
}
