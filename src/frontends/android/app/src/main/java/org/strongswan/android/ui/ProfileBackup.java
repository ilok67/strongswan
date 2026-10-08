package org.strongswan.android.ui;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.data.VpnProfileDataSource;
import org.strongswan.android.data.VpnProfileSource;
import org.strongswan.android.data.VpnType;
import org.strongswan.android.security.LocalCertificateStore;
import org.strongswan.android.utils.Constants;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public final class ProfileBackup
{
	private ProfileBackup() {}

	public static int export(Context context, Uri uri) throws Exception
	{
		VpnProfileDataSource db = new VpnProfileSource(context);
		db.open();
		try
		{
			List<VpnProfile> all = db.getAllVpnProfiles();
			LocalCertificateStore certs = new LocalCertificateStore();
			JSONArray arr = new JSONArray();
			for (VpnProfile p : all)
			{
				JSONObject o = new JSONObject();
				o.put("uuid", p.getUUID().toString());
				o.put("name", p.getName());
				o.put("gateway", p.getGateway());
				if (p.getPort() != null) o.put("port", p.getPort());
				o.put("vpn_type", p.getVpnType().getIdentifier());
				o.put("username", p.getUsername());
				o.put("password", p.getPassword());
				if (p.getSplitTunneling() != null) o.put("split", p.getSplitTunneling());
				String alias = p.getCertificateAlias();
				if (alias != null)
				{
					X509Certificate cert = certs.getCertificate(alias);
					if (cert != null)
					{
						o.put("cert", Base64.encodeToString(cert.getEncoded(), Base64.NO_WRAP));
					}
				}
				arr.put(o);
			}
			JSONObject root = new JSONObject();
			root.put("version", 1);
			root.put("profiles", arr);
			try (OutputStream out = context.getContentResolver().openOutputStream(uri))
			{
				out.write(root.toString().getBytes(StandardCharsets.UTF_8));
			}
			return all.size();
		}
		finally
		{
			db.close();
		}
	}

	public static int restore(Context context, Uri uri) throws Exception
	{
		String json;
		try (InputStream in = context.getContentResolver().openInputStream(uri))
		{
			byte[] buf = new byte[in.available()];
			int n = 0;
			while (n < buf.length)
			{
				int r = in.read(buf, n, buf.length - n);
				if (r < 0) break;
				n += r;
			}
			json = new String(buf, 0, n, StandardCharsets.UTF_8);
		}
		JSONArray arr = new JSONObject(json).getJSONArray("profiles");
		VpnProfileDataSource db = new VpnProfileSource(context);
		db.open();
		LocalCertificateStore certs = new LocalCertificateStore();
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		java.util.ArrayList<String> uuids = new java.util.ArrayList<>();
		try
		{
			for (int i = 0; i < arr.length(); i++)
			{
				JSONObject o = arr.getJSONObject(i);
				VpnProfile p = new VpnProfile();
				p.setUUID(UUID.fromString(o.getString("uuid")));
				p.setName(o.optString("name", ""));
				p.setGateway(o.optString("gateway", ""));
				if (o.has("port")) p.setPort(o.getInt("port"));
				p.setVpnType(VpnType.fromIdentifier(o.optString("vpn_type", "ikev2-eap")));
				p.setUsername(o.optString("username", null));
				p.setPassword(o.optString("password", null));
				if (o.has("split")) p.setSplitTunneling(o.getInt("split"));
				if (o.has("cert"))
				{
					byte[] der = Base64.decode(o.getString("cert"), Base64.DEFAULT);
					X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
					certs.addCertificate(cert);
					p.setCertificateAlias(certs.getCertificateAlias(cert));
				}
				VpnProfile existing = db.getVpnProfile(p.getUUID());
				if (existing != null)
				{
					p.setDataSource(existing.getDataSource());
					db.updateVpnProfile(p);
				}
				else
				{
					db.insertProfile(p);
				}
				uuids.add(p.getUUID().toString());
			}
		}
		finally
		{
			db.close();
		}
		if (!uuids.isEmpty())
		{
			android.content.Intent intent = new android.content.Intent(Constants.VPN_PROFILES_CHANGED);
			intent.putExtra(Constants.VPN_PROFILES_MULTIPLE, uuids.toArray(new String[0]));
			LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
		}
		return uuids.size();
	}
}
