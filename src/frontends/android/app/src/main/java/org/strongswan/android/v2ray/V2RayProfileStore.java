package org.strongswan.android.v2ray;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class V2RayProfileStore
{
	private static final String PREFS = "v2ray_profiles";
	private static final String KEY = "links";

	private final SharedPreferences prefs;

	public V2RayProfileStore(Context context)
	{
		prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	public List<String> load()
	{
		List<String> links = new ArrayList<String>();
		try
		{
			JSONArray a = new JSONArray(prefs.getString(KEY, "[]"));
			for (int i = 0; i < a.length(); i++)
			{
				links.add(a.getString(i));
			}
		}
		catch (Exception e)
		{
			return new ArrayList<String>();
		}
		return links;
	}

	public void save(List<String> links)
	{
		JSONArray a = new JSONArray();
		for (String l : links)
		{
			a.put(l);
		}
		prefs.edit().putString(KEY, a.toString()).apply();
	}
}
