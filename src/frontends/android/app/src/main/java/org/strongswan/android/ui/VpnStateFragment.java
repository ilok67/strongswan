package org.strongswan.android.ui;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.strongswan.android.R;
import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.logic.VpnStateService;
import org.strongswan.android.logic.VpnStateService.ErrorState;
import org.strongswan.android.logic.VpnStateService.State;
import org.strongswan.android.logic.VpnStateService.VpnStateListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class VpnStateFragment extends Fragment implements VpnStateListener
{
	private boolean mVisible;
	private TextView mProfileNameView;
	private TextView mProfileView;
	private TextView mStateView;
	private int mColorStateBase;
	private int mColorStateError;
	private int mColorStateSuccess;
	private Button mActionButton;
	private ProgressBar mProgress;
	private LinearLayout mErrorView;
	private TextView mErrorText;
	private Button mErrorRetry;
	private Button mShowLog;
	private VpnStateService mService;
	private TextView mStatsView;
	private final android.os.Handler mStatsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
	private static long sStartElapsed;
	private static long sStatsConnId = -1;
	private long mPrevRx = -1;
	private long mPrevTx = -1;
	private long mPrevAt;

	private final Runnable mStatsTick = new Runnable()
	{
		@Override
		public void run()
		{
			if (mService == null || mStatsView == null || getActivity() == null)
			{
				return;
			}
			if (mService.getState() != State.CONNECTED)
			{
				mStatsView.setVisibility(View.GONE);
				return;
			}
			long id = mService.getConnectionID();
			long now = android.os.SystemClock.elapsedRealtime();
			if (id != sStatsConnId)
			{
				sStatsConnId = id;
				sStartElapsed = now;
				mPrevRx = -1;
				mPrevTx = -1;
			}
			long[] io = readTun();
			String downSpeed = "—";
			String upSpeed = "—";
			String downTotal = "—";
			String upTotal = "—";
			if (io != null)
			{
				downTotal = formatBytes(io[0]);
				upTotal = formatBytes(io[1]);
				if (mPrevRx >= 0)
				{
					double sec = Math.max(0.2, (now - mPrevAt) / 1000.0);
					downSpeed = formatBytes((long) ((io[0] - mPrevRx) / sec)) + "/s";
					upSpeed = formatBytes((long) ((io[1] - mPrevTx) / sec)) + "/s";
				}
				mPrevRx = io[0];
				mPrevTx = io[1];
				mPrevAt = now;
			}
			long elapsed = Math.max(0, (now - sStartElapsed) / 1000);
			String time = String.format(Locale.US, "%02d:%02d:%02d",
				elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60);
			mStatsView.setText(getString(R.string.vpn_stats_line, time, downSpeed, downTotal, upSpeed, upTotal));
			mStatsView.setVisibility(View.VISIBLE);
			mStatsHandler.postDelayed(this, 1000);
		}
	};

	private final ServiceConnection mServiceConnection = new ServiceConnection()
	{
		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			mService = null;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			mService = ((VpnStateService.LocalBinder) service).getService();
			if (mVisible)
			{
				mService.registerListener(VpnStateFragment.this);
				updateView();
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		mColorStateError = ContextCompat.getColor(getActivity(), R.color.error_text);
		mColorStateSuccess = ContextCompat.getColor(getActivity(), R.color.success_text);

		Context context = getActivity().getApplicationContext();
		context.bindService(new Intent(context, VpnStateService.class),
			mServiceConnection, Service.BIND_AUTO_CREATE);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.vpn_state_fragment, null);

		mActionButton = (Button) view.findViewById(R.id.action);
		mActionButton.setOnClickListener(v -> {
			if (mService != null)
			{
				mService.disconnect();
			}
		});
		enableActionButton(null);

		mErrorView = view.findViewById(R.id.vpn_error);
		mErrorText = view.findViewById(R.id.vpn_error_text);
		mErrorRetry = view.findViewById(R.id.retry);
		mShowLog = view.findViewById(R.id.show_log);
		mProgress = (ProgressBar) view.findViewById(R.id.progress);
		mStateView = (TextView) view.findViewById(R.id.vpn_state);
		mColorStateBase = mStateView.getCurrentTextColor();
		mProfileView = (TextView) view.findViewById(R.id.vpn_profile_label);
		mProfileNameView = (TextView) view.findViewById(R.id.vpn_profile_name);
		mStatsView = view.findViewById(R.id.vpn_stats);

		mErrorRetry.setOnClickListener(v -> {
			if (mService != null)
			{
				mService.reconnect();
			}
		});
		mShowLog.setOnClickListener(v ->
			startActivity(new Intent(getActivity(), LogActivity.class)));

		return view;
	}

	@Override
	public void onStart()
	{
		super.onStart();
		mVisible = true;
		if (mService != null)
		{
			mService.registerListener(this);
			updateView();
		}
	}

	@Override
	public void onStop()
	{
		super.onStop();
		mVisible = false;
		mStatsHandler.removeCallbacks(mStatsTick);
		if (mService != null)
		{
			mService.unregisterListener(this);
		}
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		mStatsHandler.removeCallbacks(mStatsTick);
		if (mService != null)
		{
			getActivity().getApplicationContext().unbindService(mServiceConnection);
		}
	}

	@Override
	public void stateChanged()
	{
		updateView();
	}

	public void updateView()
	{
		VpnProfile profile = mService.getProfile();
		State state = mService.getState();
		ErrorState error = mService.getErrorState();
		String name = "";

		if (getActivity() == null)
		{
			return;
		}

		if (profile != null)
		{
			name = profile.getName();
		}

		if (reportError(name, error))
		{
			return;
		}

		mProfileNameView.setText(name);
		mProgress.setIndeterminate(true);

		switch (state)
		{
			case DISABLED:
				showProfile(false);
				mProgress.setVisibility(View.GONE);
				enableActionButton(null);
				mStateView.setText(R.string.state_disabled);
				mStateView.setTextColor(mColorStateBase);
				stopStats();
				break;
			case CONNECTING:
				showProfile(true);
				mProgress.setVisibility(View.VISIBLE);
				enableActionButton(getString(android.R.string.cancel));
				mStateView.setText(R.string.state_connecting);
				mStateView.setTextColor(mColorStateBase);
				stopStats();
				break;
			case CONNECTED:
				showProfile(true);
				mProgress.setVisibility(View.GONE);
				enableActionButton(getString(R.string.disconnect));
				mStateView.setText(R.string.state_connected);
				mStateView.setTextColor(mColorStateSuccess);
				mStatsHandler.removeCallbacks(mStatsTick);
				mStatsHandler.post(mStatsTick);
				break;
			case DISCONNECTING:
				showProfile(true);
				mProgress.setVisibility(View.VISIBLE);
				enableActionButton(null);
				mStateView.setText(R.string.state_disconnecting);
				mStateView.setTextColor(mColorStateBase);
				stopStats();
				break;
		}
	}

	private boolean reportError(String name, ErrorState error)
	{
		if (error == ErrorState.NO_ERROR)
		{
			mErrorView.setVisibility(View.GONE);
			return false;
		}
		stopStats();
		mProfileNameView.setText(name);
		showProfile(true);
		mStateView.setText(R.string.state_error);
		mStateView.setTextColor(mColorStateError);
		enableActionButton(getString(android.R.string.cancel));

		int retry = mService.getRetryIn();
		if (retry > 0)
		{
			mProgress.setIndeterminate(false);
			mProgress.setMax(mService.getRetryTimeout());
			mProgress.setProgress(retry);
			mProgress.setVisibility(View.VISIBLE);
			mStateView.setText(getResources().getQuantityString(R.plurals.retry_in, retry, retry));
		}
		else if (mService.getRetryTimeout() <= 0)
		{
			mProgress.setVisibility(View.GONE);
		}

		mErrorText.setText(getString(R.string.error_format, getString(mService.getErrorText())));
		mErrorView.setVisibility(View.VISIBLE);
		return true;
	}

	private void stopStats()
	{
		mStatsHandler.removeCallbacks(mStatsTick);
		if (mStatsView != null)
		{
			mStatsView.setVisibility(View.GONE);
		}
	}

	private void showProfile(boolean show)
	{
		mProfileView.setVisibility(show ? View.VISIBLE : View.GONE);
		mProfileNameView.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	private void enableActionButton(String text)
	{
		mActionButton.setText(text);
		mActionButton.setEnabled(text != null);
		mActionButton.setVisibility(text != null ? View.VISIBLE : View.GONE);
	}

	private static long[] readTun()
	{
		File[] list = new File("/sys/class/net").listFiles();
		if (list == null)
		{
			return null;
		}
		long rx = 0;
		long tx = 0;
		boolean found = false;
		for (File f : list)
		{
			if (!f.getName().startsWith("tun"))
			{
				continue;
			}
			long r = readLong(new File(f, "statistics/rx_bytes"));
			long t = readLong(new File(f, "statistics/tx_bytes"));
			if (r < 0 || t < 0)
			{
				continue;
			}
			rx += r;
			tx += t;
			found = true;
		}
		return found ? new long[]{rx, tx} : null;
	}

	private static long readLong(File file)
	{
		try (BufferedReader r = new BufferedReader(new FileReader(file)))
		{
			return Long.parseLong(r.readLine().trim());
		}
		catch (Exception e)
		{
			return -1;
		}
	}

	private static String formatBytes(long n)
	{
		if (n < 0)
		{
			n = 0;
		}
		if (n < 1024)
		{
			return n + " B";
		}
		double v = n;
		String[] u = {"KB", "MB", "GB"};
		int i = -1;
		while (v >= 1024 && i < u.length - 1)
		{
			v /= 1024;
			i++;
		}
		return String.format(Locale.US, "%.1f %s", v, u[i]);
	}
}
