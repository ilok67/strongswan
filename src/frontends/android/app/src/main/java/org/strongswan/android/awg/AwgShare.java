package org.strongswan.android.awg;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;

public final class AwgShare
{
	private AwgShare()
	{
	}

	public static String toConf(JSONObject p)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("[Interface]\n");
		sb.append("PrivateKey = ").append(p.optString("client_priv_key")).append('\n');
		sb.append("Address = ").append(p.optString("client_ip")).append('\n');
		String dns = join(p.optString("dns1"), p.optString("dns2"));
		if (!dns.isEmpty())
		{
			sb.append("DNS = ").append(dns).append('\n');
		}
		if (!p.optString("mtu").isEmpty())
		{
			sb.append("MTU = ").append(p.optString("mtu")).append('\n');
		}
		for (String k : AwgConfig.OPTIONAL_KEYS)
		{
			if (!p.optString(k).isEmpty())
			{
				sb.append(k).append(" = ").append(p.optString(k)).append('\n');
			}
		}
		sb.append("\n[Peer]\n");
		sb.append("PublicKey = ").append(p.optString("server_pub_key")).append('\n');
		if (!p.optString("psk_key").isEmpty())
		{
			sb.append("PresharedKey = ").append(p.optString("psk_key")).append('\n');
		}
		sb.append("AllowedIPs = ").append(p.optString("allowed_ips", "0.0.0.0/0, ::/0")).append('\n');
		sb.append("Endpoint = ").append(endpoint(p)).append('\n');
		String ka = p.optString("persistent_keep_alive", "25");
		sb.append("PersistentKeepalive = ").append(ka.isEmpty() ? "25" : ka).append('\n');
		return sb.toString();
	}

	public static String toVpnKey(JSONObject p) throws Exception
	{
		JSONObject last = new JSONObject();
		last.put("hostName", p.optString("host"));
		last.put("port", p.optInt("port"));
		last.put("client_priv_key", p.optString("client_priv_key"));
		last.put("server_pub_key", p.optString("server_pub_key"));
		if (!p.optString("psk_key").isEmpty())
		{
			last.put("psk_key", p.optString("psk_key"));
		}
		last.put("client_ip", p.optString("client_ip"));
		JSONArray allowed = new JSONArray();
		for (String a : AwgConfig.split(p.optString("allowed_ips", "0.0.0.0/0, ::/0")))
		{
			allowed.put(a);
		}
		last.put("allowed_ips", allowed);
		if (!p.optString("mtu").isEmpty())
		{
			last.put("mtu", p.optString("mtu"));
		}
		if (!p.optString("persistent_keep_alive").isEmpty())
		{
			last.put("persistent_keep_alive", p.optString("persistent_keep_alive"));
		}
		for (String k : AwgConfig.OPTIONAL_KEYS)
		{
			if (!p.optString(k).isEmpty())
			{
				last.put(k, p.optString(k));
			}
		}
		last.put("config", toConf(p));

		JSONObject awg = new JSONObject();
		awg.put("isThirdPartyConfig", true);
		awg.put("last_config", last.toString());
		awg.put("port", String.valueOf(p.optInt("port")));
		awg.put("transport_proto", "udp");

		JSONObject container = new JSONObject();
		container.put("container", "amnezia-awg");
		container.put("awg", awg);

		JSONObject root = new JSONObject();
		root.put("containers", new JSONArray().put(container));
		root.put("defaultContainer", "amnezia-awg");
		root.put("description", p.optString("name"));
		root.put("dns1", p.optString("dns1"));
		root.put("dns2", p.optString("dns2"));
		root.put("hostName", p.optString("host"));

		byte[] raw = root.toString().getBytes("UTF-8");
		Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
		deflater.setInput(raw);
		deflater.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write((raw.length >> 24) & 0xff);
		out.write((raw.length >> 16) & 0xff);
		out.write((raw.length >> 8) & 0xff);
		out.write(raw.length & 0xff);
		byte[] buf = new byte[4096];
		while (!deflater.finished())
		{
			int n = deflater.deflate(buf);
			out.write(buf, 0, n);
		}
		deflater.end();
		return "vpn://" + Base64.encodeToString(out.toByteArray(), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
	}

	private static String endpoint(JSONObject p)
	{
		String host = p.optString("host");
		if (host.contains(":") && !host.startsWith("["))
		{
			host = "[" + host + "]";
		}
		return host + ":" + p.optInt("port");
	}

	private static String join(String a, String b)
	{
		if (a.isEmpty())
		{
			return b;
		}
		return b.isEmpty() ? a : a + ", " + b;
	}
}
