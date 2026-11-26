package org.strongswan.android.awg;

public final class AwgState
{
	static volatile boolean running;
	static volatile String id;
	static volatile String name = "";
	static volatile String lastProfile;

	private AwgState()
	{
	}
}
