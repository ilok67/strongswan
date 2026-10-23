package org.strongswan.android.v2ray;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public final class V2RaySecondaryBuilder
{
    private static final String[][] COUNTRIES = {
        {"AT", "\uD83C\uDDE6\uD83C\uDDF9", "Austria"},
        {"BE", "\uD83C\uDDE7\uD83C\uDDEA", "Belgium"},
        {"BG", "\uD83C\uDDE7\uD83C\uDDEC", "Bulgaria"},
        {"CA", "\uD83C\uDDE8\uD83C\uDDE6", "Canada"},
        {"CH", "\uD83C\uDDE8\uD83C\uDDED", "Switzerland"},
        {"CZ", "\uD83C\uDDE8\uD83C\uDDFF", "Czech Republic"},
        {"DE", "\uD83C\uDDE9\uD83C\uDDEA", "Germany"},
        {"DK", "\uD83C\uDDE9\uD83C\uDDF0", "Denmark"},
        {"EE", "\uD83C\uDDEA\uD83C\uDDEA", "Estonia"},
        {"ES", "\uD83C\uDDEA\uD83C\uDDF8", "Spain"},
        {"FI", "\uD83C\uDDEB\uD83C\uDDEE", "Finland"},
        {"FR", "\uD83C\uDDEB\uD83C\uDDF7", "France"},
        {"GB", "\uD83C\uDDEC\uD83C\uDDE7", "United Kingdom"},
        {"UK", "\uD83C\uDDEC\uD83C\uDDE7", "United Kingdom"},
        {"GR", "\uD83C\uDDEC\uD83C\uDDF7", "Greece"},
        {"HR", "\uD83C\uDDED\uD83C\uDDF7", "Croatia"},
        {"HU", "\uD83C\uDDED\uD83C\uDDFA", "Hungary"},
        {"IE", "\uD83C\uDDEE\uD83C\uDDEA", "Ireland"},
        {"IL", "\uD83C\uDDEE\uD83C\uDDF1", "Israel"},
        {"IN", "\uD83C\uDDEE\uD83C\uDDF3", "India"},
        {"IR", "\uD83C\uDDEE\uD83C\uDDF7", "Iran"},
        {"IT", "\uD83C\uDDEE\uD83C\uDDF9", "Italy"},
        {"JP", "\uD83C\uDDEF\uD83C\uDDF5", "Japan"},
        {"KR", "\uD83C\uDDF0\uD83C\uDDF7", "South Korea"},
        {"LT", "\uD83C\uDDF1\uD83C\uDDF9", "Lithuania"},
        {"LV", "\uD83C\uDDF1\uD83C\uDDFB", "Latvia"},
        {"MD", "\uD83C\uDDF2\uD83C\uDDE9", "Moldova"},
        {"MX", "\uD83C\uDDF2\uD83C\uDDFD", "Mexico"},
        {"NL", "\uD83C\uDDF3\uD83C\uDDF1", "Netherlands"},
        {"NO", "\uD83C\uDDF3\uD83C\uDDF4", "Norway"},
        {"PL", "\uD83C\uDDF5\uD83C\uDDF1", "Poland"},
        {"PT", "\uD83C\uDDF5\uD83C\uDDF9", "Portugal"},
        {"RO", "\uD83C\uDDF7\uD83C\uDDF4", "Romania"},
        {"RS", "\uD83C\uDDF7\uD83C\uDDF8", "Serbia"},
        {"RU", "\uD83C\uDDF7\uD83C\uDDFA", "Russia"},
        {"SE", "\uD83C\uDDF8\uD83C\uDDEA", "Sweden"},
        {"SG", "\uD83C\uDDF8\uD83C\uDDEC", "Singapore"},
        {"SK", "\uD83C\uDDF8\uD83C\uDDF0", "Slovakia"},
        {"TR", "\uD83C\uDDF9\uD83C\uDDF7", "Turkey"},
        {"UA", "\uD83C\uDDFA\uD83C\uDDE6", "Ukraine"},
        {"US", "\uD83C\uDDFA\uD83C\uDDF8", "United States"},
        {"AU", "\uD83C\uDDE6\uD83C\uDDFA", "Australia"},
        {"BR", "\uD83C\uDDE7\uD83C\uDDF7", "Brazil"},
        {"HK", "\uD83C\uDDED\uD83C\uDDF0", "Hong Kong"},
        {"TW", "\uD83C\uDDF9\uD83C\uDDFC", "Taiwan"},
    };

    private static final Map<String, String> FLAGS = new HashMap<>();
    private static final Map<String, String> NAMES = new HashMap<>();

    static
    {
        for (String[] row : COUNTRIES)
        {
            FLAGS.put(row[0], row[1]);
            NAMES.put(row[0], row[2]);
        }
    }

    private static final Map<String, String> FAMILY = new HashMap<>();

    static
    {
        FAMILY.put("supx_v1", "vmess");
        FAMILY.put("supx_v3", "vmess");
        FAMILY.put("supx_v5", "vmess");
        FAMILY.put("supx_v7", "vmess");
        FAMILY.put("supx_v10", "ss");
        FAMILY.put("supx_v20", "ss");
        FAMILY.put("supx_v21", "vmess");
        FAMILY.put("supx_v22", "vmess");
        FAMILY.put("supx_v23", "vmess");
    }

    private V2RaySecondaryBuilder()
    {
    }

    /**
     * @param data  پاسخ decrypt شده mobilejump (همان که FreeServersActivity دارد)
     * @param max   حداکثر تعداد لینک
     * @param filter "all" | "vmess" | "ss"
     */
    public static List<String> buildLinks(JSONObject data, int max, String filter)
    {
        List<String> out = new ArrayList<>();
        if (data == null || max <= 0)
        {
            return out;
        }
        String want = filter == null ? "all" : filter.toLowerCase();
        if (!"vmess".equals(want) && !"ss".equals(want))
        {
            want = "all";
        }

        Templates tpl = extractTemplates(data);

        // defaults
        Map<String, JSONObject> defaults = new HashMap<>();
        JSONArray ds = data.optJSONArray("default_services");
        if (ds != null)
        {
            for (int i = 0; i < ds.length(); i++)
            {
                JSONObject s = ds.optJSONObject(i);
                if (s != null)
                {
                    defaults.put(s.optString("id", ""), s);
                }
            }
        }

        // جمع‌آوری سرورها به ترتیب free سپس vip (مثل Worker)
        List<JSONObject> servers = new ArrayList<>();
        collect(data.optJSONArray("servers"), servers);
        collect(data.optJSONArray("vip_servers"), servers);

        // مرتب‌سازی بر اساس load صعودی
        servers.sort((a, b) -> {
            int la = a.optInt("load", 999999);
            int lb = b.optInt("load", 999999);
            return Integer.compare(la, lb);
        });

        for (JSONObject server : servers)
        {
            if (out.size() >= max)
            {
                break;
            }
            String serverHost = server.optString("host", "");

            JSONObject loc = server.optJSONObject("location");
            String country = loc != null ? loc.optString("country", "") : "";
            String city = loc != null ? loc.optString("city", "") : "";
            int load = server.optInt("load", 999999);

            JSONArray services = server.optJSONArray("services");
            if (services == null || services.length() == 0)
            {
                // fallback: از defaults استفاده کن (مثل Worker)
                for (Map.Entry<String, JSONObject> e : defaults.entrySet())
                {
                    if (out.size() >= max)
                    {
                        break;
                    }
                    String id = e.getKey();
                    if (!FAMILY.containsKey(id))
                    {
                        continue;
                    }
                    String family = FAMILY.get(id);
                    if (!matchFamily(want, family))
                    {
                        continue;
                    }
                    addService(out, tpl, id, family, serverHost, serverHost,
                            e.getValue().optJSONArray("ports"),
                            country, city, load, max);
                }
                continue;
            }

            for (int i = 0; i < services.length() && out.size() < max; i++)
            {
                JSONObject svc = services.optJSONObject(i);
                if (svc == null)
                {
                    continue;
                }
                String id = svc.optString("id", "");
                String family = FAMILY.get(id);
                if (family == null)
                {
                    continue;
                }
                if (!matchFamily(want, family))
                {
                    continue;
                }
                String inbound = svc.optString("inbound_ip", serverHost);
                if (inbound == null || inbound.isEmpty())
                {
                    inbound = serverHost;
                }
                if (inbound.isEmpty())
                {
                    continue;
                }
                addService(out, tpl, id, family, inbound, serverHost,
                        svc.optJSONArray("ports"), country, city, load, max);
            }
        }
        return out;
    }

    private static void collect(JSONArray arr, List<JSONObject> out)
    {
        if (arr == null)
        {
            return;
        }
        for (int i = 0; i < arr.length(); i++)
        {
            JSONObject o = arr.optJSONObject(i);
            if (o != null)
            {
                out.add(o);
            }
        }
    }

    private static boolean matchFamily(String want, String family)
    {
        if ("all".equals(want))
        {
            return true;
        }
        if ("ss".equals(want))
        {
            return "ss".equals(family);
        }
        return "vmess".equals(family);
    }

    private static void addService(List<String> out, Templates tpl, String id,
                                   String family, String host, String fallbackHost,
                                   JSONArray ports, String country, String city,
                                   int load, int max)
    {
        if (host == null || host.isEmpty())
        {
            host = fallbackHost;
        }
        if (host == null || host.isEmpty())
        {
            return;
        }
        if (ports == null || ports.length() == 0)
        {
            return;
        }
        for (int p = 0; p < ports.length() && out.size() < max; p++)
        {
            int port = ports.optInt(p, 0);
            if (port < 1 || port > 65535)
            {
                continue;
            }
            String remark = remark(country, city, out.size() + 1);
            if ("ss".equals(family))
            {
                String method = tpl.ssMethod(id);
                String pass = tpl.ssPassword(id);
                if (pass.isEmpty())
                {
                    continue;
                }
                String link = ssLink(host, port, method, pass, remark);
                if (link != null)
                {
                    out.add(link);
                }
            }
            else
            {
                String uuid = tpl.uuid(id);
                if (uuid.isEmpty())
                {
                    continue;
                }
                String link = vmessLink(host, port,
                        uuid,
                        tpl.security(id),
                        tpl.network(id),
                        tpl.tls(id),
                        tpl.sni(id),
                        remark);
                if (link != null)
                {
                    out.add(link);
                }
            }
        }
    }

    private static String remark(String country, String city, int index)
    {
        String cc = country == null ? "" : country.toUpperCase();
        String flag = FLAGS.containsKey(cc) ? FLAGS.get(cc) : "\uD83C\uDF10";
        String name = NAMES.containsKey(cc) ? NAMES.get(cc)
                : (cc.isEmpty() ? "Unknown" : cc);
        StringBuilder sb = new StringBuilder();
        sb.append(flag).append(' ').append(index).append(' ').append(name);
        if (city != null && !city.isEmpty())
        {
            sb.append(" \u2013 ").append(city);
        }
        return sb.toString();
    }

    private static String ssLink(String host, int port, String method,
                                 String pass, String remark)
    {
        try
        {
            String ui = b64Url(method + ":" + pass);
            return "ss://" + ui + "@" + host + ":" + port
                    + "#" + urlEnc(remark);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static String vmessLink(String host, int port, String uuid,
                                    String scy, String net, String tls,
                                    String sni, String remark)
    {
        try
        {
            JSONObject o = new JSONObject();
            o.put("v", "2");
            o.put("ps", remark);
            o.put("add", host);
            o.put("port", String.valueOf(port));
            o.put("id", uuid);
            o.put("aid", "0");
            o.put("scy", scy);
            o.put("net", net);
            o.put("type", "none");
            o.put("host", "");
            o.put("path", "");
            o.put("tls", tls == null ? "" : tls);
            o.put("sni", sni == null ? "" : sni);
            o.put("alpn", "");
            return "vmess://" + b64(o.toString());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static String b64(String s)
    {
        return android.util.Base64.encodeToString(
                s.getBytes(StandardCharsets.UTF_8),
                android.util.Base64.NO_WRAP);
    }

    private static String b64Url(String s)
    {
        return b64(s).replace('+', '-').replace('/', '_').replaceAll("=+$", "");
    }

    private static String urlEnc(String s)
    {
        try
        {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        }
        catch (Exception e)
        {
            return s;
        }
    }

    // ---------- templates ----------

    private static Templates extractTemplates(JSONObject data)
    {
        Templates t = new Templates();
        JSONObject tpls = data.optJSONObject("templates");
        if (tpls == null)
        {
            return t;
        }
        Iterator<String> keys = tpls.keys();
        while (keys.hasNext())
        {
            String key = keys.next();
            JSONObject tpl;
            try
            {
                Object raw = tpls.opt(key);
                if (raw instanceof String)
                {
                    tpl = new JSONObject((String) raw);
                }
                else if (raw instanceof JSONObject)
                {
                    tpl = (JSONObject) raw;
                }
                else
                {
                    continue;
                }
            }
            catch (Exception e)
            {
                continue;
            }

            JSONArray outs = tpl.optJSONArray("outbounds");
            if (outs == null)
            {
                continue;
            }
            for (int i = 0; i < outs.length(); i++)
            {
                JSONObject ob = outs.optJSONObject(i);
                if (ob == null)
                {
                    continue;
                }
                String proto = ob.optString("protocol", "").toLowerCase();
                JSONObject st = ob.optJSONObject("settings");
                if (st == null)
                {
                    continue;
                }
                JSONObject ss = ob.optJSONObject("streamSettings");

                if ("shadowsocks".equals(proto))
                {
                    JSONArray srv = st.optJSONArray("servers");
                    if (srv == null || srv.length() == 0)
                    {
                        continue;
                    }
                    JSONObject s0 = srv.optJSONObject(0);
                    if (s0 == null)
                    {
                        continue;
                    }
                    t.ssMethods.put(key, s0.optString("method", "aes-256-gcm"));
                    t.ssPasswords.put(key, s0.optString("password", ""));
                }
                else if ("vmess".equals(proto))
                {
                    JSONArray vn = st.optJSONArray("vnext");
                    if (vn == null || vn.length() == 0)
                    {
                        continue;
                    }
                    JSONObject v0 = vn.optJSONObject(0);
                    if (v0 == null)
                    {
                        continue;
                    }
                    JSONArray users = v0.optJSONArray("users");
                    JSONObject u = (users != null && users.length() > 0)
                            ? users.optJSONObject(0) : null;
                    if (u == null)
                    {
                        continue;
                    }
                    t.uuids.put(key, u.optString("id", ""));
                    t.securities.put(key, u.optString("security", "auto"));
                    if (ss != null)
                    {
                        t.networks.put(key, ss.optString("network", "tcp"));
                        String sec = ss.optString("security", "");
                        t.tls.put(key, "tls".equals(sec) ? "tls" : sec);
                        JSONObject tlsSet = ss.optJSONObject("tlsSettings");
                        t.snis.put(key, tlsSet != null
                                ? tlsSet.optString("serverName", "") : "");
                    }
                }
            }
        }
        return t;
    }

    private static final class Templates
    {
        final Map<String, String> ssMethods = new HashMap<>();
        final Map<String, String> ssPasswords = new HashMap<>();
        final Map<String, String> uuids = new HashMap<>();
        final Map<String, String> securities = new HashMap<>();
        final Map<String, String> networks = new HashMap<>();
        final Map<String, String> tls = new HashMap<>();
        final Map<String, String> snis = new HashMap<>();

        String ssMethod(String id)
        {
            String v = ssMethods.get(id);
            if (v == null) v = first(ssMethods);
            return v == null ? "aes-256-gcm" : v;
        }

        String ssPassword(String id)
        {
            String v = ssPasswords.get(id);
            if (v == null) v = first(ssPasswords);
            return v == null ? "" : v;
        }

        String uuid(String id)
        {
            String v = uuids.get(id);
            if (v == null) v = first(uuids);
            return v == null ? "" : v;
        }

        String security(String id)
        {
            String v = securities.get(id);
            if (v == null) v = first(securities);
            return v == null ? "auto" : v;
        }

        String network(String id)
        {
            String v = networks.get(id);
            if (v == null) v = first(networks);
            return v == null ? "tcp" : v;
        }

        String tls(String id)
        {
            String v = tls.get(id);
            if (v == null) v = first(tls);
            return v == null ? "tls" : v;
        }

        String sni(String id)
        {
            String v = snis.get(id);
            if (v == null) v = first(snis);
            return v == null ? "" : v;
        }

        private String first(Map<String, String> m)
        {
            for (String s : m.values())
            {
                if (s != null && !s.isEmpty())
                {
                    return s;
                }
            }
            return null;
        }
    }
}
