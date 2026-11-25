package org.strongswan.android.awg;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.json.JSONObject;
import org.strongswan.android.R;
import org.strongswan.android.ui.widget.UiStyle;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AwgProfileAdapter extends ArrayAdapter<JSONObject>
{
	public interface ActionListener
	{
		void onEdit(JSONObject profile);

		void onShare(JSONObject profile);

		void onDelete(JSONObject profile);
	}

	private final List<JSONObject> items;
	private final Map<String, String> pings = new HashMap<String, String>();
	private ColorStateList nameColors;
	private ActionListener listener;

	public AwgProfileAdapter(Context context, List<JSONObject> items)
	{
		super(context, R.layout.profile_list_item, items);
		this.items = items;
		sortItems();
	}

	public void setActionListener(ActionListener listener)
	{
		this.listener = listener;
	}

	public void setPing(String id, String value)
	{
		pings.put(id, value);
		notifyDataSetChanged();
	}

	public void clearPings()
	{
		pings.clear();
		notifyDataSetChanged();
	}

	public boolean hasMsPing(String id)
	{
		String ping = pings.get(id);
		return ping != null && ping.endsWith("ms");
	}

	public boolean isPingFinished()
	{
		if (pings.isEmpty())
		{
			return false;
		}
		String running = getContext().getString(R.string.ping_running);
		for (String value : pings.values())
		{
			if (running.equals(value))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		View view = convertView;
		if (view == null)
		{
			view = LayoutInflater.from(getContext()).inflate(R.layout.profile_list_item, parent, false);
		}
		final JSONObject p = getItem(position);
		String id = p.optString("id");
		boolean active = AwgVpnService.isActive(id) && AwgVpnService.isRunning();

		TextView name = view.findViewById(R.id.profile_item_name);
		if (nameColors == null)
		{
			nameColors = name.getTextColors();
		}
		name.setText(active ? "\u25CF " + p.optString("name") : p.optString("name"));
		if (active)
		{
			name.setTextColor(0xFF4ADE80);
		}
		else
		{
			name.setTextColor(nameColors);
		}

		TextView detail = view.findViewById(R.id.profile_item_managed);
		detail.setText("AmneziaWG");
		detail.setVisibility(View.VISIBLE);

		TextView gateway = view.findViewById(R.id.profile_item_gateway);
		gateway.setText(p.optString("host") + ":" + p.optInt("port"));

		TextView pingView = view.findViewById(R.id.profile_item_ping);
		if (pingView != null)
		{
			String ping = pings.get(id);
			String running = getContext().getString(R.string.ping_running);
			String timeout = getContext().getString(R.string.ping_timeout);
			if (ping == null)
			{
				pingView.setText("\u2014");
				pingView.setTextColor(0x99FFFFFF);
			}
			else if (running.equals(ping))
			{
				pingView.setText(ping);
				pingView.setTextColor(0xFFFFA000);
			}
			else if (ping.endsWith("ms"))
			{
				pingView.setText(ping);
				pingView.setTextColor(0xFF4ADE80);
			}
			else if (timeout.equals(ping))
			{
				pingView.setText(ping);
				pingView.setTextColor(0xFFEF4444);
			}
			else
			{
				pingView.setText(ping);
				pingView.setTextColor(0x99FFFFFF);
			}
		}
		view.findViewById(R.id.profile_item_username).setVisibility(View.GONE);
		view.findViewById(R.id.profile_item_certificate).setVisibility(View.GONE);

		UiStyle.bindActions(view, true, true, new UiStyle.CardActions()
		{
			@Override
			public void onEdit()
			{
				if (listener != null)
				{
					listener.onEdit(p);
				}
			}

			@Override
			public void onShare()
			{
				if (listener != null)
				{
					listener.onShare(p);
				}
			}

			@Override
			public void onDelete()
			{
				if (listener != null)
				{
					listener.onDelete(p);
				}
			}
		});
		return view;
	}

	@Override
	public void notifyDataSetChanged()
	{
		sortItems();
		super.notifyDataSetChanged();
	}

	private void sortItems()
	{
		Collections.sort(items, new Comparator<JSONObject>()
		{
			@Override
			public int compare(JSONObject a, JSONObject b)
			{
				int c = Integer.compare(score(a), score(b));
				if (c != 0)
				{
					return c;
				}
				return a.optString("name").compareToIgnoreCase(b.optString("name"));
			}
		});
	}

	private int score(JSONObject p)
	{
		String ping = pings.get(p.optString("id"));
		if (ping == null)
		{
			return Integer.MAX_VALUE - 1;
		}
		if (ping.endsWith("ms"))
		{
			try
			{
				return Integer.parseInt(ping.replace("ms", "").trim());
			}
			catch (NumberFormatException e)
			{
				return Integer.MAX_VALUE;
			}
		}
		return Integer.MAX_VALUE;
	}
}
