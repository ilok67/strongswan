package org.strongswan.android.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.strongswan.android.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public final class CrashReporter
{
	private static final String FILE = "last_crash.txt";
	private static boolean installed;

	private CrashReporter()
	{
	}

	public static synchronized void install(final Context context)
	{
		if (installed)
		{
			return;
		}
		installed = true;
		final Context app = context.getApplicationContext();
		final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler()
		{
			@Override
			public void uncaughtException(Thread thread, Throwable throwable)
			{
				try
				{
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					pw.println("thread: " + thread.getName());
					throwable.printStackTrace(pw);
					pw.flush();
					try (FileOutputStream out = app.openFileOutput(FILE, Context.MODE_PRIVATE))
					{
						out.write(sw.toString().getBytes(StandardCharsets.UTF_8));
					}
				}
				catch (Throwable ignored)
				{
				}
				if (previous != null)
				{
					previous.uncaughtException(thread, throwable);
				}
			}
		});
	}

	public static void showIfAny(final Activity activity)
	{
		File file = activity.getFileStreamPath(FILE);
		if (!file.exists())
		{
			return;
		}
		String text;
		try (FileInputStream in = new FileInputStream(file))
		{
			byte[] data = new byte[(int) Math.min(file.length(), 20000)];
			int n = in.read(data);
			text = new String(data, 0, Math.max(n, 0), StandardCharsets.UTF_8);
		}
		catch (Exception e)
		{
			return;
		}
		file.delete();
		final String report = text;
		TextView view = new TextView(activity);
		view.setText(report);
		view.setTextSize(11);
		view.setTextIsSelectable(true);
		int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
		view.setPadding(pad, pad, pad, pad);
		android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
		scroll.addView(view);
		new AlertDialog.Builder(activity)
			.setTitle(R.string.crash_title)
			.setView(scroll)
			.setPositiveButton(R.string.crash_copy, (d, w) -> {
				ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
				if (cm != null)
				{
					cm.setPrimaryClip(ClipData.newPlainText("crash", report));
					Toast.makeText(activity, R.string.crash_copied, Toast.LENGTH_SHORT).show();
				}
			})
			.setNegativeButton(R.string.crash_close, null)
			.show();
	}
}
