package org.strongswan.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.Inflater;
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

	private static final String APP_VERSION          = "3.1.6";
	private static final String APP_VERSION_CODE     = "146";
	private static final String PACKAGE              = "com.jrzheng.supervpnfree";
	private static final String SECRET_SALT          = "2y6w498L2I";
	private static final String APP_SIGNATURE_SHA256 =
		"5200D5E89B55D901033896DB249503EFB28479279158101A6A0D3BEA4C7AB906";

	private static final String FIXED_ALIAS   = "en";
	private static final String FIXED_CHANNEL = "play";
	private static final String FIXED_GEO     = "IR";

	private static final int CONNECT_TIMEOUT = 5000;
	private static final int READ_TIMEOUT    = 8000;

	private static final String[][] DEVICES = {
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

	private static final String SPECIAL_CODE = "special";
	private static final String V2RAY_CODE   = "v2ray";

	private static final String SECONDARY_API =
		"https://prod-api.mobilejump.mobi/be-data/v7/server_list";
	private static final byte[] SECONDARY_AES_KEY =
		"01fb3864e37eb5d4ada3a50a1f1a373e".getBytes(StandardCharsets.US_ASCII);
	private static final String SECONDARY_USER      = "myvpn";
	private static final String SECONDARY_REMOTE_ID = "node.mobilejump.mobi";
	private static final String[] SECONDARY_REGIONS = {"IR", "US", "EU", "RU", "DE", "TR"};
	private static final String[] VIP_VALUES        = {"true", "false"};
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
						Toast.makeText(this, getString(R.string.fetch_servers_done, count),
							Toast.LENGTH_LONG).show();
						finish();
					});
				}
				catch (Exception e)
				{
					Log.e(TAG, "import failed", e);
					String message = e.getMessage() != null ? e.getMessage() : e.toString();
					runOnUiThread(() -> {
						progress.setVisibility(View.GONE);
						button.setEnabled(true);
						spinner.setEnabled(true);
						status.setText(getString(R.string.fetch_servers_failed, message));
					});
				}
			});
		});
	}

	private int importProfiles(String location) throws Exception
	{
		if (V2RAY_CODE.equals(location))
		{
			return importV2RayProfiles();
		}
		return saveProfiles(SPECIAL_CODE.equals(location)
			? fetchSecondaryProfiles()
			: fetchPrimaryProfiles(location));
	}

	private int importV2RayProfiles() throws Exception
	{
		List<String> links = V2RaySecondaryBuilder.buildLinks(
			fetchServerList(), SECONDARY_MAX_PROFILES, "all");
		if (links.isEmpty())
		{
			throw new Exception("no v2ray servers found try again");
		}

		V2RayProfileStore store = new V2RayProfileStore(this);
		List<String> existing = store.load();
		HashSet<String> known = new HashSet<>(existing);

		int added = 0;
		for (String link : links)
		{
			if (link != null && known.add(link))
			{
				existing.add(link);
				added++;
			}
		}

		if (added > 0)
		{
			store.save(existing);
			Intent intent = new Intent("org.strongswan.android.V2RAY_PROFILES_CHANGED");
			intent.setPackage(getPackageName());
			LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		}
		return added;
	}

	private int saveProfiles(List<VpnProfile> profiles)
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
			Intent intent = new Intent(Constants.VPN_PROFILES_CHANGED);
			intent.putExtra(Constants.VPN_PROFILES_MULTIPLE, uuids.toArray(new String[0]));
			LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		}
		return uuids.size();
	}

	private static VpnProfile newProfile(UUID id, String name, String gateway, int port,
										 String username, String password, String certAlias)
	{
		VpnProfile profile = new VpnProfile();
		profile.setUUID(id);
		profile.setName(name);
		profile.setVpnType(VpnType.fromIdentifier("ikev2-eap"));
		profile.setGateway(gateway);
		if (port >= 1 && port <= 65535)
		{
			profile.setPort(port);
		}
		profile.setUsername(username);
		profile.setPassword(password);
		profile.setSplitTunneling(VpnProfile.SPLIT_TUNNELING_BLOCK_IPV6);
		if (certAlias != null)
		{
			profile.setCertificateAlias(certAlias);
		}
		return profile;
	}

	private static List<VpnProfile> fetchPrimaryProfiles(String location) throws Exception
	{
		String deviceId = md5(randomHex());
		String rawUsername = "suvpn_" + md5(deviceId);
		String sign = md5(APP_SIGNATURE_SHA256.replace(":", "").toLowerCase());
		String[] device = DEVICES[ThreadLocalRandom.current().nextInt(DEVICES.length)];

		JSONObject registration = apiRequest("/api/register.json", form(
			"username", rawUsername,
			"platform", "a",
			"channel", FIXED_CHANNEL,
			"alias", FIXED_ALIAS,
			"deviceId", deviceId,
			"manufacturer", device[0],
			"model", device[1],
			"display", device[2],
			"imsi", deviceId,
			"serial", "unknown",
			"appVersionCode", APP_VERSION_CODE,
			"appVersion", APP_VERSION,
			"androidVersion", "34",
			"secret", md5(rawUsername + deviceId + SECRET_SALT),
			"package", PACKAGE,
			"sign", sign,
			"localGeo", FIXED_GEO));
		if (registration.optInt("status", -1) != 0)
		{
			throw new Exception("register failed: " + registration);
		}
		JSONObject user = registration.getJSONObject("data").getJSONObject("user");
		String username = user.getString("username");
		String password = user.getString("password");

		JSONObject response = apiRequest("/api/acquire.json", form(
			"username", username,
			"password", password,
			"signInUsername", "",
			"signInPassword", "",
			"loginType", "0",
			"platform", "a",
			"location", location,
			"alias", FIXED_ALIAS,
			"channel", FIXED_CHANNEL,
			"appVersionCode", APP_VERSION_CODE,
			"deviceId", deviceId,
			"imsi", deviceId,
			"package", PACKAGE,
			"sign", sign,
			"localGeo", FIXED_GEO,
			"tcp", "false",
			"hy2", "false",
			"tcpOnly", "false",
			"configVersion", "0"));
		JSONObject data = response.optJSONObject("data");
		if (data == null)
		{
			data = new JSONObject();
		}

		HashMap<String, String> aliases = new HashMap<>();
		ArrayList<VpnProfile> result = new ArrayList<>();
		for (String key : new String[]{"gateways", "relayGateways"})
		{
			JSONArray gateways = data.optJSONArray(key);
			for (int i = 0; gateways != null && i < gateways.length(); i++)
			{
				JSONObject g = gateways.optJSONObject(i);
				if (g == null || g.optString("ip", "").isEmpty() || g.optInt("port", 0) <= 0)
				{
					continue;
				}

				String cert = g.optString("cert", "");
				String alias = null;
				if (!cert.isEmpty() && !"none".equals(cert))
				{
					if (!aliases.containsKey(cert))
					{
						aliases.put(cert, installCertificate(cert));
					}
					alias = aliases.get(cert);
				}

				String loc = g.optString("location", "");
				if (loc.isEmpty() || "auto".equalsIgnoreCase(loc))
				{
					loc = location;
				}
				result.add(newProfile(UUID.randomUUID(), flagFor(loc) + " " + countryName(loc),
					g.optString("ip"), g.optInt("port", 500), username, password, alias));
			}
		}
		return result;
	}

	private static List<VpnProfile> fetchSecondaryProfiles() throws Exception
	{
		ArrayList<SecondaryRow> rows = collectSecondaryRows(fetchServerList());
		Collections.sort(rows, (a, b) -> Integer.compare(a.load, b.load));

		String alias = installCertificate(SECONDARY_CA);

		ArrayList<VpnProfile> result = new ArrayList<>();
		for (SecondaryRow r : rows.subList(0, Math.min(rows.size(), SECONDARY_MAX_PROFILES)))
		{
			String cc = r.country.toUpperCase();
			String name = flagFor(cc) + " " + (result.size() + 1) + " "
				+ (cc.isEmpty() ? "Unknown" : countryName(cc));
			if (!r.city.isEmpty())
			{
				name += " – " + r.city;
			}
			name += " :" + r.port;

			VpnProfile profile = newProfile(
				UUID.nameUUIDFromBytes(("secondary:" + r.gateway + ":" + r.port).getBytes(StandardCharsets.UTF_8)),
				name, r.gateway, r.port, SECONDARY_USER, md5(r.apiPassword + r.serverHost), alias);
			profile.setRemoteId(SECONDARY_REMOTE_ID);
			result.add(profile);
		}

		if (result.isEmpty())
		{
			throw new Exception("no servers found try again");
		}
		return result;
	}

	private static ArrayList<SecondaryRow> collectSecondaryRows(JSONObject data)
	{
		ArrayList<SecondaryRow> rows = new ArrayList<>();
		HashSet<String> seen = new HashSet<>();

		JSONArray servers = data.optJSONArray("servers");
		for (int i = 0; servers != null && i < servers.length(); i++)
		{
			JSONObject server = servers.optJSONObject(i);
			JSONArray services = server != null ? server.optJSONArray("services") : null;
			if (services == null)
			{
				continue;
			}

			String srvHost = server.optString("host", "");
			JSONObject loc = server.optJSONObject("location");
			String country = loc != null ? loc.optString("country", "") : "";
			String city = loc != null ? loc.optString("city", "") : "";

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
				for (int k = 0; pa != null && k < pa.length(); k++)
				{
					int p = pa.optInt(k, 0);
					if (p > 0)
					{
						ports.add(p);
					}
				}
				if (ports.isEmpty())
				{
					ports.add(4500);
				}

				for (int port : ports)
				{
					if (port <= 65535 && seen.add(gateway + ":" + port))
					{
						rows.add(new SecondaryRow(gateway, port, srvHost.isEmpty() ? gateway : srvHost,
							server.optString("password", ""), country, city, server.optInt("load", 999999)));
					}
				}
			}
		}
		return rows;
	}

	private static JSONObject fetchServerList() throws Exception
	{
		String url = SECONDARY_API
			+ "?platform=android&vip=" + pick(VIP_VALUES)
			+ "&region=" + pick(SECONDARY_REGIONS) + "&app=";
		return decryptBlob(http(url, "GET", null, SECONDARY_TIMEOUT, SECONDARY_TIMEOUT));
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

	private static String installCertificate(String certificate) throws Exception
	{
		String base64 = certificate
			.replace("-----BEGIN CERTIFICATE-----", "")
			.replace("-----END CERTIFICATE-----", "")
			.replaceAll("\\s+", "");
		byte[] der = Base64.decode(base64, Base64.DEFAULT);
		X509Certificate x509 = (X509Certificate) CertificateFactory.getInstance("X.509")
			.generateCertificate(new ByteArrayInputStream(der));

		KeyStore store = KeyStore.getInstance("LocalCertificateStore");
		store.load(null, null);
		store.setCertificateEntry(null, x509);
		TrustedCertificateManager.getInstance().reset();
		return store.getCertificateAlias(x509);
	}

	private static JSONObject apiRequest(String path, byte[] body) throws Exception
	{
		String lastError = null;
		for (String base : BASE_URLS)
		{
			try
			{
				byte[] response = http(base.replaceAll("/$", "") + path, "POST", body,
					CONNECT_TIMEOUT, READ_TIMEOUT);
				return new JSONObject(new String(response, StandardCharsets.UTF_8));
			}
			catch (Exception e)
			{
				lastError = e.getMessage();
				Log.w(TAG, "request failed for " + base + ": " + lastError);
			}
		}
		throw new Exception("All hosts failed: " + lastError);
	}

	private static byte[] http(String urlSpec, String method, byte[] body,
							   int connectTimeout, int readTimeout) throws Exception
	{
		HttpURLConnection conn = (HttpURLConnection) new URL(urlSpec).openConnection();
		try
		{
			conn.setConnectTimeout(connectTimeout);
			conn.setReadTimeout(readTimeout);
			conn.setRequestMethod(method);
			conn.setRequestProperty("User-Agent", "okhttp/4.12.0");
			conn.setRequestProperty("Accept", "*/*");
			if (body != null)
			{
				conn.setDoOutput(true);
				conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				try (OutputStream os = conn.getOutputStream())
				{
					os.write(body);
				}
			}

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

	private static byte[] form(String... keyValues) throws Exception
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i + 1 < keyValues.length; i += 2)
		{
			if (sb.length() > 0)
			{
				sb.append('&');
			}
			sb.append(URLEncoder.encode(keyValues[i], "UTF-8")).append('=')
			  .append(URLEncoder.encode(keyValues[i + 1] == null ? "" : keyValues[i + 1], "UTF-8"));
		}
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String pick(String[] values)
	{
		return values[ThreadLocalRandom.current().nextInt(values.length)];
	}

	private static String hex(byte[] bytes)
	{
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes)
		{
			sb.append(Character.forDigit((b >> 4) & 0xF, 16));
			sb.append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}

	private static String md5(String input) throws Exception
	{
		return hex(MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8)));
	}

	private static String randomHex()
	{
		byte[] bytes = new byte[16];
		new SecureRandom().nextBytes(bytes);
		return hex(bytes);
	}

	private static String normalizeCountry(String code)
	{
		String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
		switch (c)
		{
			case "UK":  return "GB";
			case "SGP": return "SG";
			default:    return c;
		}
	}

	private static String flagFor(String code)
	{
		String c = normalizeCountry(code);
		if (!c.matches("[A-Z]{2}"))
		{
			return "🌐";
		}
		int base = 0x1F1E6 - 'A';
		return new String(Character.toChars(base + c.charAt(0)))
			+ new String(Character.toChars(base + c.charAt(1)));
	}

	private static String countryName(String code)
	{
		String c = normalizeCountry(code);
		if (!c.matches("[A-Z]{2}"))
		{
			return c.isEmpty() || "AUTO".equals(c) ? "Auto" : c;
		}
		String name = new Locale("", c).getDisplayCountry(Locale.ENGLISH);
		return name.isEmpty() ? c : name;
	}

	private static class SecondaryRow
	{
		final String gateway;
		final int port;
		final String serverHost;
		final String apiPassword;
		final String country;
		final String city;
		final int load;

		SecondaryRow(String gateway, int port, String serverHost, String apiPassword,
					 String country, String city, int load)
		{
			this.gateway = gateway;
			this.port = port;
			this.serverHost = serverHost;
			this.apiPassword = apiPassword;
			this.country = country;
			this.city = city;
			this.load = load;
		}
	}
}
