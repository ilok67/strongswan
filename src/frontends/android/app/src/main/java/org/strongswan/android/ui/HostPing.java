package org.strongswan.android.ui;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class HostPing
{
	private static final int IKE_SA_INIT_EXCHANGE = 34;
	private static final int IKE_INFORMATIONAL = 37;
	private static final int PAYLOAD_SA = 33;
	private static final int PAYLOAD_KE = 34;
	private static final int PAYLOAD_NONCE = 40;
	private static final int DH_GROUP_14 = 14;
	private static final int DH14_LEN = 256;
	private static final int HEADER_LEN = 28;
	private static final int ATTEMPTS = 2;
	private static final int ATTEMPT_TIMEOUT_MS = 1500;
	private static final int DNS_TIMEOUT_MS = 2000;

	private HostPing() {}

	public static String ping(String host, Integer port)
	{
		if (host == null || host.isEmpty())
		{
			return "timeout";
		}
		int ikePort = (port != null && port > 0) ? port : 500;
		int offset = (ikePort == 4500) ? 4 : 0;

		InetAddress addr = resolve(host);
		if (addr == null)
		{
			return "timeout";
		}

		byte[] spi = new byte[8];
		new SecureRandom().nextBytes(spi);
		byte[] ike = buildIkeSaInit(spi);
		byte[] request = (offset == 4) ? withNatT(ike) : ike;

		try (DatagramSocket socket = new DatagramSocket())
		{
			socket.connect(new InetSocketAddress(addr, ikePort));
			byte[] buf = new byte[2048];

			for (int i = 0; i < ATTEMPTS; i++)
			{
				long t0 = System.nanoTime();
				long deadline = t0 + ATTEMPT_TIMEOUT_MS * 1_000_000L;
				socket.send(new DatagramPacket(request, request.length));

				while (true)
				{
					long remainingMs = (deadline - System.nanoTime()) / 1_000_000L;
					if (remainingMs <= 0)
					{
						break;
					}
					socket.setSoTimeout((int) remainingMs);
					DatagramPacket in = new DatagramPacket(buf, buf.length);
					try
					{
						socket.receive(in);
					}
					catch (java.net.SocketTimeoutException e)
					{
						break;
					}
					long rtt = (System.nanoTime() - t0) / 1_000_000L;
					if (isValidResponse(in.getData(), in.getLength(), offset, spi))
					{
						return rtt + " ms";
					}
				}
			}
		}
		catch (Exception ignored)
		{
		}
		return "timeout";
	}

	private static InetAddress resolve(String host)
	{
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try
		{
			Future<InetAddress> f = executor.submit(new Callable<InetAddress>()
			{
				@Override
				public InetAddress call() throws Exception
				{
					return InetAddress.getByName(host);
				}
			});
			return f.get(DNS_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		}
		catch (Exception e)
		{
			return null;
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	private static boolean isValidResponse(byte[] data, int length, int offset, byte[] spi)
	{
		if (length < offset + HEADER_LEN)
		{
			return false;
		}
		for (int i = 0; i < 8; i++)
		{
			if (data[offset + i] != spi[i])
			{
				return false;
			}
		}
		int version = data[offset + 17] & 0xFF;
		int exchange = data[offset + 18] & 0xFF;
		return (version >> 4) == 2 &&
			   (exchange == IKE_SA_INIT_EXCHANGE || exchange == IKE_INFORMATIONAL);
	}

	private static byte[] withNatT(byte[] ike)
	{
		byte[] packet = new byte[ike.length + 4];
		System.arraycopy(ike, 0, packet, 4, ike.length);
		return packet;
	}

	private static byte[] buildIkeSaInit(byte[] spi)
	{
		SecureRandom rnd = new SecureRandom();
		byte[] nonce = new byte[32];
		byte[] keData = new byte[DH14_LEN];
		rnd.nextBytes(nonce);
		rnd.nextBytes(keData);
		keData[0] &= 0x7f;

		byte[] sa = payload(PAYLOAD_KE, saBody());
		byte[] ke = payload(PAYLOAD_NONCE, keBody(keData));
		byte[] ni = payload(0, nonce);

		int length = HEADER_LEN + sa.length + ke.length + ni.length;
		ByteBuffer bb = ByteBuffer.allocate(length);
		bb.put(spi);
		bb.put(new byte[8]);
		bb.put((byte) PAYLOAD_SA);
		bb.put((byte) 0x20);
		bb.put((byte) IKE_SA_INIT_EXCHANGE);
		bb.put((byte) 0x08);
		bb.putInt(0);
		bb.putInt(length);
		bb.put(sa);
		bb.put(ke);
		bb.put(ni);
		return bb.array();
	}

	private static byte[] payload(int next, byte[] body)
	{
		ByteBuffer bb = ByteBuffer.allocate(4 + body.length);
		bb.put((byte) next);
		bb.put((byte) 0);
		bb.putShort((short) (4 + body.length));
		bb.put(body);
		return bb.array();
	}

	private static byte[] saBody()
	{
		byte[] tEncr = transform(true, 1, 12, 256);
		byte[] tPrf = transform(true, 2, 5, 0);
		byte[] tInteg = transform(true, 3, 12, 0);
		byte[] tDh = transform(false, 4, DH_GROUP_14, 0);
		int len = 8 + tEncr.length + tPrf.length + tInteg.length + tDh.length;
		ByteBuffer bb = ByteBuffer.allocate(len);
		bb.put((byte) 0);
		bb.put((byte) 0);
		bb.putShort((short) len);
		bb.put((byte) 1);
		bb.put((byte) 1);
		bb.put((byte) 0);
		bb.put((byte) 4);
		bb.put(tEncr);
		bb.put(tPrf);
		bb.put(tInteg);
		bb.put(tDh);
		return bb.array();
	}

	private static byte[] keBody(byte[] publicValue)
	{
		ByteBuffer bb = ByteBuffer.allocate(4 + publicValue.length);
		bb.putShort((short) DH_GROUP_14);
		bb.putShort((short) 0);
		bb.put(publicValue);
		return bb.array();
	}

	private static byte[] transform(boolean more, int type, int id, int keyLen)
	{
		int len = keyLen > 0 ? 12 : 8;
		ByteBuffer bb = ByteBuffer.allocate(len);
		bb.put((byte) (more ? 3 : 0));
		bb.put((byte) 0);
		bb.putShort((short) len);
		bb.put((byte) type);
		bb.put((byte) 0);
		bb.putShort((short) id);
		if (keyLen > 0)
		{
			bb.putShort((short) 0x800e);
			bb.putShort((short) keyLen);
		}
		return bb.array();
	}
}
