package org.strongswan.android.awg;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;
import java.util.zip.Inflater;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AmneziaGateway
{
	private static final String GATEWAY = "http://gw.amnezia.org";
	private static final String APP_VERSION = "5.1.1";
	private static final String PUBLIC_KEY_B64 =
		"MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAj5mxl/4DL3Sk89ntxs5G" +
		"X3JawGQWIoq6rvNkOzNGuNgedNS2+pi6hZl3Izl1Io9om4KiUlMT6mgLO1hTr9q+" +
		"s7CYhlvroFA7ErucF+9L+7FCt0Igi0kIK/R2/vxd/2HaUrorn/aSvvutkYwbfxqW" +
		"SwtzE+RuBeDWGvEt937OW0oqYONPYv9E4T56Dz/EZ6v2t8ejAnKLbGD/GocMmipK" +
		"7etFSiSMAB2RmaztqTq4NleBepfO80XpYlW9pCSXuHcE8wxHczkzxsbyMAMsG/K3" +
		"vUQY6qPtohqqzSSBwa/8u2ptNHBeor7l7DdYXeR/Nqcc4z92VUkZ5lOVR4evkS5V" +
		"/wQqp5tnOJEj3NjUhEhXFoNEapbZd1bh6iQoUk7jC1TdvKJ/nPKGZAsHRpr0rNKz" +
		"fx/N/Oo6lr2yh/+ps6VxTkbPmB6E85WOO3UvjImZUY0XQdBjWle/4iJLdEC77Nr0" +
		"jXhdgeypucy6jkB6iBHMeVMlrNMEV7UxoBR/cCNx55zu/8sml5ByiDvCDT7sRomN" +
		"NgVt5S/FaVjYuzFUifJ12ToChXFgESKFmuso7WluEaWvMIGREdrMrKQKHfYLOzWF" +
		"2B5ZJDqw4o03fU4J/6rw61M1b+rjVpXMjPnzc2A+RgcjTvXv955gfZkwe4lt5wk/" +
		"3j8zMVo3+zLrMTAaEeIUM0UCAwEAAQ==";

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final BigInteger P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
	private static final BigInteger A24 = BigInteger.valueOf(121665);

	public static class Service
	{
		public String type;
		public boolean available;
		public String protocol;
		public String name;
	}

	public static class Services
	{
		public String countryCode = "";
		public final java.util.List<Service> list = new java.util.ArrayList<>();
	}

	public static class Pending
	{
		public String privateKey = "";
		public String publicKey = "";
		public String country = "";
		public String protocol = "awg";
		public String captchaId = "";
		public byte[] captchaImage;
	}

	public static class ConfigResult
	{
		public JSONObject config;
		public Pending pending;
		public String message = "";
		public int httpStatus;
	}

	public static class GatewayException extends Exception
	{
		public GatewayException(String message)
		{
			super(message);
		}

		public GatewayException(String message, Throwable cause)
		{
			super(message, cause);
		}
	}

	public static Services listServices() throws GatewayException
	{
		JSONObject data = post("/v1/services", basePayload());
		Services result = new Services();
		result.countryCode = data.optString("user_country_code", "");
		JSONArray arr = data.optJSONArray("services");
		if (arr != null)
		{
			for (int i = 0; i < arr.length(); i++)
			{
				JSONObject o = arr.optJSONObject(i);
				if (o == null)
				{
					continue;
				}
				Service s = new Service();
				s.type = o.optString("service_type", "");
				s.available = o.optBoolean("is_available", false);
				s.protocol = o.optString("service_protocol", "");
				JSONObject desc = o.optJSONObject("service_description");
				if (desc != null)
				{
					s.name = desc.optString("service_name", desc.optString("name", ""));
				}
				result.list.add(s);
			}
		}
		return result;
	}

	public static ConfigResult requestConfig(Services services, Pending pending, String captchaSolution) throws GatewayException
	{
		Service chosen = null;
		for (Service s : services.list)
		{
			if ("amnezia-free".equals(s.type) && s.available)
			{
				chosen = s;
				break;
			}
		}
		if (chosen == null)
		{
			throw new GatewayException("amnezia-free is not available for this address");
		}
		String protocol = chosen.protocol.isEmpty() ? "awg" : chosen.protocol;
		String country = services.countryCode;
		String priv;
		String pub;
		String captchaId = "";
		if (pending != null && captchaSolution != null && !captchaSolution.trim().isEmpty())
		{
			priv = pending.privateKey;
			pub = pending.publicKey;
			protocol = pending.protocol;
			if (!pending.country.isEmpty())
			{
				country = pending.country;
			}
			captchaId = pending.captchaId;
		}
		else if ("awg".equals(protocol))
		{
			byte[] k = new byte[32];
			RANDOM.nextBytes(k);
			priv = Base64.encodeToString(k, Base64.NO_WRAP);
			pub = Base64.encodeToString(x25519Base(k), Base64.NO_WRAP);
		}
		else
		{
			priv = "";
			pub = UUID.randomUUID().toString();
		}
		try
		{
			JSONObject payload = basePayload();
			payload.put("user_country_code", country);
			payload.put("service_type", "amnezia-free");
			payload.put("service_protocol", protocol);
			payload.put("public_key", pub);
			if (!captchaId.isEmpty() && captchaSolution != null && !captchaSolution.trim().isEmpty())
			{
				payload.put("captcha_id", captchaId);
				payload.put("captcha_solution", captchaSolution.trim());
			}
			JSONObject data = post("/v1/config", payload);
			ConfigResult result = new ConfigResult();
			result.httpStatus = data.optInt("_http", 0);
			result.message = data.optString("message", data.optString("error", ""));
			String image = data.optString("captcha_image", "");
			if (!image.isEmpty())
			{
				int comma = image.indexOf(',');
				if (image.startsWith("data:") && comma >= 0)
				{
					image = image.substring(comma + 1);
				}
				Pending p = new Pending();
				p.privateKey = priv;
				p.publicKey = pub;
				p.country = country;
				p.protocol = protocol;
				p.captchaId = data.optString("captcha_id", "");
				p.captchaImage = Base64.decode(image, Base64.DEFAULT);
				result.pending = p;
				return result;
			}
			String encoded = data.optString("config", "");
			if (encoded.isEmpty())
			{
				return result;
			}
			String decoded = decodeVpnKey(encoded);
			if (!priv.isEmpty())
			{
				decoded = decoded.replace("$WIREGUARD_CLIENT_PRIVATE_KEY", priv);
			}
			result.config = new JSONObject(decoded);
			return result;
		}
		catch (GatewayException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new GatewayException("config request failed: " + e.getMessage(), e);
		}
	}

	public static String decodeVpnKey(String value) throws GatewayException
	{
		try
		{
			String text = value.trim();
			if (text.startsWith("vpn://"))
			{
				text = text.substring(6);
			}
			byte[] raw = Base64.decode(text, Base64.URL_SAFE);
			if (raw.length > 4)
			{
				try
				{
					Inflater inflater = new Inflater();
					inflater.setInput(raw, 4, raw.length - 4);
					ByteArrayOutputStream out = new ByteArrayOutputStream();
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
					inflater.end();
					if (out.size() > 0)
					{
						raw = out.toByteArray();
					}
				}
				catch (Exception ignored)
				{
				}
			}
			return new String(raw, "UTF-8");
		}
		catch (Exception e)
		{
			throw new GatewayException("bad vpn key: " + e.getMessage(), e);
		}
	}

	private static JSONObject basePayload() throws GatewayException
	{
		try
		{
			JSONObject o = new JSONObject();
			o.put("os_version", "android");
			o.put("app_version", APP_VERSION);
			o.put("cli_name", "AmneziaVPN");
			o.put("app_language", "en");
			o.put("installation_uuid", UUID.randomUUID().toString());
			return o;
		}
		catch (Exception e)
		{
			throw new GatewayException("payload error", e);
		}
	}

	private static JSONObject post(String path, JSONObject payload) throws GatewayException
	{
		HttpURLConnection conn = null;
		try
		{
			byte[] aesKey = new byte[32];
			byte[] aesIv = new byte[32];
			byte[] salt = new byte[8];
			RANDOM.nextBytes(aesKey);
			RANDOM.nextBytes(aesIv);
			RANDOM.nextBytes(salt);
			byte[] iv16 = new byte[16];
			System.arraycopy(aesIv, 0, iv16, 0, 16);

			JSONObject keys = new JSONObject();
			keys.put("aes_key", Base64.encodeToString(aesKey, Base64.NO_WRAP));
			keys.put("aes_iv", Base64.encodeToString(aesIv, Base64.NO_WRAP));
			keys.put("aes_salt", Base64.encodeToString(salt, Base64.NO_WRAP));

			Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
			rsa.init(Cipher.ENCRYPT_MODE, KeyFactory.getInstance("RSA")
				.generatePublic(new X509EncodedKeySpec(Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT))));
			byte[] keyPayload = rsa.doFinal(keys.toString().getBytes("UTF-8"));

			Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
			aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv16));
			byte[] apiPayload = aes.doFinal(payload.toString().getBytes("UTF-8"));

			JSONObject body = new JSONObject();
			body.put("key_payload", Base64.encodeToString(keyPayload, Base64.NO_WRAP));
			body.put("api_payload", Base64.encodeToString(apiPayload, Base64.NO_WRAP));

			conn = (HttpURLConnection) new URL(GATEWAY + path).openConnection();
			conn.setRequestMethod("POST");
			conn.setConnectTimeout(25000);
			conn.setReadTimeout(25000);
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("X-Client-Request-ID", UUID.randomUUID().toString());
			OutputStream os = conn.getOutputStream();
			os.write(body.toString().getBytes("UTF-8"));
			os.close();

			int status = conn.getResponseCode();
			InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			if (is != null)
			{
				byte[] buf = new byte[8192];
				int n;
				while ((n = is.read(buf)) > 0)
				{
					bos.write(buf, 0, n);
				}
				is.close();
			}

			Cipher dec = Cipher.getInstance("AES/CBC/PKCS5Padding");
			dec.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv16));
			JSONObject decoded;
			try
			{
				decoded = new JSONObject(new String(dec.doFinal(bos.toByteArray()), "UTF-8"));
			}
			catch (Exception e)
			{
				throw new GatewayException("HTTP " + status + ", decrypt failed: " + e.getMessage(), e);
			}
			decoded.put("_http", status);
			return decoded;
		}
		catch (GatewayException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new GatewayException("request failed: " + e.getMessage(), e);
		}
		finally
		{
			if (conn != null)
			{
				conn.disconnect();
			}
		}
	}

	public static byte[] x25519Base(byte[] privateKey)
	{
		byte[] s = privateKey.clone();
		s[0] &= (byte) 248;
		s[31] &= (byte) 127;
		s[31] |= (byte) 64;
		BigInteger x1 = BigInteger.valueOf(9);
		BigInteger x2 = BigInteger.ONE;
		BigInteger z2 = BigInteger.ZERO;
		BigInteger x3 = x1;
		BigInteger z3 = BigInteger.ONE;
		int swap = 0;
		for (int t = 254; t >= 0; t--)
		{
			int kt = (s[t >> 3] >> (t & 7)) & 1;
			swap ^= kt;
			if (swap == 1)
			{
				BigInteger tmp = x2;
				x2 = x3;
				x3 = tmp;
				tmp = z2;
				z2 = z3;
				z3 = tmp;
			}
			swap = kt;
			BigInteger a = x2.add(z2).mod(P);
			BigInteger aa = a.multiply(a).mod(P);
			BigInteger b = x2.subtract(z2).mod(P);
			BigInteger bb = b.multiply(b).mod(P);
			BigInteger e = aa.subtract(bb).mod(P);
			BigInteger c = x3.add(z3).mod(P);
			BigInteger d = x3.subtract(z3).mod(P);
			BigInteger da = d.multiply(a).mod(P);
			BigInteger cb = c.multiply(b).mod(P);
			BigInteger sum = da.add(cb).mod(P);
			BigInteger diff = da.subtract(cb).mod(P);
			x3 = sum.multiply(sum).mod(P);
			z3 = x1.multiply(diff.multiply(diff).mod(P)).mod(P);
			x2 = aa.multiply(bb).mod(P);
			z2 = e.multiply(aa.add(A24.multiply(e)).mod(P)).mod(P);
		}
		if (swap == 1)
		{
			BigInteger tmp = x2;
			x2 = x3;
			x3 = tmp;
			tmp = z2;
			z2 = z3;
			z3 = tmp;
		}
		BigInteger result = x2.multiply(z2.modPow(P.subtract(BigInteger.valueOf(2)), P)).mod(P);
		byte[] be = result.toByteArray();
		byte[] out = new byte[32];
		for (int i = 0; i < 32; i++)
		{
			int idx = be.length - 1 - i;
			out[i] = idx >= 0 ? be[idx] : 0;
		}
		return out;
	}
}
