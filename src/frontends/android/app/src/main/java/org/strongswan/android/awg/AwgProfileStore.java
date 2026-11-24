package org.strongswan.android.awg;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AwgProfileStore
{
	private static final String PREFS = "awg_profiles";
	private static final String KEY = "profiles";

	private final SharedPreferences prefs;

	public AwgProfileStore(Context context)
	{
		prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	public List<JSONObject> load()
	{
		List<JSONObject> result = new ArrayList<JSONObject>();
		try
		{
			JSONArray a = new JSONArray(prefs.getString(KEY, "[]"));
			for (int i = 0; i < a.length(); i++)
			{
				result.add(a.getJSONObject(i));
			}
		}
		catch (Exception e)
		{
			return new ArrayList<JSONObject>();
		}
		return result;
	}

	public void save(List<JSONObject> profiles)
	{
		JSONArray a = new JSONArray();
		for (JSONObject p : profiles)
		{
			a.put(p);
		}
		prefs.edit().putString(KEY, a.toString()).apply();
	}
}
