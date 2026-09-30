package org.strongswan.android.ui;

import org.json.JSONObject;

import java.io.BufferedReader;
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
		try (BufferedReader r = new BufferedReader(
			new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8)))
		{
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = r.readLine()) != null)
			{
				sb.append(line);
			}
			JSONObject json = new JSONObject(sb.toString());
			String tag = json.getString("tag_name");
			String apk = json.getJSONArray("assets")
				.getJSONObject(0)
				.getString("browser_download_url");
			return new Result(tag, apk);
		}
		finally
		{
			c.disconnect();
		}
	}
}
