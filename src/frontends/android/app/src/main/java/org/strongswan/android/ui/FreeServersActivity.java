package org.strongswan.android.ui;

import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
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
import java.util.UUID;
import java.util.concurrent.Executors;

public class FreeServersActivity extends AppCompatActivity
{
	private static final String TAG = "FreeServersActivity";

	
	private static final String[] BASE_URLS = {
		"https://www.supervpn.cc",
		"https://d3nrxxff6afmoi.cloudfront.net",
		"https://api.ttsw557.app",
		"https://api.kingyt6761.cc",
		"https://api.wuli009821.net",
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

	private static final int CONNECT_TIMEOUT = 20000;
	private static final int READ_TIMEOUT    = 30000;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Toast.makeText(this, R.string.fetch_servers_loading, Toast.LENGTH_SHORT).show();

		Executors.newSingleThreadExecutor().execute(() -> {
			try
			{
				int count = importProfiles();
				runOnUiThread(() -> {
					Toast.makeText(this,
						getString(R.string.fetch_servers_done, count),
						Toast.LENGTH_LONG).show();
					finish();
				});
			}
			catch (Exception e)
			{
				Log.e(TAG, "import failed", e);
				runOnUiThread(() -> {
					Toast.makeText(this,
						getString(R.string.fetch_servers_failed, e.getMessage()),
						Toast.LENGTH_LONG).show();
					finish();
				});
			}
		});
	}

	private int importProfiles() throws Exception
	{
		
		Registration reg = doRegister();

		
		JSONObject data = doAcquire(reg, "auto");

		
		ArrayList<JSONObject> gateways = new ArrayList<>();
		collectGateways(data.optJSONArray("gateways"), gateways);
		collectGateways(data.optJSONArray("relayGateways"), gateways);

		
		VpnProfileDataSource dataSource = new VpnProfileSource(this);
		dataSource.open();

		ArrayList<String> uuids = new ArrayList<>();
		try
		{
			for (JSONObject g : gateways)
			{
				VpnProfile profile = buildProfile(g, reg);
				if (profile == null)
				{
					continue;
				}
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

	private VpnProfile buildProfile(JSONObject g, Registration reg) throws Exception
	{
		VpnProfile profile = new VpnProfile();
		profile.setUUID(UUID.randomUUID());

		String ip = g.optString("ip", "");
		int port = g.optInt("port", 500);

		String loc = serverLoc(g, "auto");
		String flag = flagFor(loc);

		profile.setName(flag + " " + ip + ":" + port);
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
			byte[] der = Base64.decode(certB64, Base64.DEFAULT);
			CertificateFactory factory = CertificateFactory.getInstance("X.509");
			X509Certificate certificate = (X509Certificate) factory.generateCertificate(
				new ByteArrayInputStream(der));

			KeyStore store = KeyStore.getInstance("LocalCertificateStore");
			store.load(null, null);
			store.setCertificateEntry(null, certificate);
			TrustedCertificateManager.getInstance().reset();
			String alias = store.getCertificateAlias(certificate);
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

	StringBuilder body = new StringBuilder();
	appendParam(body, "username", rawUsername);
	appendParam(body, "platform", "a");
	appendParam(body, "channel", FIXED_CHANNEL);
	appendParam(body, "alias", FIXED_ALIAS);
	appendParam(body, "deviceId", deviceId);
	appendParam(body, "manufacturer", "Google");
	appendParam(body, "model", "Pixel 8");
	appendParam(body, "display", "UP1A.231005.007");
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
			case "uk":   return "🇬🇧";
			case "us":   return "🇺🇸";
			case "ca":   return "🇨🇦";
			case "pl":   return "🇵🇱";
			case "sgp":  return "🇸🇬";
			case "jp":   return "🇯🇵";
			case "in":   return "🇮🇳";
			case "es":   return "🇪🇸";
			case "it":   return "🇮🇹";
			case "cz":   return "🇨🇿";
			default:     return "🏳️";
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
}
