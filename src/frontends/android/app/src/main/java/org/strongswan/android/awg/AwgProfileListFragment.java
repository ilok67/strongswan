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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import org.strongswan.android.R;
import org.strongswan.android.v2ray.SingBoxVpnService;
import org.strongswan.android.v2ray.V2RayPing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
	private LinearLayout emptyContainer;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private AmneziaGateway.Services services;
	private final Map<String, String> pings = new HashMap<String, String>();
	private volatile boolean pinging;

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
		float density = getResources().getDisplayMetrics().density;
		int pad = (int) (12 * density);

		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);

		// Free server button (styled, no import button)
		LinearLayout buttons = new LinearLayout(context);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		buttons.setPadding(pad, pad, pad, pad / 2);
		buttons.setGravity(Gravity.CENTER);

		getFree = createStyledButton(context, getString(R.string.fetch_servers));
		getFree.setOnClickListener(v -> fetchFree());
		LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		buttons.addView(getFree, btnLp);
		root.addView(buttons);

		// Empty state
		emptyContainer = new LinearLayout(context);
		emptyContainer.setOrientation(LinearLayout.VERTICAL);
		emptyContainer.setGravity(Gravity.CENTER);
		emptyContainer.setPadding(pad * 2, pad * 3, pad * 2, pad);
		hint = new TextView(context);
		hint.setText(getString(R.string.no_profiles));
		hint.setTextColor(0x99FFFFFF);
		hint.setGravity(Gravity.CENTER);
		hint.setTextSize(15);
		emptyContainer.addView(hint);
		root.addView(emptyContainer);

		ListView list = new ListView(context);
		list.setDivider(null);
		list.setDividerHeight(0);
		list.setClipToPadding(false);
		list.setPadding(0, 0, 0, pad);

		adapter = new ArrayAdapter<JSONObject>(context, 0, profiles)
		{
			@Override
			public View getView(int position, View convertView, ViewGroup parent)
			{
				return buildCard(getContext(), getItem(position), position);
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
				// Stop other protocols before connecting
				if (SingBoxVpnService.isRunning())
				{
					SingBoxVpnService.stop(requireContext());
				}
				connect(p);
			}
		});
		root.addView(list, new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
		updateEmpty();
		return root;
	}

	private Button createStyledButton(Context context, String text)
	{
		Button btn = new Button(context);
		btn.setText(text);
		btn.setAllCaps(false);
		btn.setTextColor(Color.WHITE);
		btn.setTextSize(14);
		btn.setTypeface(null, Typeface.BOLD);
		int hPad = (int) (20 * getResources().getDisplayMetrics().density);
		int vPad = (int) (12 * getResources().getDisplayMetrics().density);
		btn.setPadding(hPad, vPad, hPad, vPad);
		btn.setBackgroundResource(R.drawable.btn_fetch_servers);
		return btn;
	}

	private View buildCard(Context context, JSONObject p, int position)
	{
		float density = getResources().getDisplayMetrics().density;
		int pad = (int) (14 * density);
		int margin = (int) (10 * density);

		LinearLayout card = new LinearLayout(context);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(pad, pad, pad, pad);
		LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		cardLp.setMargins(margin, margin / 2, margin, margin / 2);
		card.setLayoutParams(cardLp);

		boolean active = AwgVpnService.isActive(p.optString("id"));
		GradientDrawable bg = new GradientDrawable();
		bg.setCornerRadius(16 * density);
		if (active)
		{
			bg.setColor(0xFF163A2A);
			bg.setStroke((int) (2 * density), 0xFF4ADE80);
		}
		else
		{
			bg.setColor(0xFF1E293B);
			bg.setStroke((int) density, 0xFF334155);
		}
		card.setBackground(bg);

		// Top row: flag + name + actions
		LinearLayout top = new LinearLayout(context);
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.setGravity(Gravity.CENTER_VERTICAL);

		String name = p.optString("name", "AmneziaWG");
		String host = p.optString("host", "");
		String flag = countryFlag(host, name);

		TextView nameView = new TextView(context);
		nameView.setText((active ? "● " : "") + flag + " " + name);
		nameView.setTextColor(active ? 0xFF4ADE80 : Color.WHITE);
		nameView.setTextSize(15);
		nameView.setTypeface(null, Typeface.BOLD);
		nameView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		top.addView(nameView);

		// Action buttons: edit / share / delete
		int iconSize = (int) (36 * density);
		top.addView(makeActionBtn(context, "✎", 0xFF60A5FA, v -> showEditDialog(p)));
		top.addView(makeActionBtn(context, "↗", 0xFF4ADE80, v -> shareProfile(p)));
		top.addView(makeActionBtn(context, "✕", 0xFFEF4444, v -> confirmDelete(position)));

		card.addView(top);

		// Host:port + ping
		LinearLayout bottom = new LinearLayout(context);
		bottom.setOrientation(LinearLayout.HORIZONTAL);
		bottom.setPadding(0, (int) (6 * density), 0, 0);

		TextView hostView = new TextView(context);
		int port = p.optInt("port", 0);
		hostView.setText(port > 0 ? host + ":" + port : host);
		hostView.setTextColor(0x99FFFFFF);
		hostView.setTextSize(13);
		hostView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		bottom.addView(hostView);

		TextView pingView = new TextView(context);
		String id = p.optString("id");
		String ping = pings.get(id);
		if (ping == null)
		{
			pingView.setText("—");
			pingView.setTextColor(0x99FFFFFF);
		}
		else if (getString(R.string.ping_running).equals(ping))
		{
			pingView.setText(ping);
			pingView.setTextColor(0xFFFFA000);
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
		pingView.setTextSize(13);
		bottom.addView(pingView);

		card.addView(bottom);
		return card;
	}

	private TextView makeActionBtn(Context context, String label, int color, View.OnClickListener listener)
	{
		float density = getResources().getDisplayMetrics().density;
		TextView btn = new TextView(context);
		btn.setText(label);
		btn.setTextColor(color);
		btn.setTextSize(16);
		btn.setGravity(Gravity.CENTER);
		int size = (int) (36 * density);
		btn.setWidth(size);
		btn.setHeight(size);
		btn.setOnClickListener(listener);
		// Prevent click from propagating to card
		btn.setClickable(true);
		btn.setFocusable(true);
		return btn;
	}

	private String countryFlag(String host, String name)
	{
		// Simple heuristic from name or host
		String lower = (name + " " + host).toLowerCase();
		if (lower.contains("germany") || lower.contains("de ") || lower.contains(".de")) return "🇩🇪";
		if (lower.contains("france") || lower.contains("fr ") || lower.contains(".fr")) return "🇫🇷";
		if (lower.contains("netherlands") || lower.contains("nl ") || lower.contains(".nl")) return "🇳🇱";
		if (lower.contains("finland") || lower.contains("fi ") || lower.contains(".fi")) return "🇫🇮";
		if (lower.contains("sweden") || lower.contains("se ") || lower.contains(".se")) return "🇸🇪";
		if (lower.contains("turkey") || lower.contains("tr ") || lower.contains(".tr")) return "🇹🇷";
		if (lower.contains("usa") || lower.contains("us ") || lower.contains(".us") || lower.contains("america")) return "🇺🇸";
		if (lower.contains("uk") || lower.contains("britain") || lower.contains(".uk") || lower.contains("england")) return "🇬🇧";
		if (lower.contains("canada") || lower.contains("ca ") || lower.contains(".ca")) return "🇨🇦";
		if (lower.contains("japan") || lower.contains("jp ") || lower.contains(".jp")) return "🇯🇵";
		if (lower.contains("singapore") || lower.contains("sg ")) return "🇸🇬";
		if (lower.contains("iran") || lower.contains("ir ") || lower.contains(".ir")) return "🇮🇷";
		if (lower.contains("russia") || lower.contains("ru ") || lower.contains(".ru")) return "🇷🇺";
		if (lower.contains("poland") || lower.contains("pl ")) return "🇵🇱";
		if (lower.contains("italy") || lower.contains("it ")) return "🇮🇹";
		if (lower.contains("spain") || lower.contains("es ")) return "🇪🇸";
		return "🌐";
	}

	private void shareProfile(JSONObject p)
	{
		try
		{
			// Prefer vpn:// key if available, otherwise conf-like summary
			String share = p.optString("share", "");
			if (share.isEmpty())
			{
				StringBuilder sb = new StringBuilder();
				sb.append("[Interface]\n");
				sb.append("PrivateKey = ").append(p.optString("private_key")).append("\n");
				sb.append("Address = ").append(p.optString("address")).append("\n");
				if (p.has("dns")) sb.append("DNS = ").append(p.optString("dns")).append("\n");
				sb.append("\n[Peer]\n");
				sb.append("PublicKey = ").append(p.optString("public_key")).append("\n");
				sb.append("Endpoint = ").append(p.optString("host")).append(":").append(p.optInt("port")).append("\n");
				if (p.has("allowed_ips")) sb.append("AllowedIPs = ").append(p.optString("allowed_ips")).append("\n");
				// Amnezia obfuscation params
				for (String key : AwgConfig.OPTIONAL_KEYS)
				{
					if (p.has(key))
					{
						sb.append(key).append(" = ").append(p.opt(key)).append("\n");
					}
				}
				share = sb.toString();
			}
			ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
			if (cm != null)
			{
				cm.setPrimaryClip(ClipData.newPlainText("AmneziaWG", share));
				Toast.makeText(requireContext(), getString(R.string.v2ray_copy), Toast.LENGTH_SHORT).show();
			}
			Intent send = new Intent(Intent.ACTION_SEND);
			send.setType("text/plain");
			send.putExtra(Intent.EXTRA_TEXT, share);
			startActivity(Intent.createChooser(send, p.optString("name")));
		}
		catch (Exception e)
		{
			Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}

	private void showEditDialog(JSONObject p)
	{
		Context context = requireContext();
		float density = getResources().getDisplayMetrics().density;
		int pad = (int) (16 * density);

		LinearLayout box = new LinearLayout(context);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(pad, pad, pad, 0);

		final EditText nameInput = new EditText(context);
		nameInput.setHint(getString(R.string.v2ray_profile_name));
		nameInput.setText(p.optString("name"));
		nameInput.setSingleLine(true);
		box.addView(nameInput);

		final EditText hostInput = new EditText(context);
		hostInput.setHint(getString(R.string.v2ray_server));
		hostInput.setText(p.optString("host"));
		hostInput.setSingleLine(true);
		box.addView(hostInput);

		final EditText portInput = new EditText(context);
		portInput.setHint(getString(R.string.v2ray_port));
		portInput.setText(String.valueOf(p.optInt("port", 0)));
		portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
		portInput.setSingleLine(true);
		box.addView(portInput);

		new AlertDialog.Builder(context)
			.setTitle(getString(R.string.v2ray_edit_profile))
			.setView(box)
			.setPositiveButton(getString(R.string.v2ray_save), (d, w) -> {
				try
				{
					String newName = nameInput.getText().toString().trim();
					String newHost = hostInput.getText().toString().trim();
					int newPort = Integer.parseInt(portInput.getText().toString().trim());
					if (!newName.isEmpty()) p.put("name", newName);
					if (!newHost.isEmpty()) p.put("host", newHost);
					if (newPort > 0) p.put("port", newPort);
					store.save(profiles);
					adapter.notifyDataSetChanged();
				}
				catch (Exception e)
				{
					Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
				}
			})
			.setNegativeButton(getString(R.string.v2ray_cancel), null)
			.show();
	}

	/** Run ping for all Amnezia profiles and optionally remove those without response */
	public void runPing(boolean removeDead)
	{
		if (pinging || profiles.isEmpty()) return;
		pinging = true;
		pings.clear();
		for (JSONObject p : profiles)
		{
			pings.put(p.optString("id"), getString(R.string.ping_running));
		}
		if (adapter != null) adapter.notifyDataSetChanged();

		executor.execute(() -> {
			List<JSONObject> dead = new ArrayList<JSONObject>();
			for (JSONObject p : new ArrayList<JSONObject>(profiles))
			{
				String host = p.optString("host");
				int port = p.optInt("port", 0);
				String id = p.optString("id");
				String result;
				try
				{
					Context ctx = getContext();
					if (ctx == null)
					{
						result = getString(R.string.ping_timeout);
						dead.add(p);
					}
					else
					{
						result = V2RayPing.ping(ctx, host, port > 0 ? port : 51820);
						// normalize "123 ms" -> "123ms"
						if (result != null && result.endsWith(" ms"))
						{
							result = result.replace(" ms", "ms");
						}
						if (result == null || result.contains("timeout") || result.equals("timeout"))
						{
							result = getString(R.string.ping_timeout);
							dead.add(p);
						}
					}
				}
				catch (Exception e)
				{
					result = getString(R.string.ping_timeout);
					dead.add(p);
				}
				pings.put(id, result);
				Activity a = getActivity();
				if (a != null)
				{
					a.runOnUiThread(() -> {
						if (adapter != null) adapter.notifyDataSetChanged();
					});
				}
			}
			if (removeDead && !dead.isEmpty())
			{
				Activity a = getActivity();
				if (a != null)
				{
					a.runOnUiThread(() -> {
						for (JSONObject d : dead)
						{
							if (AwgVpnService.isActive(d.optString("id")))
							{
								AwgVpnService.stop(requireContext());
							}
							profiles.remove(d);
							pings.remove(d.optString("id"));
						}
						store.save(profiles);
						if (adapter != null) adapter.notifyDataSetChanged();
						updateEmpty();
						Toast.makeText(a, getString(R.string.profiles_deleted), Toast.LENGTH_SHORT).show();
					});
				}
			}
			pinging = false;
		});
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
		if (emptyContainer != null)
		{
			emptyContainer.setVisibility(profiles.isEmpty() ? View.VISIBLE : View.GONE);
		}
		if (getFree != null)
		{
			// Keep free button always visible (also when list has items, as user requested style for empty tabs)
			getFree.setVisibility(View.VISIBLE);
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
		if (position < 0 || position >= profiles.size()) return;
		JSONObject p = profiles.get(position);
		new AlertDialog.Builder(requireContext())
			.setTitle(p.optString("name"))
			.setMessage(getString(R.string.delete_profile) + "?")
			.setPositiveButton(getString(R.string.v2ray_delete), (d, w) -> {
				if (AwgVpnService.isActive(p.optString("id")))
				{
					AwgVpnService.stop(requireContext());
				}
				profiles.remove(p);
				store.save(profiles);
				adapter.notifyDataSetChanged();
				updateEmpty();
			})
			.setNegativeButton(getString(R.string.v2ray_cancel), null)
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

	private void setBusy(boolean busy)
	{
		Activity a = getActivity();
		if (a == null) return;
		a.runOnUiThread(() -> {
			if (getFree != null)
			{
				getFree.setEnabled(!busy);
				getFree.setText(busy ? getString(R.string.fetch_servers_loading) : getString(R.string.fetch_servers));
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
				toast(getString(R.string.fetch_servers_failed, e.getMessage()));
				setBusy(false);
			}
		});
	}

	private void handleResult(AmneziaGateway.ConfigResult result)
	{
		Activity a = getActivity();
		if (a == null) return;
		if (result.config != null)
		{
			try
			{
				JSONObject p = AwgConfig.fromAmnezia(result.config);
				a.runOnUiThread(() -> addProfile(p));
				toast(getString(R.string.fetch_servers_done, 1));
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
		toast(getString(R.string.fetch_servers_failed, "HTTP " + result.httpStatus + " " + result.message));
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
		box.addView(image, new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		EditText input = new EditText(context);
		input.setHint("Captcha");
		input.setSingleLine(true);
		box.addView(input);

		new AlertDialog.Builder(context)
			.setTitle("Captcha")
			.setView(box)
			.setCancelable(false)
			.setPositiveButton(android.R.string.ok, (d, w) -> {
				String solution = input.getText().toString();
				executor.execute(() -> {
					try
					{
						handleResult(AmneziaGateway.requestConfig(services, pending, solution));
					}
					catch (Exception e)
					{
						toast(getString(R.string.fetch_servers_failed, e.getMessage()));
						setBusy(false);
					}
				});
			})
			.setNegativeButton(getString(R.string.v2ray_cancel), (d, w) -> setBusy(false))
			.show();
	}
}
