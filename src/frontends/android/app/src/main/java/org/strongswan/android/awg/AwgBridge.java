package org.strongswan.android.awg;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.logic.VpnStateService;
import org.strongswan.android.logic.VpnStateService.ErrorState;
import org.strongswan.android.logic.VpnStateService.State;
import org.strongswan.android.v2ray.SingBoxVpnService;

import java.io.FileInputStream;

public final class AwgBridge
{
	private static final String TAG = "AwgBridge";
	private static final String PREFS = "awg_bridge";

	private static Context app;
	private static volatile VpnStateService stateService;
	private static String lastPhase;
	private static String lastName = "";

	private AwgBridge()
	{
	}

	public static boolean isMainProcess(Context context)
	{
		try (FileInputStream in = new FileInputStream("/proc/self/cmdline"))
		{
			byte[] buf = new byte[256];
			int n = in.read(buf);
			int end = 0;
			while (end < n && buf[end] != 0)
			{
				end++;
			}
			String name = new String(buf, 0, end, "UTF-8");
			return name.equals(context.getPackageName());
		}
		catch (Exception e)
		{
			return true;
		}
	}

	public static synchronized void init(Context context)
	{
		if (app != null)
		{
			return;
		}
		app = context.getApplicationContext();
		ContextCompat.registerReceiver(app, receiver, new IntentFilter(AwgVpnService.ACTION_INTERNAL),
			ContextCompat.RECEIVER_NOT_EXPORTED);
		try
		{
			app.bindService(new Intent(app, VpnStateService.class), connection, Context.BIND_AUTO_CREATE);
		}
		catch (Exception e)
		{
			Log.e(TAG, "bind failed", e);
		}
		SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		if (prefs.getBoolean("up", false))
		{
			try
			{
				app.startService(new Intent(app, AwgVpnService.class).setAction(AwgVpnService.ACTION_QUERY));
			}
			catch (Exception e)
			{
				Log.e(TAG, "query failed", e);
			}
		}
	}

	private static final ServiceConnection connection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder binder)
		{
			stateService = ((VpnStateService.LocalBinder) binder).getService();
			if ("connecting".equals(lastPhase) || "connected".equals(lastPhase))
			{
				applyShared(lastPhase, lastName, false);
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			stateService = null;
		}
	};

	private static final BroadcastReceiver receiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String phase = intent.getStringExtra(AwgVpnService.EXTRA_PHASE);
			String id = intent.getStringExtra(AwgVpnService.EXTRA_ID);
			String name = intent.getStringExtra(AwgVpnService.EXTRA_NAME);
			boolean updateState = intent.getBooleanExtra(AwgVpnService.EXTRA_UPDATE_STATE, false);
			if (phase == null)
			{
				return;
			}
			lastPhase = phase;
			lastName = name == null ? "" : name;
			SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
			if ("connecting".equals(phase))
			{
				AwgState.id = id;
				AwgState.name = lastName;
				AwgState.running = false;
			}
			else if ("connected".equals(phase))
			{
				AwgState.id = id;
				AwgState.name = lastName;
				AwgState.running = true;
				prefs.edit().putBoolean("up", true).apply();
			}
			else
			{
				AwgState.running = false;
				AwgState.id = null;
				prefs.edit().putBoolean("up", false).apply();
			}
			applyShared(phase, lastName, updateState);
		}
	};

	private static void applyShared(String phase, String name, boolean updateState)
	{
		VpnStateService service = stateService;
		if (service == null)
		{
			return;
		}
		try
		{
			if ("connecting".equals(phase))
			{
				VpnProfile profile = new VpnProfile();
				profile.setName(name);
				service.startConnection(profile);
			}
			else if ("connected".equals(phase))
			{
				service.setState(State.CONNECTED);
			}
			else if ("error".equals(phase))
			{
				service.setError(ErrorState.GENERIC_ERROR);
			}
			else if ("stopped".equals(phase) && updateState && !SingBoxVpnService.hasSession())
			{
				service.setState(State.DISABLED);
			}
		}
		catch (Throwable t)
		{
			Log.e(TAG, "apply failed", t);
		}
	}

	public static void dropIkev2() throws InterruptedException
	{
		for (int i = 0; i < 40 && stateService == null; i++)
		{
			Thread.sleep(50);
		}
		VpnStateService service = stateService;
		if (service == null || AwgState.running || service.getState() == State.DISABLED)
		{
			return;
		}
		service.disconnect();
		for (int i = 0; i < 100; i++)
		{
			if (service.getState() == State.DISABLED)
			{
				return;
			}
			Thread.sleep(50);
		}
	}
}
