package org.strongswan.android.v2ray;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.strongswan.android.R;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class V2RayProfileAdapter extends ArrayAdapter<String>
{
	private final List<String> items;
	private final Map<String, String> pings = new HashMap<String, String>();
	private ColorStateList nameColors;

	public V2RayProfileAdapter(Context context, List<String> items)
	{
		super(context, R.layout.profile_list_item, items);
		this.items = items;
		sortItems();
	}

	public void setPing(String link, String value)
	{
		pings.put(link, value);
		notifyDataSetChanged();
	}

	public void clearPings()
	{
		pings.clear();
		notifyDataSetChanged();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		View view = convertView;
		if (view == null)
		{
			view = LayoutInflater.from(getContext()).inflate(R.layout.profile_list_item, parent, false);
		}

		String link = getItem(position);
		V2RayConfigBuilder.Info info = V2RayConfigBuilder.info(link);
		boolean active = link.equals(SingBoxVpnService.getLink()) && SingBoxVpnService.isRunning();

		TextView name = view.findViewById(R.id.profile_item_name);
		if (nameColors == null)
		{
			nameColors = name.getTextColors();
		}
		name.setText(active ? "\u25CF " + info.name : info.name);
		if (active)
		{
			name.setTextColor(0xFF4ADE80);
		}
		else
		{
			name.setTextColor(nameColors);
		}

		TextView detail = view.findViewById(R.id.profile_item_managed);
		detail.setText(info.summary);
		detail.setVisibility(info.summary.isEmpty() ? View.GONE : View.VISIBLE);

		TextView gateway = view.findViewById(R.id.profile_item_gateway);
		gateway.setText(info.port > 0 ? info.host + ":" + info.port : info.host);

		TextView pingView = view.findViewById(R.id.profile_item_ping);
		if (pingView != null)
		{
			String ping = pings.get(link);
			if (ping == null)
			{
				pingView.setText("\u2014");
				pingView.setTextColor(0x99FFFFFF);
			}
			else if (ping.endsWith("ms"))
			{
				pingView.setText(ping);
				pingView.setTextColor(0xFF4ADE80);
			}
			else
			{
				pingView.setText(ping);
				pingView.setTextColor(0xFFEF4444);
			}
		}

		view.findViewById(R.id.profile_item_username).setVisibility(View.GONE);
		view.findViewById(R.id.profile_item_certificate).setVisibility(View.GONE);
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
		Collections.sort(items, new Comparator<String>()
		{
			@Override
			public int compare(String lhs, String rhs)
			{
				int c = Integer.compare(pingScore(lhs), pingScore(rhs));
				if (c != 0)
				{
					return c;
				}
				return V2RayConfigBuilder.name(lhs).compareToIgnoreCase(V2RayConfigBuilder.name(rhs));
			}
		});
	}

	private int pingScore(String link)
	{
		String ping = pings.get(link);
		if (ping == null)
		{
			return Integer.MAX_VALUE - 1;
		}
		if (ping.endsWith("ms"))
		{
			try
			{
				return Integer.parseInt(ping.replace(" ms", "").trim());
			}
			catch (NumberFormatException e)
			{
				return Integer.MAX_VALUE;
			}
		}
		return Integer.MAX_VALUE;
	}
}
