package org.strongswan.android.awg;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AwgPing
{
	private static final Pattern TIME = Pattern.compile("time[=<]([0-9.]+)\\s*ms");

	private AwgPing()
	{
	}

	public static int ping(String host)
	{
		if (host == null || host.isEmpty())
		{
			return -1;
		}
		int viaProcess = viaProcess(host);
		if (viaProcess >= 0)
		{
			return viaProcess;
		}
		return viaReachable(host);
	}

	private static int viaProcess(final String host)
	{
		final Process[] holder = new Process[1];
		java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
		try
		{
			java.util.concurrent.Future<Integer> future = executor.submit(new java.util.concurrent.Callable<Integer>()
			{
				@Override
				public Integer call() throws Exception
				{
					String binary = host.contains(":") ? "/system/bin/ping6" : "/system/bin/ping";
					Process process = new ProcessBuilder(binary, "-c", "2", "-W", "2", host).redirectErrorStream(true).start();
					holder[0] = process;
					BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
					double best = -1;
					String line;
					while ((line = reader.readLine()) != null)
					{
						Matcher m = TIME.matcher(line);
						if (m.find())
						{
							double v = Double.parseDouble(m.group(1));
							if (best < 0 || v < best)
							{
								best = v;
							}
						}
					}
					return best < 0 ? -1 : Math.max(1, (int) Math.round(best));
				}
			});
			return future.get(9, TimeUnit.SECONDS);
		}
		catch (Throwable t)
		{
			return -1;
		}
		finally
		{
			Process process = holder[0];
			if (process != null)
			{
				process.destroy();
			}
			executor.shutdownNow();
		}
	}

	private static int viaReachable(String host)
	{
		try
		{
			InetAddress address = InetAddress.getByName(host);
			long start = System.nanoTime();
			if (address.isReachable(3000))
			{
				return Math.max(1, (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
			}
		}
		catch (Throwable ignored)
		{
		}
		return -1;
	}
}
