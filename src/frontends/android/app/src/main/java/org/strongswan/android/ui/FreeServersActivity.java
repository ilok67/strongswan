package org.strongswan.android.ui;

import android.os.Bundle;
import android.util.Base64;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;

public class FreeServersActivity extends AppCompatActivity
{
	private static final String PROFILES_URL =
		"https://suvpnex.iloktest6.workers.dev/api/profiles?location=auto";

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
				e.printStackTrace();
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
		JSONArray arr = new JSONArray(download(PROFILES_URL));
		VpnProfileDataSource dataSource = new VpnProfileSource(this);
		dataSource.open();

		ArrayList<String> uuids = new ArrayList<>();
		try
		{
			for (int i = 0; i < arr.length(); i++)
			{
				VpnProfile profile = parseSswan(arr.getJSONObject(i));
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
			android.content.Intent intent = new android.content.Intent(Constants.VPN_PROFILES_CHANGED);
			intent.putExtra(Constants.VPN_PROFILES_MULTIPLE, uuids.toArray(new String[0]));
			LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		}
		return uuids.size();
	}

	private VpnProfile parseSswan(JSONObject obj) throws Exception
	{
		JSONObject remote = obj.optJSONObject("remote");
		if (remote == null)
		{
			return null;
		}

		VpnProfile profile = new VpnProfile();
		try
		{
			profile.setUUID(UUID.fromString(obj.getString("uuid")));
		}
		catch (Exception e)
		{
			profile.setUUID(UUID.randomUUID());
		}

		profile.setName(obj.optString("name", remote.getString("addr")));
		profile.setVpnType(VpnType.fromIdentifier(obj.optString("type", "ikev2-eap")));
		profile.setGateway(remote.getString("addr"));

		int port = remote.optInt("port", 0);
		if (port >= 1 && port <= 65535)
		{
			profile.setPort(port);
		}

		JSONObject local = obj.optJSONObject("local");
		if (local != null)
		{
			profile.setUsername(local.optString("eap_id", null));
			profile.setPassword(local.optString("shared_secret", null));
		}
          JSONObject split = obj.optJSONObject("split-tunneling");
if (split != null)
{
    int st = 0;
    if (split.optBoolean("block-ipv4", false))
    {
        st |= VpnProfile.SPLIT_TUNNELING_BLOCK_IPV4;
    }
    if (split.optBoolean("block-ipv6", false))
    {
        st |= VpnProfile.SPLIT_TUNNELING_BLOCK_IPV6;
    }
    if (st != 0)
    {
        profile.setSplitTunneling(st);
    }
}
		String certB64 = remote.optString("cert", null);
		if (certB64 != null && !certB64.isEmpty())
		{
			byte[] der = Base64.decode(certB64.replaceAll("\\s+", ""), Base64.DEFAULT);
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

	private String download(String urlSpec) throws Exception
	{
		HttpURLConnection conn = (HttpURLConnection) new URL(urlSpec).openConnection();
		conn.setConnectTimeout(20000);
		conn.setReadTimeout(30000);
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Accept", "application/json");

		int code = conn.getResponseCode();
		InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		int n;
		while ((n = in.read(buf)) != -1)
		{
			out.write(buf, 0, n);
		}
		in.close();
		conn.disconnect();

		if (code != 200)
		{
			throw new Exception("HTTP " + code);
		}
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}
}
