package org.strongswan.android.awg;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AwgProfileListFragment extends Fragment
{
	private AwgProfileStore store;
	private final List<JSONObject> profiles = new ArrayList<JSONObject>();
	private ArrayAdapter<JSONObject> adapter;
	private JSONObject pendingProfile;
	private TextView hint;
	private Button getFree;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private AmneziaGateway.Services services;

	private final ActivityResultLauncher<Intent> vpnPermission =
		registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			if (result.getResultCode() == Activity.RESULT_OK && pendingProfile != null)
			{
				AwgVpnService.start(requireContext(), pendingProfile);
			}
		});

	private final BroadcastReceiver receiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String error = intent.getStringExtra(AwgVpnService.EXTRA_ERROR);
			if (error != null)
			{
				Toast.makeText(context, error, Toast.LENGTH_LONG).show();
			}
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
		store = new AwgProfileStore(context);
		profiles.clear();
		profiles.addAll(store.load());
		int pad = (int) (12 * getResources().getDisplayMetrics().density);

		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);

		LinearLayout buttons = new LinearLayout(context);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		buttons.setPadding(pad, pad, pad, 0);

		getFree = new Button(context);
		getFree.setText("Get free server");
		getFree.setOnClickListener(v -> fetchFree());
		buttons.addView(getFree, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

		Button importBtn = new Button(context);
		importBtn.setText("Import");
		importBtn.setOnClickListener(v -> showImportDialog());
		buttons.addView(importBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		root.addView(buttons);

		hint = new TextView(context);
		hint.setText("No AmneziaWG profiles");
		hint.setTextColor(Color.WHITE);
		hint.setGravity(Gravity.CENTER);
		hint.setPadding(pad, pad * 2, pad, pad);
		root.addView(hint);

		ListView list = new ListView(context);
		list.setDivider(null);
		list.setDividerHeight(0);
		adapter = new ArrayAdapter<JSONObject>(context, android.R.layout.simple_list_item_1, profiles)
		{
			@Override
			public View getView(int position, View convertView, ViewGroup parent)
			{
				TextView tv = convertView instanceof TextView ? (TextView) convertView : new TextView(getContext());
				JSONObject p = getItem(position);
				boolean active = AwgVpnService.isActive(p.optString("id"));
				tv.setText((active ? "\u25CF " : "") + p.optString("name") + "\n" + p.optString("host") + ":" + p.optInt("port"));
				tv.setTextColor(active ? Color.parseColor("#4ADE80") : Color.WHITE);
				tv.setTextSize(15);
				tv.setPadding(pad * 2, pad, pad * 2, pad);
				return tv;
			}
		};
		list.setAdapter(adapter);
		list.setOnItemClickListener((parent, view, position, id) -> {
			JSONObject p = profiles.get(position);
			if (AwgVpnService.isActive(p.optString("id")))
			{
				AwgVpnService.stop(requireContext());
				adapter.notifyDataSetChanged();
			}
			else
			{
				connect(p);
			}
		});
		list.setOnItemLongClickListener((parent, view, position, id) -> {
			confirmDelete(position);
			return true;
		});
		root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
		updateEmpty();
		return root;
	}

	@Override
	public void onStart()
	{
		super.onStart();
		ContextCompat.registerReceiver(requireContext(), receiver, new IntentFilter(AwgVpnService.ACTION_STATE),
			ContextCompat.RECEIVER_NOT_EXPORTED);
		if (adapter != null)
		{
			adapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onStop()
	{
		try
		{
			requireContext().unregisterReceiver(receiver);
		}
		catch (Exception ignored)
		{
		}
		super.onStop();
	}

	@Override
	public void onDestroy()
	{
		executor.shutdownNow();
		super.onDestroy();
	}

	private void updateEmpty()
	{
		if (hint != null)
		{
			hint.setVisibility(profiles.isEmpty() ? View.VISIBLE : View.GONE);
		}
	}

	private void connect(JSONObject p)
	{
		pendingProfile = p;
		Intent prepare = VpnService.prepare(requireContext());
		if (prepare != null)
		{
			vpnPermission.launch(prepare);
		}
		else
		{
			AwgVpnService.start(requireContext(), p);
		}
	}

	private void confirmDelete(int position)
	{
		JSONObject p = profiles.get(position);
		new AlertDialog.Builder(requireContext())
			.setTitle(p.optString("name"))
			.setMessage("Delete this profile?")
			.setPositiveButton("Delete", (d, w) -> {
				if (AwgVpnService.isActive(p.optString("id")))
				{
					AwgVpnService.stop(requireContext());
				}
				profiles.remove(p);
				store.save(profiles);
				adapter.notifyDataSetChanged();
				updateEmpty();
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	private void addProfile(JSONObject p)
	{
		try
		{
			if (!p.has("id"))
			{
				p.put("id", UUID.randomUUID().toString());
			}
		}
		catch (Exception ignored)
		{
		}
		profiles.add(p);
		store.save(profiles);
		adapter.notifyDataSetChanged();
		updateEmpty();
	}

	private void showImportDialog()
	{
		Context context = requireContext();
		EditText input = new EditText(context);
		input.setHint("vpn:// key or .conf text");
		input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
		input.setMinLines(4);
		input.setGravity(Gravity.TOP);
		ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
		if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0)
		{
			ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
			CharSequence text = item.getText();
			if (text != null && (text.toString().trim().startsWith("vpn://") || text.toString().contains("[Interface]")))
			{
				input.setText(text);
			}
		}
		new AlertDialog.Builder(context)
			.setTitle("Import AmneziaWG")
			.setView(input)
			.setPositiveButton("Import", (d, w) -> {
				try
				{
					addProfile(AwgConfig.parse(input.getText().toString()));
				}
				catch (Exception e)
				{
					Toast.makeText(context, "Invalid config: " + e.getMessage(), Toast.LENGTH_LONG).show();
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	private void setBusy(boolean busy)
	{
		Activity a = getActivity();
		if (a == null)
		{
			return;
		}
		a.runOnUiThread(() -> {
			if (getFree != null)
			{
				getFree.setEnabled(!busy);
				getFree.setText(busy ? "Requesting..." : "Get free server");
			}
		});
	}

	private void toast(String message)
	{
		Activity a = getActivity();
		if (a != null)
		{
			a.runOnUiThread(() -> Toast.makeText(a, message, Toast.LENGTH_LONG).show());
		}
	}

	private void fetchFree()
	{
		setBusy(true);
		executor.execute(() -> {
			try
			{
				services = AmneziaGateway.listServices();
				handleResult(AmneziaGateway.requestConfig(services, null, null));
			}
			catch (Exception e)
			{
				toast("Failed: " + e.getMessage());
				setBusy(false);
			}
		});
	}

	private void handleResult(AmneziaGateway.ConfigResult result)
	{
		Activity a = getActivity();
		if (a == null)
		{
			return;
		}
		if (result.config != null)
		{
			try
			{
				JSONObject p = AwgConfig.fromAmnezia(result.config);
				a.runOnUiThread(() -> addProfile(p));
				toast("Server added");
			}
			catch (Exception e)
			{
				toast("Bad config: " + e.getMessage());
			}
			setBusy(false);
			return;
		}
		if (result.pending != null)
		{
			a.runOnUiThread(() -> showCaptcha(result.pending));
			return;
		}
		toast("No config (HTTP " + result.httpStatus + ") " + result.message);
		setBusy(false);
	}

	private void showCaptcha(AmneziaGateway.Pending pending)
	{
		Context context = requireContext();
		int pad = (int) (16 * getResources().getDisplayMetrics().density);
		LinearLayout box = new LinearLayout(context);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(pad, pad, pad, 0);

		ImageView image = new ImageView(context);
		Bitmap bmp = pending.captchaImage != null
			? BitmapFactory.decodeByteArray(pending.captchaImage, 0, pending.captchaImage.length) : null;
		if (bmp != null)
		{
			image.setImageBitmap(bmp);
			image.setAdjustViewBounds(true);
		}
		box.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		EditText input = new EditText(context);
		input.setHint("Captcha text");
		input.setSingleLine(true);
		box.addView(input);

		new AlertDialog.Builder(context)
			.setTitle("Captcha")
			.setView(box)
			.setCancelable(false)
			.setPositiveButton("OK", (d, w) -> {
				String solution = input.getText().toString();
				executor.execute(() -> {
					try
					{
						handleResult(AmneziaGateway.requestConfig(services, pending, solution));
					}
					catch (Exception e)
					{
						toast("Failed: " + e.getMessage());
						setBusy(false);
					}
				});
			})
			.setNegativeButton(android.R.string.cancel, (d, w) -> setBusy(false))
			.show();
	}
}
