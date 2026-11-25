package org.strongswan.android.utils;

import android.content.Context;

import org.strongswan.android.logic.StrongSwanApplication;

public final class Str
{
	private Str()
	{
	}

	public static String get(int id, Object... args)
	{
		Context context = StrongSwanApplication.getContext();
		if (args == null || args.length == 0)
		{
			return context.getString(id);
		}
		return context.getString(id, args);
	}
}
