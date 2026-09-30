package org.strongswan.android.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.strongswan.android.R;
import org.strongswan.android.data.VpnProfile;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class VpnProfileAdapter extends ArrayAdapter<VpnProfile>
{
	private final int resource;
	private final List<VpnProfile> items;
    private final java.util.Map<String, String> pings = new java.util.HashMap<>();

	public VpnProfileAdapter(Context context, int resource, List<VpnProfile> items)
	{
		super(context, resource, items);
		this.resource = resource;
		this.items = items;
		sortItems();
	}

    public void setPing(String uuid, String value)
    {
	     pings.put(uuid, value);
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
		View vpnProfileView;
		if (convertView != null)
		{
			vpnProfileView = convertView;
		}
		else
		{
			LayoutInflater inflater = LayoutInflater.from(getContext());
			vpnProfileView = inflater.inflate(resource, parent, false);
		}

		VpnProfile profile = getItem(position);

		TextView tv = vpnProfileView.findViewById(R.id.profile_item_name);
		tv.setText(profile.getName());

		tv = vpnProfileView.findViewById(R.id.profile_item_managed);
		tv.setVisibility(profile.isReadOnly() ? View.VISIBLE : View.GONE);

		tv = vpnProfileView.findViewById(R.id.profile_item_gateway);
		Integer port = profile.getPort();
		String endpoint = profile.getGateway();
		if (port != null && port > 0)
		{
			endpoint = endpoint + ":" + port;
		}
		tv.setText(endpoint);
         TextView pingView = vpnProfileView.findViewById(R.id.profile_item_ping);
if (pingView != null)
{
	String ping = pings.get(profile.getUUID().toString());
	if (ping == null)
	{
		pingView.setText("—");
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
		tv = vpnProfileView.findViewById(R.id.profile_item_username);
		tv.setVisibility(View.GONE);

		tv = vpnProfileView.findViewById(R.id.profile_item_certificate);
		tv.setVisibility(View.GONE);

		return vpnProfileView;
	}

	@Override
	public void notifyDataSetChanged()
	{
		sortItems();
		super.notifyDataSetChanged();
	}

	private void sortItems()
	{
		Collections.sort(this.items, new Comparator<VpnProfile>()
		{
			@Override
			public int compare(VpnProfile lhs, VpnProfile rhs)
			{
				return lhs.getName().compareToIgnoreCase(rhs.getName());
			}
		});
	}
}
