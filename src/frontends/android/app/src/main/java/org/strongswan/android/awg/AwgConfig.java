package org.strongswan.android.awg;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AwgConfig
{
	public static final String[] OPTIONAL_KEYS = {
		"Jc", "Jmin", "Jmax", "S1", "S2", "S3", "S4", "H1", "H2", "H3", "H4",
		"I1", "I2", "I3", "I4", "I5", "HeaderProtectionKey", "ContentPaddingAddition",
		"RekeyAfterTime", "RekeyTimeout", "RejectAfterTime", "KeepaliveTimeout",
		"MaxHandshakeAttempts", "RandomTrailers", "DisableCookies"
	};

	public static JSONObject parse(String input) throws Exception
	{
		String text = input.trim();
		if (text.contains("[Interface]"))
		{
			return fromConf(text);
		}
		if (text.startsWith("{"))
		{
			return fromAmnezia(new JSONObject(text));
		}
		String decoded = AmneziaGateway.decodeVpnKey(text).trim();
		if (decoded.contains("[Interface]"))
		{
			return fromConf(decoded);
		}
		return fromAmnezia(new JSONObject(decoded));
	}

	public static JSONObject fromAmnezia(JSONObject root) throws Exception
	{
		JSONArray containers = root.optJSONArray("containers");
		JSONObject awg = null;
		if (containers != null)
		{
			for (int i = 0; i < containers.length(); i++)
			{
				JSONObject c = containers.optJSONObject(i);
				if (c != null && c.optJSONObject("awg") != null)
				{
					awg = c.getJSONObject("awg");
					break;
				}
			}
		}
		if (awg == null)
		{
			throw new Exception("no AmneziaWG container in config");
		}
		Object lastRaw = awg.opt("last_config");
		JSONObject last;
		if (lastRaw instanceof JSONObject)
		{
			last = (JSONObject) lastRaw;
		}
		else
		{
			last = new JSONObject(String.valueOf(lastRaw));
		}
		String host = last.optString("hostName", root.optString("hostName", "")).trim();
		int port = parsePort(last.opt("port"));
		if (port == 0)
		{
			port = parsePort(awg.opt("port"));
		}
		JSONObject p = new JSONObject();
		p.put("id", UUID.randomUUID().toString());
		String name = root.optString("description", "").trim();
		p.put("name", name.isEmpty() ? host : name);
		p.put("host", host);
		p.put("port", port);
		copy(last, p, "client_priv_key");
		copy(last, p, "server_pub_key");
		copy(last, p, "psk_key");
		copy(last, p, "client_ip");
		copy(last, p, "mtu");
		copy(last, p, "persistent_keep_alive");
		for (String k : OPTIONAL_KEYS)
		{
			copy(last, p, k);
		}
		Object allowed = last.opt("allowed_ips");
		if (allowed instanceof JSONArray)
		{
			StringBuilder sb = new StringBuilder();
			JSONArray arr = (JSONArray) allowed;
			for (int i = 0; i < arr.length(); i++)
			{
				if (i > 0)
				{
					sb.append(", ");
				}
				sb.append(arr.getString(i).trim());
			}
			p.put("allowed_ips", sb.toString());
		}
		else if (allowed != null && !String.valueOf(allowed).trim().isEmpty())
		{
			p.put("allowed_ips", String.valueOf(allowed).trim());
		}
		else
		{
			p.put("allowed_ips", "0.0.0.0/0, ::/0");
		}
		p.put("dns1", root.optString("dns1", "").trim());
		p.put("dns2", root.optString("dns2", "").trim());
		String country = findCountry(root, last, awg);
		if (!country.isEmpty())
		{
			AwgGeo.applyCountry(p, country, 1);
		}
		validate(p);
		return p;
	}

	private static String findCountry(JSONObject root, JSONObject last, JSONObject awg)
	{
		JSONObject api = root.optJSONObject("api_config");
		String[] candidates = {
			api != null ? api.optString("server_country_code", "") : "",
			api != null ? api.optString("country_code", "") : "",
			root.optString("server_country_code", ""),
			root.optString("country_code", ""),
			last.optString("server_country_code", ""),
			awg.optString("server_country_code", "")
		};
		for (String c : candidates)
		{
			if (c != null && c.trim().length() == 2)
			{
				return c.trim().toUpperCase(java.util.Locale.US);
			}
		}
		return "";
	}

	public static JSONObject fromConf(String text) throws Exception
	{
		Map<String, String> iface = new HashMap<String, String>();
		Map<String, String> peer = new HashMap<String, String>();
		Map<String, String> canonical = new HashMap<String, String>();
		for (String k : OPTIONAL_KEYS)
		{
			canonical.put(k.toLowerCase(), k);
		}
		Map<String, String> current = null;
		for (String raw : text.split("\\r?\\n"))
		{
			String line = raw;
			int hash = line.indexOf('#');
			if (hash >= 0)
			{
				line = line.substring(0, hash);
			}
			line = line.trim();
			if (line.isEmpty())
			{
				continue;
			}
			if (line.startsWith("["))
			{
				String section = line.toLowerCase();
				current = section.startsWith("[interface") ? iface : section.startsWith("[peer") ? peer : null;
				continue;
			}
			int eq = line.indexOf('=');
			if (eq < 0 || current == null)
			{
				continue;
			}
			current.put(line.substring(0, eq).trim().toLowerCase(), line.substring(eq + 1).trim());
		}
		String endpoint = peer.get("endpoint");
		if (endpoint == null || !endpoint.contains(":"))
		{
			throw new Exception("Endpoint missing in config");
		}
		int idx = endpoint.lastIndexOf(':');
		String host = endpoint.substring(0, idx).replace("[", "").replace("]", "").trim();
		JSONObject p = new JSONObject();
		p.put("id", UUID.randomUUID().toString());
		p.put("name", host);
		p.put("host", host);
		p.put("port", parsePort(endpoint.substring(idx + 1).trim()));
		p.put("client_priv_key", nz(iface.get("privatekey")));
		p.put("client_ip", nz(iface.get("address")));
		p.put("server_pub_key", nz(peer.get("publickey")));
		String psk = peer.get("presharedkey");
		if (psk != null && !psk.isEmpty())
		{
			p.put("psk_key", psk);
		}
		String mtu = iface.get("mtu");
		if (mtu != null && !mtu.isEmpty())
		{
			p.put("mtu", mtu);
		}
		String ka = peer.get("persistentkeepalive");
		if (ka != null && !ka.isEmpty())
		{
			p.put("persistent_keep_alive", ka);
		}
		String allowed = peer.get("allowedips");
		p.put("allowed_ips", allowed == null || allowed.isEmpty() ? "0.0.0.0/0, ::/0" : allowed);
		String dns = iface.get("dns");
		String dns1 = "";
		String dns2 = "";
		if (dns != null)
		{
			String[] parts = dns.split("\\s*,\\s*");
			dns1 = parts.length > 0 ? parts[0] : "";
			dns2 = parts.length > 1 ? parts[1] : "";
		}
		p.put("dns1", dns1);
		p.put("dns2", dns2);
		for (Map.Entry<String, String> e : canonical.entrySet())
		{
			String v = iface.get(e.getKey());
			if (v != null && !v.isEmpty())
			{
				p.put(e.getValue(), v);
			}
		}
		validate(p);
		return p;
	}

	public static String toUapi(JSONObject p, String endpointIp) throws Exception
	{
		StringBuilder sb = new StringBuilder();
		sb.append("private_key=").append(b64ToHex(p.getString("client_priv_key"))).append('\n');
		boolean obfuscated = has(p, "Jc") && has(p, "Jmin") && has(p, "Jmax") && has(p, "S1") && has(p, "S2")
			&& has(p, "H1") && has(p, "H2") && has(p, "H3") && has(p, "H4");
		if (obfuscated)
		{
			line(sb, "jc", p, "Jc");
			line(sb, "jmin", p, "Jmin");
			line(sb, "jmax", p, "Jmax");
			line(sb, "s1", p, "S1");
			line(sb, "s2", p, "S2");
			line(sb, "s3", p, "S3");
			line(sb, "s4", p, "S4");
			line(sb, "h1", p, "H1");
			line(sb, "h2", p, "H2");
			line(sb, "h3", p, "H3");
			line(sb, "h4", p, "H4");
			line(sb, "i1", p, "I1");
			line(sb, "i2", p, "I2");
			line(sb, "i3", p, "I3");
			line(sb, "i4", p, "I4");
			line(sb, "i5", p, "I5");
		}
		if (has(p, "HeaderProtectionKey"))
		{
			sb.append("header_protection_key=").append(b64ToHex(p.getString("HeaderProtectionKey").trim())).append('\n');
		}
		line(sb, "content_padding_addition", p, "ContentPaddingAddition");
		line(sb, "rekey_after_time", p, "RekeyAfterTime");
		line(sb, "rekey_timeout", p, "RekeyTimeout");
		line(sb, "reject_after_time", p, "RejectAfterTime");
		line(sb, "keepalive_timeout", p, "KeepaliveTimeout");
		line(sb, "max_handshake_attempts", p, "MaxHandshakeAttempts");
		if (has(p, "RandomTrailers"))
		{
			sb.append("random_trailers=").append(uapiBool(p.getString("RandomTrailers"))).append('\n');
		}
		if (has(p, "DisableCookies"))
		{
			sb.append("disable_cookies=").append(uapiBool(p.getString("DisableCookies"))).append('\n');
		}
		sb.append("replace_peers=true\n");
		sb.append("public_key=").append(b64ToHex(p.getString("server_pub_key"))).append('\n');
		for (String r : split(p.optString("allowed_ips", "0.0.0.0/0, ::/0")))
		{
			sb.append("allowed_ip=").append(r.contains("/") ? r : (r.contains(":") ? r + "/128" : r + "/32")).append('\n');
		}
		String ep = endpointIp.contains(":") ? "[" + endpointIp + "]" : endpointIp;
		sb.append("endpoint=").append(ep).append(':').append(p.getInt("port")).append('\n');
		String ka = p.optString("persistent_keep_alive", "").trim();
		sb.append("persistent_keepalive_interval=").append(ka.isEmpty() || ka.equals("0") ? "25" : ka).append('\n');
		if (has(p, "psk_key"))
		{
			sb.append("preshared_key=").append(b64ToHex(p.getString("psk_key"))).append('\n');
		}
		return sb.toString();
	}

	public static String[] split(String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return new String[0];
		}
		return value.trim().split("\\s*,\\s*");
	}

	private static void validate(JSONObject p) throws Exception
	{
		if (p.optString("host", "").isEmpty() || p.optInt("port", 0) <= 0)
		{
			throw new Exception("host or port missing");
		}
		if (p.optString("client_priv_key", "").isEmpty() || p.optString("server_pub_key", "").isEmpty()
			|| p.optString("client_ip", "").isEmpty())
		{
			throw new Exception("keys or address missing");
		}
	}

	private static void copy(JSONObject from, JSONObject to, String key) throws Exception
	{
		if (from.has(key) && !from.isNull(key))
		{
			String v = String.valueOf(from.get(key)).trim();
			if (!v.isEmpty())
			{
				to.put(key, v);
			}
		}
	}

	private static boolean has(JSONObject p, String key)
	{
		return p.has(key) && !p.isNull(key) && !p.optString(key, "").trim().isEmpty();
	}

	private static void line(StringBuilder sb, String uapiKey, JSONObject p, String key)
	{
		if (has(p, key))
		{
			sb.append(uapiKey).append('=').append(p.optString(key, "").trim()).append('\n');
		}
	}

	private static String uapiBool(String v)
	{
		String s = v.trim().toLowerCase();
		return s.equals("on") || s.equals("1") || s.equals("true") || s.equals("t") || s.equals("yes") ? "1" : "0";
	}

	private static String nz(String v)
	{
		return v == null ? "" : v;
	}

	private static int parsePort(Object o)
	{
		if (o == null)
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(String.valueOf(o).trim());
		}
		catch (Exception e)
		{
			return 0;
		}
	}

	private static String b64ToHex(String b64)
	{
		byte[] raw = Base64.decode(b64.trim(), Base64.DEFAULT);
		StringBuilder sb = new StringBuilder();
		for (byte b : raw)
		{
			sb.append(String.format("%02x", b & 0xff));
		}
		return sb.toString();
	}
}
