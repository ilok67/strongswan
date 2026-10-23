package org.strongswan.android.ui;

import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.strongswan.android.R;
import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.data.VpnProfileDataSource;
import org.strongswan.android.data.VpnProfileSource;
import org.strongswan.android.data.VpnType;
import org.strongswan.android.logic.TrustedCertificateManager;
import org.strongswan.android.utils.Constants;
import org.strongswan.android.v2ray.V2RayProfileStore;
import org.strongswan.android.v2ray.V2RaySecondaryBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.zip.Inflater;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class FreeServersActivity extends AppCompatActivity
{
	private static final String TAG = "FreeServersActivity";

	private static final String[] BASE_URLS = {
		"https://www.supervpn.cc",
		"https://d3nrxxff6afmoi.cloudfront.net",
		"https://api.ttsw557.app",
		"https://api.kingyt6761.cc",
		"https://api.wuli009821.net",
		"https://geckovpn.xyz",
		"https://getip.supervpn.cc",
		"https://64.177.65.1",
		"https://45.77.55.192",
		"https://108.61.176.133",
	};

	private static final String APP_VERSION       = "3.1.6";
	private static final String APP_VERSION_CODE  = "146";
	private static final String PACKAGE           = "com.jrzheng.supervpnfree";
	private static final String SECRET_SALT       = "2y6w498L2I";
	private static final String APP_SIGNATURE_SHA256 =
		"5200D5E89B55D901033896DB249503EFB28479279158101A6A0D3BEA4C7AB906";

	private static final String FIXED_ALIAS       = "en";
	private static final String FIXED_CHANNEL     = "play";
	private static final String FIXED_GEO         = "IR";

	private static final int CONNECT_TIMEOUT = 5000;
	private static final int READ_TIMEOUT    = 8000;

	private static final String SPECIAL_CODE = "special";

	private static final String SECONDARY_API =
		"https://prod-api.mobilejump.mobi/be-data/v7/server_list";
	private static final byte[] SECONDARY_AES_KEY =
		"01fb3864e37eb5d4ada3a50a1f1a373e".getBytes(StandardCharsets.US_ASCII);
	private static final String SECONDARY_USER      = "myvpn";
	private static final String SECONDARY_REMOTE_ID = "node.mobilejump.mobi";
	private static final String SECONDARY_REGION    = "IR";
	private static final int SECONDARY_MAX_PROFILES = 100;
	private static final int SECONDARY_TIMEOUT      = 20000;

	private static final String SECONDARY_CA =
		"MIIE8DCCAtigAwIBAgIIEFM70t29i5EwDQYJKoZIhvcNAQEMBQAwFjEUMBIGA1UE" +
		"AxMLRGlnaUNlciBpbmMwHhcNMjIwMjE2MDk1NzMwWhcNMzIwMjE0MDk1NzMwWjAW" +
		"MRQwEgYDVQQDEwtEaWdpQ2VyIGluYzCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCC" +
		"AgoCggIBAPLpxg63CovLEfeB5py7ajcoHGL8t9QIubghIw6phVAMZAvAe1BpF2i9" +
		"hyoCU2WTnQq5nmVkBVns9q5unTTDO64J3CrPELjoc+QX1YU4UgGoMs87TZFnvP9U" +
		"UREZoGZ7MkefozX/l8CrsKdcgggo9KRf3aJEn711pgsoGp+3UlA2omSLqV/2makU" +
		"FEglYUqFjjwxPCXslPodpjCiqFONf1GrgL0SD5AWxHqnaUshZ9iQWahCAVAso9iX" +
		"zS1CAnotgcPij2fwEB6OosaVN+WfgSqcEB/fNTqTyt63nWorSIrJrGVXvd4z4kdl" +
		"iP74t5l6Yl/Vb7HUkN6tjrjRTyBoWT3jK/Q3/lMCVZpBobJs+4RPf0XhJiCOJzuu" +
		"7iIcb/P82Rdsb7HCpccnJbWEKTvPZbW8tanJ6BmMnJ9YSyQFJkBh/5Tj8vNVd0qM" +
		"l2gisV9xw/GKpf7uXmmHP1Qpd5hKMIHIsiNtIHHLaDNxwTHbixFYZn/4xgZSMp5m" +
		"0YZWQKV1Zmnxh7dG1cMWP8f/Zbxz+huYo9FPI+m7AMbXItLnKq9CyOhiAcasUoDG" +
		"gJqBNU9Zk7+qWUiUapWalDXWoEMYMEJdaEblc7gTn4BtL8wel5exb8kX44ITqosA" +
		"PIT9Y7yM+lzgyZ79NZv3C9vresgTcpjRAfIYSRNSl9eAZBVgdLvxAgMBAAGjQjBA" +
		"MA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMB0GA1UdDgQWBBQZWeuU" +
		"8ViQUKaHGsIRo5OxFgS86zANBgkqhkiG9w0BAQwFAAOCAgEAL+H10cJU2EoCILBn" +
		"cS8OX6vsO5NzNYYtE7b7kA3D5mKq9aTVLhP7FSWmCu748ams3f1WEMg6DExHXxDI" +
		"/lByaQvt/L1UQRfPmbuEYM6RytPvDDhIgvGoCueUC0G/VE8Bmi/y/V6VNcju2irI" +
		"0XtR8IcAFi7Sr5maUhImjRs5lSj2WFJzEBB2ObhslxQDUVyqxzJ01lSknsDem3KX" +
		"Iv7hvUkmRrM/4kEp5wFgRjeDPf/bqSTK0WAdB2Vr3l1UMx3zTVFQBbu7n8CQEwK6" +
		"ifuRxMpcU0ZTmajNBZkipMpgwsxjh6rook/Yz4c2XuDyvjPecfRHpsv7ftIFaxsG" +
		"7bRle+oUR5yd4XR9OVBTPuP8fdLqUwp2TbMCt2Lnl65P1M2HLIF8ZezdgUy2zNXs" +
		"9soBkr7f6BuIHq+e3K+hsQQjZxGw+rNGtgjr3d/BD4ataVqa7zswKrhbOgFYZE41" +
		"YwIDdFowZA6AYQ5lbvj4I9nR0xfMgHZJIw3kL0t/gJOPP72ohWYl6TRR2y32pwG8" +
		"I9RRKysndpktfmx+elC2V9d9eqbNyUpjs7LW2lOy1pC+fYT+qV3h09kOdE/9TrE2" +
		"qB6g1ja4MebyXEPA7WdiwIC7Rf8vGhWiZYFLoJxpBl4nXUJ326oYByldoumDrHWc" +
		"hXlrFaFbBESv5BDF/oPIhZYMaqk=";
      private static final java.util.HashMap<String, String> SEC_FLAGS = new java.util.HashMap<>();
private static final java.util.HashMap<String, String> SEC_NAMES = new java.util.HashMap<>();

static
{
	String[][] c = {
		{"AT", "🇦🇹", "Austria"},        {"BE", "🇧🇪", "Belgium"},
		{"BG", "🇧🇬", "Bulgaria"},       {"CA", "🇨🇦", "Canada"},
		{"CH", "🇨🇭", "Switzerland"},    {"CZ", "🇨🇿", "Czech Republic"},
		{"DE", "🇩🇪", "Germany"},        {"DK", "🇩🇰", "Denmark"},
		{"EE", "🇪🇪", "Estonia"},        {"ES", "🇪🇸", "Spain"},
		{"FI", "🇫🇮", "Finland"},        {"FR", "🇫🇷", "France"},
		{"GB", "🇬🇧", "United Kingdom"}, {"UK", "🇬🇧", "United Kingdom"},
		{"GR", "🇬🇷", "Greece"},         {"HR", "🇭🇷", "Croatia"},
		{"HU", "🇭🇺", "Hungary"},        {"IE", "🇮🇪", "Ireland"},
		{"IL", "🇮🇱", "Israel"},         {"IN", "🇮🇳", "India"},
		{"IR", "🇮🇷", "Iran"},           {"IT", "🇮🇹", "Italy"},
		{"JP", "🇯🇵", "Japan"},          {"KR", "🇰🇷", "South Korea"},
		{"LT", "🇱🇹", "Lithuania"},      {"LV", "🇱🇻", "Latvia"},
		{"MD", "🇲🇩", "Moldova"},        {"MX", "🇲🇽", "Mexico"},
		{"NL", "🇳🇱", "Netherlands"},    {"NO", "🇳🇴", "Norway"},
		{"PL", "🇵🇱", "Poland"},         {"PT", "🇵🇹", "Portugal"},
		{"RO", "🇷🇴", "Romania"},        {"RS", "🇷🇸", "Serbia"},
		{"RU", "🇷🇺", "Russia"},         {"SE", "🇸🇪", "Sweden"},
		{"SG", "🇸🇬", "Singapore"},      {"SK", "🇸🇰", "Slovakia"},
		{"TR", "🇹🇷", "Turkey"},         {"UA", "🇺🇦", "Ukraine"},
		{"US", "🇺🇸", "United States"},  {"AU", "🇦🇺", "Australia"},
		{"BR", "🇧🇷", "Brazil"},         {"HK", "🇭🇰", "Hong Kong"},
		{"TW", "🇹🇼", "Taiwan"},
	};
	for (String[] r : c)
	{
		SEC_FLAGS.put(r[0], r[1]);
		SEC_NAMES.put(r[0], r[2]);
	}
}
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_free_servers);

		Spinner spinner = findViewById(R.id.location_spinner);
		Button button = findViewById(R.id.fetch_button);
		ProgressBar progress = findViewById(R.id.fetch_progress);
		TextView status = findViewById(R.id.fetch_status);

		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
			this, R.array.vpn_location_names, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);

		String[] codes = getResources().getStringArray(R.array.vpn_location_codes);

		button.setOnClickListener(v -> {
			String location = codes[spinner.getSelectedItemPosition()];
			button.setEnabled(false);
			spinner.setEnabled(false);
			progress.setVisibility(View.VISIBLE);
			status.setText(R.string.fetch_servers_loading);

			Executors.newSingleThreadExecutor().execute(() -> {
				try
				{
					int count = importProfiles(location);
					runOnUiThread(() -> {
						Toast.makeText(this,
							getString(R.string.fetch_servers_done, count),
							Toast.LENGTH_LONG).show();
						finish();
					});
				}
				catch (Exception e)
				{
					runOnUiThread(() -> {
						progress.setVisibility(View.GONE);
						button.setEnabled(true);
						spinner.setEnabled(true);
						status.setText(getString(R.string.fetch_servers_failed, e.getMessage()));
					});
				}
			});
		});
	}

