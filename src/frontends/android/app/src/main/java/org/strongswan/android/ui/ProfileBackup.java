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
import org.strongswan.android.v2ray.V2RayProfileStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public final class ProfileBackup
{
	private static final String V2RAY_CHANGED = "org.strongswan.android.V2RAY_PROFILES_CHANGED";

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
				String remoteId = p.getRemoteId();
				if (remoteId != null && !remoteId.isEmpty())
				{
					o.put("remote_id", remoteId);
				}
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
			JSONArray links = new JSONArray();
			for (String link : new V2RayProfileStore(context).load())
			{
				if (link != null && !link.isEmpty())
				{
					links.put(link);
				}
			}
			JSONObject root = new JSONObject();
			root.put("version", 2);
			root.put("profiles", arr);
			root.put("links", links);
			try (OutputStream out = context.getContentResolver().openOutputStream(uri))
			{
				out.write(root.toString().getBytes(StandardCharsets.UTF_8));
			}
			return all.size() + links.length();
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
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) != -1)
			{
				buffer.write(buf, 0, n);
			}
			json = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
		}
		JSONObject root = new JSONObject(json);
		int ike = restoreIke(context, root.optJSONArray("profiles"));
		int box = restoreLinks(context, root.optJSONArray("links"));
		return ike + box;
	}

	private static int restoreIke(Context context, JSONArray arr)
	{
		if (arr == null)
		{
			return 0;
		}
		VpnProfileDataSource db = new VpnProfileSource(context);
		db.open();
		LocalCertificateStore certs = new LocalCertificateStore();
		CertificateFactory cf;
		try
		{
			cf = CertificateFactory.getInstance("X.509");
		}
		catch (Exception e)
		{
			db.close();
			return 0;
		}
		ArrayList<String> uuids = new ArrayList<>();
		try
		{
			for (int i = 0; i < arr.length(); i++)
			{
				try
				{
					JSONObject o = arr.getJSONObject(i);
					VpnProfile p = new VpnProfile();
					String uuidStr = o.optString("uuid", "");
					p.setUUID(uuidStr.isEmpty() ? UUID.randomUUID() : UUID.fromString(uuidStr));
					p.setName(o.optString("name", ""));
					p.setGateway(o.optString("gateway", ""));
					if (o.has("port")) p.setPort(o.getInt("port"));
					p.setVpnType(VpnType.fromIdentifier(o.optString("vpn_type", "ikev2-eap")));
					p.setUsername(o.optString("username", null));
					p.setPassword(o.optString("password", null));
					if (o.has("split")) p.setSplitTunneling(o.getInt("split"));
					String remoteId = o.optString("remote_id", "");
					if (!remoteId.isEmpty())
					{
						p.setRemoteId(remoteId);
					}
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
				catch (Exception e)
				{
					android.util.Log.w("ProfileBackup", "skipped profile " + i + ": " + e.getMessage());
				}
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

	private static int restoreLinks(Context context, JSONArray arr)
	{
		if (arr == null || arr.length() == 0)
		{
			return 0;
		}
		V2RayProfileStore store = new V2RayProfileStore(context);
		List<String> existing = store.load();
		HashSet<String> seen = new HashSet<>(existing);
		int added = 0;
		for (int i = 0; i < arr.length(); i++)
		{
			String link = arr.optString(i, "").trim();
			if (!link.isEmpty() && seen.add(link))
			{
				existing.add(link);
				added++;
			}
		}
		if (added > 0)
		{
			store.save(existing);
			LocalBroadcastManager.getInstance(context).sendBroadcast(new android.content.Intent(V2RAY_CHANGED));
		}
		return added;
	}
}
