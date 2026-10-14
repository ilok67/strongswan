package org.strongswan.android.utils;

import android.net.TrafficStats;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

/**
 * Measures VPN traffic (speed and totals since connect) on all Android versions.
 * Android 9 and older: reads the tun interface counters.
 * Android 10+: /sys/class/net is blocked, so it falls back to the app's UID counters.
 */
public class TrafficMonitor
{
	private long mBaseRx = -1;
	private long mBaseTx = -1;
	private long mPrevRx = -1;
	private long mPrevTx = -1;
	private long mPrevAt;
	private String mDownSpeed = "—";
	private String mUpSpeed = "—";
	private String mDownTotal = "—";
	private String mUpTotal = "—";

	/** Call when a new connection starts. */
	public void reset()
	{
		mBaseRx = mBaseTx = -1;
		mPrevRx = mPrevTx = -1;
		mDownSpeed = mUpSpeed = mDownTotal = mUpTotal = "—";
	}

	/** Take a new sample; call about once per second. */
	public void update()
	{
		long now = SystemClock.elapsedRealtime();
		long[] io = readTraffic();
		if (io == null)
		{
			return;
		}
		mDownTotal = formatBytes(io[0]);
		mUpTotal = formatBytes(io[1]);
		if (mPrevRx >= 0)
		{
			double sec = Math.max(0.2, (now - mPrevAt) / 1000.0);
			mDownSpeed = formatBytes((long) (Math.max(0, io[0] - mPrevRx) / sec)) + "/s";
			mUpSpeed = formatBytes((long) (Math.max(0, io[1] - mPrevTx) / sec)) + "/s";
		}
		mPrevRx = io[0];
		mPrevTx = io[1];
		mPrevAt = now;
	}

	/** Example: ↓ 1.2 MB/s  48.0 MB    ↑ 120.0 KB/s  3.1 MB */
	public String getLine()
	{
		return "↓ " + mDownSpeed + "  " + mDownTotal + "    ↑ " + mUpSpeed + "  " + mUpTotal;
	}

	private long[] readTraffic()
	{
		long[] io = readTun();
		if (io != null)
		{
			return io;
		}
		int uid = android.os.Process.myUid();
		long rx = TrafficStats.getUidRxBytes(uid);
		long tx = TrafficStats.getUidTxBytes(uid);
		if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED)
		{
			return null;
		}
		if (mBaseRx < 0 || mBaseTx < 0 || rx < mBaseRx || tx < mBaseTx)
		{
			mBaseRx = rx;
			mBaseTx = tx;
		}
		return new long[]{rx - mBaseRx, tx - mBaseTx};
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
		String[] u = {"KB", "MB", "GB", "TB"};
		int i = -1;
		while (v >= 1024 && i < u.length - 1)
		{
			v /= 1024;
			i++;
		}
		return String.format(Locale.US, "%.1f %s", v, u[i]);
	}
}
