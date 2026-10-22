package org.strongswan.android.v2ray;

import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public final class V2RayConfigBuilder
{
	private V2RayConfigBuilder()
	{
	}

	public static String build(String link) throws Exception
	{
		JSONObject proxy = outbound(link.trim());

		JSONObject root = new JSONObject();
		root.put("log", new JSONObject().put("level", "warn"));

		JSONObject dns = new JSONObject();
		dns.put("servers", new JSONArray()
				.put(new JSONObject().put("tag", "remote").put("address", "https://1.1.1.1/dns-query").put("detour", "proxy"))
				.put(new JSONObject().put("tag", "local").put("address", "local").put("detour", "direct")));
		dns.put("rules", new JSONArray().put(new JSONObject().put("outbound", "any").put("server", "local")));
		dns.put("strategy", "ipv4_only");
		root.put("dns", dns);

		JSONObject tun = new JSONObject();
		tun.put("type", "tun");
		tun.put("tag", "tun-in");
		tun.put("address", new JSONArray().put("172.19.0.1/30"));
		tun.put("mtu", 9000);
		tun.put("auto_route", true);
		tun.put("strict_route", false);
		tun.put("sniff", true);
		root.put("inbounds", new JSONArray().put(tun));

		root.put("outbounds", new JSONArray()
				.put(proxy)
				.put(new JSONObject().put("type", "direct").put("tag", "direct"))
				.put(new JSONObject().put("type", "dns").put("tag", "dns-out")));

		JSONObject route = new JSONObject();
		route.put("auto_detect_interface", true);
		route.put("rules", new JSONArray()
				.put(new JSONObject().put("protocol", "dns").put("outbound", "dns-out"))
				.put(new JSONObject().put("ip_is_private", true).put("outbound", "direct")));
		route.put("final", "proxy");
		root.put("route", route);

		return root.toString();
	}

	public static String name(String link)
	{
		try
		{
			String t = link.trim();
			if (t.toLowerCase().startsWith("vmess://"))
			{
				String json = decode(cut(t.substring(8), '#'));
				String ps = new JSONObject(json).optString("ps");
				return ps.isEmpty() ? "vmess" : ps;
			}
			String f = Uri.parse(t).getFragment();
			if (f != null && !f.isEmpty())
			{
				return f;
			}
			String host = Uri.parse(t).getHost();
			return host != null ? host : "v2ray";
		}
		catch (Exception e)
		{
			return "v2ray";
		}
	}

	private static JSONObject outbound(String link) throws Exception
	{
		int i = link.indexOf("://");
		if (i < 0)
		{
			throw new IllegalArgumentException("invalid link");
		}
		String scheme = link.substring(0, i).toLowerCase();
		switch (scheme)
		{
			case "vless":
				return vless(link);
			case "vmess":
				return vmess(link);
			case "trojan":
				return trojan(link);
			case "ss":
				return shadowsocks(link);
			case "hysteria2":
			case "hy2":
				return hysteria2(link);
			default:
				throw new IllegalArgumentException("unsupported scheme: " + scheme);
		}
	}

	private static JSONObject vless(String link) throws Exception
	{
		Uri u = Uri.parse(link);
		JSONObject o = base("vless", u.getHost(), port(u));
		o.put("uuid", u.getUserInfo());
		String flow = q(u, "flow");
		if (!flow.isEmpty())
		{
			o.put("flow", flow);
		}
		tls(o, q(u, "security"), q(u, "sni"), q(u, "alpn"), q(u, "fp"), flag(q(u, "allowInsecure")) || flag(q(u, "insecure")), q(u, "pbk"), q(u, "sid"));
		transport(o, q(u, "type"), q(u, "host"), q(u, "path"), q(u, "serviceName"));
		return o;
	}

	private static JSONObject vmess(String link) throws Exception
	{
		JSONObject j = new JSONObject(decode(cut(link.substring(8), '#')));
		JSONObject o = base("vmess", j.getString("add"), intOr(j.optString("port"), 443));
		o.put("uuid", j.getString("id"));
		String scy = j.optString("scy");
		o.put("security", scy.isEmpty() ? "auto" : scy);
		o.put("alter_id", intOr(j.optString("aid"), 0));
		String host = j.optString("host");
		String sni = j.optString("sni");
		if (sni.isEmpty())
		{
			sni = host;
		}
		String security = j.optString("tls").equals("tls") ? "tls" : "";
		tls(o, security, sni, j.optString("alpn"), j.optString("fp"), flag(j.optString("allowInsecure")), "", "");
		String net = j.optString("net");
		transport(o, net, host, j.optString("path"), j.optString("path"));
		return o;
	}

	private static JSONObject trojan(String link) throws Exception
	{
		Uri u = Uri.parse(link);
		JSONObject o = base("trojan", u.getHost(), port(u));
		o.put("password", u.getUserInfo());
		String security = q(u, "security");
		if (security.isEmpty())
		{
			security = "tls";
		}
		String sni = q(u, "sni");
		if (sni.isEmpty())
		{
			sni = q(u, "peer");
		}
		tls(o, security, sni, q(u, "alpn"), q(u, "fp"), flag(q(u, "allowInsecure")) || flag(q(u, "insecure")), q(u, "pbk"), q(u, "sid"));
		transport(o, q(u, "type"), q(u, "host"), q(u, "path"), q(u, "serviceName"));
		return o;
	}

	private static JSONObject shadowsocks(String link) throws Exception
	{
		String body = cut(cut(link.substring(5), '#'), '?');
		String userinfo;
		String hostport;
		int at = body.lastIndexOf('@');
		if (at >= 0)
		{
			userinfo = body.substring(0, at);
			hostport = body.substring(at + 1);
			userinfo = userinfo.contains(":") ? Uri.decode(userinfo) : decode(userinfo);
		}
		else
		{
			String d = decode(body);
			at = d.lastIndexOf('@');
			userinfo = d.substring(0, at);
			hostport = d.substring(at + 1);
		}
		if (hostport.endsWith("/"))
		{
			hostport = hostport.substring(0, hostport.length() - 1);
		}
		int colon = userinfo.indexOf(':');
		int pc = hostport.lastIndexOf(':');
		String host = hostport.substring(0, pc);
		if (host.startsWith("[") && host.endsWith("]"))
		{
			host = host.substring(1, host.length() - 1);
		}
		JSONObject o = base("shadowsocks", host, intOr(hostport.substring(pc + 1), 8388));
		o.put("method", userinfo.substring(0, colon));
		o.put("password", userinfo.substring(colon + 1));
		return o;
	}

	private static JSONObject hysteria2(String link) throws Exception
	{
		Uri u = Uri.parse(link);
		JSONObject o = base("hysteria2", u.getHost(), port(u));
		o.put("password", u.getUserInfo());
		String obfs = q(u, "obfs");
		if (!obfs.isEmpty())
		{
			o.put("obfs", new JSONObject().put("type", obfs).put("password", q(u, "obfs-password")));
		}
		JSONObject t = new JSONObject();
		t.put("enabled", true);
		String sni = q(u, "sni");
		if (!sni.isEmpty())
		{
			t.put("server_name", sni);
		}
		if (flag(q(u, "insecure")))
		{
			t.put("insecure", true);
		}
		o.put("tls", t);
		return o;
	}

	private static JSONObject base(String type, String host, int port) throws Exception
	{
		JSONObject o = new JSONObject();
		o.put("type", type);
		o.put("tag", "proxy");
		o.put("server", host);
		o.put("server_port", port);
		return o;
	}

	private static void tls(JSONObject o, String security, String sni, String alpn, String fp, boolean insecure, String pbk, String sid) throws Exception
	{
		boolean reality = security.equals("reality");
		if (!security.equals("tls") && !reality)
		{
			return;
		}
		JSONObject t = new JSONObject();
		t.put("enabled", true);
		if (!sni.isEmpty())
		{
			t.put("server_name", sni);
		}
		if (insecure)
		{
			t.put("insecure", true);
		}
		if (!alpn.isEmpty())
		{
			JSONArray a = new JSONArray();
			for (String s : alpn.split(","))
			{
				a.put(s.trim());
			}
			t.put("alpn", a);
		}
		if (!fp.isEmpty() || reality)
		{
			t.put("utls", new JSONObject().put("enabled", true).put("fingerprint", fp.isEmpty() ? "chrome" : fp));
		}
		if (reality)
		{
			t.put("reality", new JSONObject().put("enabled", true).put("public_key", pbk).put("short_id", sid));
		}
		o.put("tls", t);
	}

	private static void transport(JSONObject o, String type, String host, String path, String service) throws Exception
	{
		JSONObject t = new JSONObject();
		switch (type)
		{
			case "ws":
				t.put("type", "ws");
				int ed = path.indexOf("?ed=");
				if (ed >= 0)
				{
					t.put("max_early_data", intOr(path.substring(ed + 4), 2048));
					t.put("early_data_header_name", "Sec-WebSocket-Protocol");
					path = path.substring(0, ed);
				}
				if (!path.isEmpty())
				{
					t.put("path", path);
				}
				if (!host.isEmpty())
				{
					t.put("headers", new JSONObject().put("Host", host));
				}
				break;
			case "grpc":
				t.put("type", "grpc");
				if (!service.isEmpty())
				{
					t.put("service_name", service);
				}
				break;
			case "http":
			case "h2":
				t.put("type", "http");
				if (!host.isEmpty())
				{
					t.put("host", new JSONArray().put(host));
				}
				if (!path.isEmpty())
				{
					t.put("path", path);
				}
				break;
			case "httpupgrade":
				t.put("type", "httpupgrade");
				if (!host.isEmpty())
				{
					t.put("host", host);
				}
				if (!path.isEmpty())
				{
					t.put("path", path);
				}
				break;
			default:
				return;
		}
		o.put("transport", t);
	}

	private static String q(Uri u, String key)
	{
		String v = u.getQueryParameter(key);
		return v == null ? "" : v;
	}

	private static int port(Uri u)
	{
		return u.getPort() > 0 ? u.getPort() : 443;
	}

	private static boolean flag(String v)
	{
		return v.equals("1") || v.equalsIgnoreCase("true");
	}

	private static int intOr(String v, int def)
	{
		try
		{
			return Integer.parseInt(v.trim());
		}
		catch (Exception e)
		{
			return def;
		}
	}

	private static String cut(String s, char c)
	{
		int i = s.indexOf(c);
		return i >= 0 ? s.substring(0, i) : s;
	}

	private static String decode(String s)
	{
		String fixed = s.trim().replace('-', '+').replace('_', '/');
		return new String(Base64.decode(fixed, Base64.DEFAULT), StandardCharsets.UTF_8);
	}
}