private int importProfiles(String location) throws Exception
{
    if ("v2ray".equals(location))
    {
        return importV2RayProfiles();
    }
    ArrayList<VpnProfile> profiles;
    if (SPECIAL_CODE.equals(location))
    {
        profiles = fetchSecondaryProfiles();
    }
    else
    {
        profiles = fetchPrimaryProfiles(location);
    }
    return saveProfiles(profiles);
}

	private int importV2RayProfiles() throws Exception
{
    String url = SECONDARY_API + "?platform=android&vip=true&region="
            + SECONDARY_REGION + "&app=";
    byte[] blob = httpGetBytes(url);
    JSONObject data = decryptBlob(blob);

    List<String> links = V2RaySecondaryBuilder.buildLinks(data, SECONDARY_MAX_PROFILES, "all");
    if (links.isEmpty())
    {
        throw new Exception("no v2ray servers found");
    }

    V2RayProfileStore store = new V2RayProfileStore(this);
    List<String> existing = store.load();
    java.util.HashSet<String> set = new java.util.HashSet<>(existing);

    int added = 0;
    for (String link : links)
    {
        if (link != null && set.add(link))
        {
            existing.add(link);
            added++;
        }
    }

    if (added > 0)
    {
        store.save(existing);
        android.content.Intent intent = new android.content.Intent(
                "org.strongswan.android.V2RAY_PROFILES_CHANGED");
        intent.setPackage(getPackageName());
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    return added;
}
	private int saveProfiles(ArrayList<VpnProfile> profiles)
	{
		VpnProfileDataSource dataSource = new VpnProfileSource(this);
		dataSource.open();

		ArrayList<String> uuids = new ArrayList<>();
		try
		{
			for (VpnProfile profile : profiles)
			{
				VpnProfile existing = dataSource.getVpnProfile(profile.getUUID());
				if (existing != null)
				{
					profile.setDataSource(existing.getDataSource());
					dataSource.updateVpnProfile(profile);
				}
				else
				{
					dataSource.insertProfile(profile);
				}
				uuids.add(profile.getUUID().toString());
			}
		}
		finally
		{
			dataSource.close();
		}

		if (!uuids.isEmpty())
		{
			android.content.Intent intent =
				new android.content.Intent(Constants.VPN_PROFILES_CHANGED);
			intent.putExtra(Constants.VPN_PROFILES_MULTIPLE,
				uuids.toArray(new String[0]));
			LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		}
		return uuids.size();
	}

	private ArrayList<VpnProfile> fetchPrimaryProfiles(String location) throws Exception
	{
		Registration reg = doRegister();
		JSONObject data = doAcquire(reg, location);

		ArrayList<JSONObject> gateways = new ArrayList<>();
		collectGateways(data.optJSONArray("gateways"), gateways);
		collectGateways(data.optJSONArray("relayGateways"), gateways);

		ArrayList<VpnProfile> result = new ArrayList<>();
		for (JSONObject g : gateways)
		{
			VpnProfile profile = buildProfile(g, reg, location);
			if (profile != null)
			{
				result.add(profile);
			}
		}
		return result;
	}

	private ArrayList<VpnProfile> fetchSecondaryProfiles() throws Exception
	{
		String url = SECONDARY_API + "?platform=android&vip=true&region="
			+ SECONDARY_REGION + "&app=";
		byte[] blob = httpGetBytes(url);
		JSONObject data = decryptBlob(blob);

		ArrayList<SecondaryRow> rows = collectSecondaryRows(data);
		Collections.sort(rows, new Comparator<SecondaryRow>()
		{
			@Override
			public int compare(SecondaryRow a, SecondaryRow b)
			{
				return Integer.compare(a.load, b.load);
			}
		});

		String alias = installCertificate(SECONDARY_CA);

		ArrayList<VpnProfile> result = new ArrayList<>();
		int index = 1;
		for (SecondaryRow r : rows)
		{
			if (result.size() >= SECONDARY_MAX_PROFILES)
			{
				break;
			}
			String cc = r.country.toUpperCase();
            String flag = SEC_FLAGS.containsKey(cc) ? SEC_FLAGS.get(cc) : "🌐";
            String cname = SEC_NAMES.containsKey(cc) ? SEC_NAMES.get(cc) : (cc.isEmpty() ? "Unknown" : cc);
            String name = flag + " " + index + " " + cname;
           if (!r.city.isEmpty())
            {
	          name += " – " + r.city;
            }
              name += " :" + r.port;

			VpnProfile profile = new VpnProfile();
			profile.setUUID(UUID.nameUUIDFromBytes(
				("secondary:" + r.gateway + ":" + r.port).getBytes(StandardCharsets.UTF_8)));
			profile.setName(name);
			profile.setVpnType(VpnType.fromIdentifier("ikev2-eap"));
			profile.setGateway(r.gateway);
			profile.setPort(r.port);
			profile.setUsername(SECONDARY_USER);
			profile.setPassword(md5(r.apiPassword + r.serverHost));
			profile.setRemoteId(SECONDARY_REMOTE_ID);
			profile.setSplitTunneling(VpnProfile.SPLIT_TUNNELING_BLOCK_IPV6);
			if (alias != null)
			{
				profile.setCertificateAlias(alias);
			}
			result.add(profile);
			index++;
		}

		if (result.isEmpty())
		{
			throw new Exception("no servers found");
		}
		return result;
	}

	private ArrayList<SecondaryRow> collectSecondaryRows(JSONObject data)
	{
		ArrayList<SecondaryRow> rows = new ArrayList<>();
		HashSet<String> seen = new HashSet<>();

		JSONArray servers = data.optJSONArray("servers");
		if (servers == null)
		{
			return rows;
		}

		for (int i = 0; i < servers.length(); i++)
		{
			JSONObject server = servers.optJSONObject(i);
			if (server == null)
			{
				continue;
			}
			String apiPass = server.optString("password", "");
			String srvHost = server.optString("host", "");
			JSONObject loc = server.optJSONObject("location");
			String country = loc != null ? loc.optString("country", "") : "";
			String city = loc != null ? loc.optString("city", "") : "";
			int load = server.optInt("load", 999999);

			JSONArray services = server.optJSONArray("services");
			if (services == null)
			{
				continue;
			}

			for (int j = 0; j < services.length(); j++)
			{
				JSONObject svc = services.optJSONObject(j);
				if (svc == null || !svc.optString("id", "").startsWith("ikev2"))
				{
					continue;
				}
				String gateway = svc.optString("inbound_ip", srvHost).trim();
				if (gateway.isEmpty())
				{
					gateway = srvHost.trim();
				}
				if (gateway.isEmpty())
				{
					continue;
				}

				ArrayList<Integer> ports = new ArrayList<>();
				JSONArray pa = svc.optJSONArray("ports");
				if (pa != null)
				{
					for (int k = 0; k < pa.length(); k++)
					{
						int p = pa.optInt(k, 0);
						if (p > 0)
						{
							ports.add(p);
						}
					}
				}
				if (ports.isEmpty())
				{
					ports.add(4500);
				}

				for (int port : ports)
				{
					if (port < 1 || port > 65535)
					{
						continue;
					}
					if (!seen.add(gateway + ":" + port))
					{
						continue;
					}
					SecondaryRow r = new SecondaryRow();
					r.gateway = gateway;
					r.port = port;
					r.serverHost = srvHost.isEmpty() ? gateway : srvHost;
					r.apiPassword = apiPass;
					r.country = country;
					r.city = city;
					r.load = load;
					rows.add(r);
				}
			}
		}
		return rows;
	}

	private static JSONObject decryptBlob(byte[] blob) throws Exception
	{
		if (blob == null || blob.length < 33 || blob[0] != 0x10)
		{
			throw new Exception("unexpected server list format");
		}
		byte[] iv = Arrays.copyOfRange(blob, 1, 17);
		byte[] body = Arrays.copyOfRange(blob, 17, blob.length);
		if (body.length % 16 != 0)
		{
			throw new Exception("bad ciphertext length");
		}

		Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE,
			new SecretKeySpec(SECONDARY_AES_KEY, "AES"), new IvParameterSpec(iv));
		byte[] pt = cipher.doFinal(body);

		int len = pt.length;
		int pad = pt[len - 1] & 0xFF;
		if (pad >= 1 && pad <= 16 && pad <= len)
		{
			boolean valid = true;
			for (int i = len - pad; i < len; i++)
			{
				if ((pt[i] & 0xFF) != pad)
				{
					valid = false;
					break;
				}
			}
			if (valid)
			{
				len -= pad;
			}
		}

		Inflater inflater = new Inflater();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try
		{
			inflater.setInput(pt, 0, len);
			byte[] buf = new byte[8192];
			while (!inflater.finished())
			{
				int n = inflater.inflate(buf);
				if (n == 0 && (inflater.needsInput() || inflater.needsDictionary()))
				{
					break;
				}
				out.write(buf, 0, n);
			}
		}
		finally
		{
			inflater.end();
		}
		return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
	}

	private static byte[] httpGetBytes(String urlSpec) throws Exception
	{
		HttpURLConnection conn = (HttpURLConnection) new URL(urlSpec).openConnection();
		try
		{
			conn.setConnectTimeout(SECONDARY_TIMEOUT);
			conn.setReadTimeout(SECONDARY_TIMEOUT);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("User-Agent", "okhttp/4.12.0");
			conn.setRequestProperty("Accept", "*/*");

			int code = conn.getResponseCode();
			if (code != 200)
			{
				throw new Exception("HTTP " + code);
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (InputStream in = conn.getInputStream())
			{
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) != -1)
				{
					out.write(buf, 0, n);
				}
			}
			return out.toByteArray();
		}
		finally
		{
			conn.disconnect();
		}
	}

	private static String installCertificate(String certB64) throws Exception
	{
		byte[] der = Base64.decode(certB64, Base64.DEFAULT);
		CertificateFactory factory = CertificateFactory.getInstance("X.509");
		X509Certificate certificate = (X509Certificate) factory.generateCertificate(
			new ByteArrayInputStream(der));

		KeyStore store = KeyStore.getInstance("LocalCertificateStore");
		store.load(null, null);
		store.setCertificateEntry(null, certificate);
		TrustedCertificateManager.getInstance().reset();
		return store.getCertificateAlias(certificate);
	}

	private void collectGateways(JSONArray arr, ArrayList<JSONObject> out)
	{
		if (arr == null)
		{
			return;
		}
		for (int i = 0; i < arr.length(); i++)
		{
			JSONObject g = arr.optJSONObject(i);
			if (g == null)
			{
				continue;
			}
			String ip = g.optString("ip", "");
			int port = g.optInt("port", 0);
			if (ip.isEmpty() || port <= 0)
			{
				continue;
			}
			out.add(g);
		}
	}

	private VpnProfile buildProfile(JSONObject g, Registration reg, String location) throws Exception
	{
		VpnProfile profile = new VpnProfile();
		profile.setUUID(UUID.randomUUID());

		String ip = g.optString("ip", "");
		int port = g.optInt("port", 500);
		String loc = serverLoc(g, location);
		String flag = flagFor(loc);
		profile.setName(flag + " " + countryName(loc));
		profile.setVpnType(VpnType.fromIdentifier("ikev2-eap"));
		profile.setGateway(ip);
		if (port >= 1 && port <= 65535)
		{
			profile.setPort(port);
		}
		profile.setUsername(reg.username);
		profile.setPassword(reg.password);

		profile.setSplitTunneling(VpnProfile.SPLIT_TUNNELING_BLOCK_IPV6);

		String certB64 = certToBase64(g.optString("cert", ""));
		if (!certB64.isEmpty())
		{
			String alias = installCertificate(certB64);
			if (alias != null)
			{
				profile.setCertificateAlias(alias);
			}
		}
		return profile;
	}

	private Registration doRegister() throws Exception
	{
		String deviceId = md5(makeId());
		String rawUsername = "suvpn_" + md5(deviceId);
		String secret = makeSecret(rawUsername, deviceId);
		String sign = calculateSign();

		String[][] devices = {
			{"Google",  "Pixel 8",     "UP1A.231005.007"},
			{"Google",  "Pixel 8 Pro", "UP1A.231005.007"},
			{"Google",  "Pixel 7",     "TQ3A.230901.001"},
			{"Google",  "Pixel 6a",    "TQ3A.230805.001"},
			{"Samsung", "SM-S918B",    "UP1A.231005.007"},
			{"Samsung", "SM-S911B",    "UP1A.231005.007"},
			{"Samsung", "SM-A546B",    "UP1A.231005.007"},
			{"Samsung", "SM-A346B",    "UP1A.231005.007"},
			{"Xiaomi",  "22111317G",   "TKQ1.221114.001"},
			{"Xiaomi",  "2201123G",    "TKQ1.221114.001"},
			{"Xiaomi",  "23021RAAEG",  "UKQ1.230804.001"},
			{"Huawei",  "FOA-LX9",     "HUAWEIFOA-LX9"},
			{"Huawei",  "ELS-NX9",     "HUAWEIELS-NX9"},
			{"OnePlus", "CPH2449",     "TP1A.220905.001"},
			{"Oppo",    "CPH2525",     "TP1A.220905.001"},
			{"Vivo",    "V2230",       "TP1A.220905.001"},
			{"Realme",  "RMX3630",     "TP1A.220905.001"},
		};

		String[] div = devices[new java.util.Random().nextInt(devices.length)];
		StringBuilder body = new StringBuilder();
		appendParam(body, "username", rawUsername);
		appendParam(body, "platform", "a");
		appendParam(body, "channel", FIXED_CHANNEL);
		appendParam(body, "alias", FIXED_ALIAS);
		appendParam(body, "deviceId", deviceId);
		appendParam(body, "manufacturer", div[0]);
		appendParam(body, "model", div[1]);
		appendParam(body, "display", div[2]);
		appendParam(body, "imsi", deviceId);
		appendParam(body, "serial", "unknown");
		appendParam(body, "appVersionCode", APP_VERSION_CODE);
		appendParam(body, "appVersion", APP_VERSION);
		appendParam(body, "androidVersion", "34");
		appendParam(body, "secret", secret);
		appendParam(body, "package", PACKAGE);
		appendParam(body, "sign", sign);
		appendParam(body, "localGeo", FIXED_GEO);

		JSONObject resp = apiRequest("/api/register.json", body.toString());
		if (resp.optInt("status", -1) != 0)
		{
			throw new Exception("register failed: " + resp);
		}

		JSONObject user = resp.getJSONObject("data").getJSONObject("user");

		Registration reg = new Registration();
		reg.username = user.getString("username");
		reg.password = user.getString("password");
		reg.deviceId = deviceId;
		reg.sign     = sign;
		return reg;
	}

	private JSONObject doAcquire(Registration reg, String location) throws Exception
	{
		StringBuilder body = new StringBuilder();
		appendParam(body, "username", reg.username);
		appendParam(body, "password", reg.password);
		appendParam(body, "signInUsername", "");
		appendParam(body, "signInPassword", "");
		appendParam(body, "loginType", "0");
		appendParam(body, "platform", "a");
		appendParam(body, "location", location != null ? location : "auto");
		appendParam(body, "alias", FIXED_ALIAS);
		appendParam(body, "channel", FIXED_CHANNEL);
		appendParam(body, "appVersionCode", APP_VERSION_CODE);
		appendParam(body, "deviceId", reg.deviceId);
		appendParam(body, "imsi", reg.deviceId);
		appendParam(body, "package", PACKAGE);
		appendParam(body, "sign", reg.sign);
		appendParam(body, "localGeo", FIXED_GEO);
		appendParam(body, "tcp", "false");
		appendParam(body, "hy2", "false");
		appendParam(body, "tcpOnly", "false");
		appendParam(body, "configVersion", "0");

		JSONObject resp = apiRequest("/api/acquire.json", body.toString());
		return resp.optJSONObject("data") != null
			? resp.getJSONObject("data")
			: new JSONObject();
	}

	private JSONObject apiRequest(String path, String formBody) throws Exception
	{
		String lastError = null;
		for (String base : BASE_URLS)
		{
			try
			{
				String url = base.replaceAll("/$", "") + path;
				String response = httpPost(url, formBody);
				return new JSONObject(response);
			}
			catch (Exception e)
			{
				lastError = e.getMessage();
				Log.w(TAG, "request failed for " + base + ": " + e.getMessage());
			}
		}
		throw new Exception("All hosts failed: " + lastError);
	}

	private String httpPost(String urlSpec, String body) throws Exception
	{
		HttpURLConnection conn = (HttpURLConnection) new URL(urlSpec).openConnection();
		try
		{
			conn.setConnectTimeout(CONNECT_TIMEOUT);
			conn.setReadTimeout(READ_TIMEOUT);
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			conn.setRequestProperty("User-Agent", "okhttp/4.12.0");
			conn.setRequestProperty("Content-Type",
				"application/x-www-form-urlencoded");

			byte[] payload = body.getBytes(StandardCharsets.UTF_8);
			try (OutputStream os = conn.getOutputStream())
			{
				os.write(payload);
			}

			int code = conn.getResponseCode();
			InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			if (in != null)
			{
				byte[] buf = new byte[4096];
				int n;
				while ((n = in.read(buf)) != -1)
				{
					out.write(buf, 0, n);
				}
				in.close();
			}
			if (code != 200)
			{
				throw new Exception("HTTP " + code);
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		}
		finally
		{
			conn.disconnect();
		}
	}

	private static void appendParam(StringBuilder sb, String key, String value)
	{
		if (sb.length() > 0)
		{
			sb.append('&');
		}
		try
		{
			sb.append(URLEncoder.encode(key, "UTF-8"))
			  .append('=')
			  .append(URLEncoder.encode(value == null ? "" : value, "UTF-8"));
		}
		catch (Exception e)
		{
		}
	}

	private static String md5(String input) throws Exception
	{
		MessageDigest md = MessageDigest.getInstance("MD5");
		byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
		StringBuilder sb = new StringBuilder(digest.length * 2);
		for (byte b : digest)
		{
			sb.append(Character.forDigit((b >> 4) & 0xF, 16));
			sb.append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}

	private static String makeId()
	{
		byte[] bytes = new byte[16];
		new SecureRandom().nextBytes(bytes);
		StringBuilder sb = new StringBuilder(32);
		for (byte b : bytes)
		{
			sb.append(Character.forDigit((b >> 4) & 0xF, 16));
			sb.append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}

	private static String calculateSign() throws Exception
	{
		return md5(APP_SIGNATURE_SHA256.replace(":", "").toLowerCase());
	}

	private static String makeSecret(String username, String deviceId) throws Exception
	{
		return md5(username + deviceId + SECRET_SALT);
	}

	private static String certToBase64(String cert)
	{
		if (cert == null || cert.isEmpty() || "none".equals(cert))
		{
			return "";
		}
		return cert.replace("-----BEGIN CERTIFICATE-----", "")
		           .replace("-----END CERTIFICATE-----", "")
		           .replaceAll("\\s+", "");
	}

	private static String flagFor(String code)
	{
		if (code == null)
		{
			return "🏳️";
		}
		switch (code.toLowerCase())
		{
			case "auto": return "🌐";
			case "nl":   return "🇳🇱";
			case "de":   return "🇩🇪";
			case "fr":   return "🇫🇷";
			case "uk":
			case "gb":   return "🇬🇧";
			case "us":   return "🇺🇸";
			case "ca":   return "🇨🇦";
			case "pl":   return "🇵🇱";
			case "sgp":
			case "sg":   return "🇸🇬";
			case "jp":   return "🇯🇵";
			case "in":   return "🇮🇳";
			case "es":   return "🇪🇸";
			case "it":   return "🇮🇹";
			case "cz":   return "🇨🇿";
			case "at":   return "🇦🇹";
			case "be":   return "🇧🇪";
			case "ch":   return "🇨🇭";
			case "se":   return "🇸🇪";
			case "no":   return "🇳🇴";
			case "fi":   return "🇫🇮";
			case "tr":   return "🇹🇷";
			case "ru":   return "🇷🇺";
			case "ua":   return "🇺🇦";
			case "kr":   return "🇰🇷";
			case "hk":   return "🇭🇰";
			case "tw":   return "🇹🇼";
			case "au":   return "🇦🇺";
			case "br":   return "🇧🇷";
			default:     return "🏳️";
		}
	}

	private static String countryName(String code)
	{
		if (code == null || code.isEmpty()) return "Auto";
		switch (code.toLowerCase())
		{
			case "auto": return "Auto";
			case "nl":   return "Netherlands";
			case "de":   return "Germany";
			case "fr":   return "France";
			case "uk":
			case "gb":   return "United Kingdom";
			case "us":   return "United States";
			case "ca":   return "Canada";
			case "pl":   return "Poland";
			case "sgp":
			case "sg":   return "Singapore";
			case "jp":   return "Japan";
			case "in":   return "India";
			case "es":   return "Spain";
			case "it":   return "Italia";
			case "cz":   return "Czech Republic";
			case "at":   return "Austria";
			case "be":   return "Belgium";
			case "ch":   return "Switzerland";
			case "se":   return "Sweden";
			case "no":   return "Norway";
			case "fi":   return "Finland";
			case "tr":   return "Turkey";
			case "ru":   return "Russia";
			case "ua":   return "Ukraine";
			case "kr":   return "South Korea";
			case "hk":   return "Hong Kong";
			case "tw":   return "Taiwan";
			case "au":   return "Australia";
			case "br":   return "Brazil";
			default:     return code.toUpperCase();
		}
	}

	private static String serverLoc(JSONObject g, String fallback)
	{
		String loc = g.optString("location", "").toLowerCase();
		if (!loc.isEmpty() && !"auto".equals(loc))
		{
			return loc;
		}
		if (fallback != null && !fallback.isEmpty() && !"auto".equals(fallback))
		{
			return fallback.toLowerCase();
		}
		return "auto";
	}

	private static class Registration
	{
		String username;
		String password;
		String deviceId;
		String sign;
	}

	private static class SecondaryRow
	{
		String gateway;
		int port;
		String serverHost;
		String apiPassword;
		String country;
		String city;
		int load;
	}
}
