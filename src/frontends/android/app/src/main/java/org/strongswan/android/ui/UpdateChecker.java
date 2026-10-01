package org.strongswan.android.ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class UpdateChecker
{
	private static final String API =
		"https://api.github.com/repos/ilok67/strongswan/releases/latest";

	public static class Result
	{
		public final String tag;
		public final String apkUrl;

		Result(String tag, String apkUrl)
		{
			this.tag = tag;
			this.apkUrl = apkUrl;
		}
	}

	private UpdateChecker() {}

	public static Result latest() throws Exception
	{
		HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
		c.setRequestProperty("User-Agent", "BardiaVPN");
		c.setRequestProperty("Accept", "application/vnd.github+json");
		c.setConnectTimeout(8000);
		c.setReadTimeout(8000);
		try
		{
			int code = c.getResponseCode();
			if (code != HttpURLConnection.HTTP_OK)
			{
				throw new IOException("HTTP " + code);
			}
			BufferedReader r = new BufferedReader(
				new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = r.readLine()) != null)
			{
				sb.append(line);
			}
			r.close();

			JSONObject json = new JSONObject(sb.toString());
			String tag = json.getString("tag_name");
			String apk = null;
			JSONArray assets = json.getJSONArray("assets");
			for (int i = 0; i < assets.length(); i++)
			{
				JSONObject a = assets.getJSONObject(i);
				if (a.getString("name").toLowerCase().endsWith(".apk"))
				{
					apk = a.getString("browser_download_url");
					break;
				}
			}
			if (apk == null)
			{
				apk = json.getString("html_url");
			}
			return new Result(tag, apk);
		}
		finally
		{
			c.disconnect();
		}
	}

	public static boolean isNewer(String latestTag, String current)
	{
		int[] a = parse(latestTag);
		int[] b = parse(current);
		for (int i = 0; i < 3; i++)
		{
			if (a[i] != b[i])
			{
				return a[i] > b[i];
			}
		}
		return false;
	}

	private static int[] parse(String raw)
	{
		int[] out = new int[3];
		if (raw == null)
		{
			return out;
		}
		String s = raw.trim();
		if (s.startsWith("v") || s.startsWith("V"))
		{
			s = s.substring(1);
		}
		int dash = s.indexOf('-');
		if (dash >= 0)
		{
			s = s.substring(0, dash);
		}
		String[] p = s.split("\\.");
		for (int i = 0; i < 3 && i < p.length; i++)
		{
			try
			{
				out[i] = Integer.parseInt(p[i].replaceAll("[^0-9]", ""));
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		return out;
	}
}
