package org.strongswan.android.awg;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Locale;

public final class AwgGeo
{
	private AwgGeo()
	{
	}

	public static String lookup(String host)
	{
		String ip;
		try
		{
			ip = InetAddress.getByName(host).getHostAddress();
		}
		catch (Exception e)
		{
			return "";
		}
		String[][] services = {
			{"https://ipwho.is/" + ip + "?fields=success,country_code", "country_code"},
			{"http://ip-api.com/json/" + ip + "?fields=status,countryCode", "countryCode"}
		};
		for (String[] svc : services)
		{
			HttpURLConnection conn = null;
			try
			{
				conn = (HttpURLConnection) new URL(svc[0]).openConnection();
				conn.setConnectTimeout(6000);
				conn.setReadTimeout(6000);
				InputStream in = conn.getInputStream();
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buf = new byte[2048];
				int n;
				while ((n = in.read(buf)) > 0)
				{
					out.write(buf, 0, n);
				}
				in.close();
				String code = new JSONObject(out.toString("UTF-8")).optString(svc[1], "");
				if (code.length() == 2)
				{
					return code.toUpperCase(Locale.US);
				}
			}
			catch (Exception ignored)
			{
			}
			finally
			{
				if (conn != null)
				{
					conn.disconnect();
				}
			}
		}
		return "";
	}

	public static String flag(String code)
	{
		if (code == null || code.length() != 2)
		{
			return "\uD83C\uDF10";
		}
		String c = code.toUpperCase(Locale.US);
		int a = Character.codePointAt(c, 0) - 'A' + 0x1F1E6;
		int b = Character.codePointAt(c, 1) - 'A' + 0x1F1E6;
		if (a < 0x1F1E6 || a > 0x1F1FF || b < 0x1F1E6 || b > 0x1F1FF)
		{
			return "\uD83C\uDF10";
		}
		return new String(Character.toChars(a)) + new String(Character.toChars(b));
	}

	public static String countryName(String code)
	{
		if (code == null || code.length() != 2)
		{
			return "";
		}
		String name = new Locale("", code.toUpperCase(Locale.US)).getDisplayCountry();
		return name == null || name.isEmpty() ? code.toUpperCase(Locale.US) : name;
	}

	public static void applyCountry(JSONObject profile, String code, int index) throws Exception
	{
		if (code == null || code.isEmpty())
		{
			return;
		}
		profile.put("country", code);
		String name = flag(code) + " " + countryName(code);
		if (index > 1)
		{
			name = name + " " + index;
		}
		profile.put("name", name);
	}
}
