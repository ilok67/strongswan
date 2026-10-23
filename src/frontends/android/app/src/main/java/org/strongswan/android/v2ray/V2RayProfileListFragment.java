package org.strongswan.android.v2ray;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class V2RayProfileListFragment extends Fragment
{
	private V2RayProfileStore store;
	private final List<String> links = new ArrayList<String>();
	private ArrayAdapter<String> adapter;
	private String pendingLink;

	private final ActivityResultLauncher<Intent> vpnPermission = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == Activity.RESULT_OK && pendingLink != null)
				{
					SingBoxVpnService.start(requireContext(), pendingLink);
				}
			});

	private final BroadcastReceiver receiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (adapter != null)
			{
				adapter.notifyDataSetChanged();
			}
		}
	};

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		Context context = requireContext();
		store = new V2RayProfileStore(context);
		links.clear();
		links.addAll(store.load());

		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);

		ListView list = new ListView(context);
		adapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, links)
		{
			@Override
			public View getView(int position, View convertView, ViewGroup parent)
			{
				TextView v = (TextView) super.getView(position, convertView, parent);
				String link = getItem(position);
				boolean active = link != null && link.equals(SingBoxVpnService.getLink()) && SingBoxVpnService.isRunning();
				v.setText(V2RayConfigBuilder.name(link) + (active ? "  \u25CF" : ""));
				return v;
			}
		};
		list.setAdapter(adapter);
		list.setEmptyView(emptyView(context));
		list.setOnItemClickListener(new AdapterView.OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id)
			{
				String link = links.get(position);
				if (link.equals(SingBoxVpnService.getLink()) && SingBoxVpnService.isRunning())
				{
					SingBoxVpnService.stop(requireContext());
				}
				else
				{
					connect(link);
				}
			}
		});
		list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener()
		{
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
			{
				confirmDelete(position);
				return true;
			}
		});
		root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
		((ViewGroup) list.getParent()).addView(list.getEmptyView());

		Button add = new Button(context);
		add.setText("Paste from clipboard");
		add.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				addFromClipboard();
			}
		});
		Button log = new Button(context);
		log.setText("Log");
		log.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				showLog();
			}
		});

		LinearLayout row = new LinearLayout(context);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.addView(add, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
		row.addView(log, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		root.addView(row);
		return root;
	}

	private View emptyView(Context context)
	{
		TextView empty = new TextView(context);
		empty.setText("No V2Ray profiles. Copy a vless:// vmess:// trojan:// ss:// or hy2:// link and tap Paste.");
		int pad = (int) (24 * getResources().getDisplayMetrics().density);
		empty.setPadding(pad, pad, pad, pad);
		empty.setVisibility(View.GONE);
		return empty;
	}

	@Override
	public void onStart()
	{
		super.onStart();
		ContextCompat.registerReceiver(requireContext(), receiver, new IntentFilter(SingBoxVpnService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED);
		adapter.notifyDataSetChanged();
	}

	@Override
	public void onStop()
	{
		requireContext().unregisterReceiver(receiver);
		super.onStop();
	}

	private void addFromClipboard()
	{
		ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData data = cm.getPrimaryClip();
		if (data == null || data.getItemCount() == 0)
		{
			Toast.makeText(getActivity(), "clipboard is empty", Toast.LENGTH_SHORT).show();
			return;
		}
		String text = data.getItemAt(0).coerceToText(requireContext()).toString();
		int added = 0;
		for (String line : text.split("\\r?\\n"))
		{
			String link = line.trim();
			if (link.isEmpty() || links.contains(link))
			{
				continue;
			}
			try
			{
				V2RayConfigBuilder.build(link, "1.10.0");
				links.add(link);
				added++;
			}
			catch (Exception e)
			{
			}
		}
		if (added > 0)
		{
			store.save(links);
			adapter.notifyDataSetChanged();
		}
		Toast.makeText(getActivity(), added + " added", Toast.LENGTH_SHORT).show();
	}

	private void confirmDelete(final int position)
	{
		new AlertDialog.Builder(requireActivity())
				.setMessage("Delete " + V2RayConfigBuilder.name(links.get(position)) + "?")
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					links.remove(position);
					store.save(links);
					adapter.notifyDataSetChanged();
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void showLog()
	{
		String text = SingBoxVpnService.recentLog();
		TextView view = new TextView(requireContext());
		view.setText(text.isEmpty() ? "empty" : text);
		view.setTextIsSelectable(true);
		view.setTextSize(11f);
		int pad = (int) (12 * getResources().getDisplayMetrics().density);
		view.setPadding(pad, pad, pad, pad);
		android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext());
		scroll.addView(view);
		new AlertDialog.Builder(requireActivity())
				.setTitle("sing-box log")
				.setView(scroll)
				.setPositiveButton(android.R.string.ok, null)
				.show();
	}

	private void connect(String link)
	{
		pendingLink = link;
		Intent prepare = VpnService.prepare(requireContext());
		if (prepare != null)
		{
			vpnPermission.launch(prepare);
		}
		else
		{
			SingBoxVpnService.start(requireContext(), link);
		}
	}
}
