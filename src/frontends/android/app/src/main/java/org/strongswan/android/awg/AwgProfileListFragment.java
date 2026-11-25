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
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.json.JSONObject;
import org.strongswan.android.R;
import org.strongswan.android.ui.widget.UiStyle;
import org.strongswan.android.utils.Str;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AwgProfileListFragment extends Fragment
{
	public static final String PROFILES_CHANGED = "org.strongswan.android.AWG_PROFILES_CHANGED";
	private static final int MENU_DELETE = 3001;
	private static final int MENU_COPY = 3002;

	private AwgProfileStore store;
	private final List<JSONObject> profiles = new ArrayList<JSONObject>();
	private AwgProfileAdapter adapter;
	private JSONObject pendingProfile;
	private View emptyView;
	private ListView list;
	private Button getFree;
	private ExtendedFloatingActionButton fab;
	private volatile boolean pinging;
	private volatile boolean fetching;
	private volatile boolean flagging;
	private static final java.util.Set<String> GEO_TRIED = new java.util.HashSet<String>();
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private AmneziaGateway.Services services;

	private final ActivityResultLauncher<Intent> vpnPermission =
		registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			if (result.getResultCode() == Activity.RESULT_OK && pendingProfile != null)
			{
				AwgVpnService.start(requireContext(), pendingProfile);
			}
		});

	private final BroadcastReceiver stateReceiver = new BroadcastReceiver()
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

	private final BroadcastReceiver changedReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			reload();
		}
	};

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		final Context context = requireContext();
		store = new AwgProfileStore(context);
		profiles.clear();
		profiles.addAll(store.load());
		int pad = UiStyle.dp(context, 24);

		FrameLayout root = new FrameLayout(context);

		list = new ListView(context);
		list.setDivider(null);
		list.setDividerHeight(0);
		list.setClipToPadding(false);
		list.setPadding(0, 0, 0, UiStyle.dp(context, 88));
		adapter = new AwgProfileAdapter(context, profiles);
		adapter.setActionListener(new AwgProfileAdapter.ActionListener()
		{
			@Override
			public void onEdit(JSONObject profile)
			{
				edit(profile);
			}

			@Override
			public void onShare(JSONObject profile)
			{
				share(profile);
			}

			@Override
			public void onDelete(JSONObject profile)
			{
				confirmDelete(profile);
			}
		});
		list.setAdapter(adapter);
		list.setOnItemClickListener((parent, view, position, id) -> {
			JSONObject p = adapter.getItem(position);
			if (p == null)
			{
				return;
			}
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
		list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
		list.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener()
		{
			@Override
			public void onItemCheckedStateChanged(android.view.ActionMode mode, int position, long id, boolean checked)
			{
				mode.setTitle(Str.get(R.string.selected_count, list.getCheckedItemCount()));
			}

			@Override
			public boolean onCreateActionMode(android.view.ActionMode mode, Menu menu)
			{
				menu.add(Menu.NONE, MENU_COPY, 0, R.string.action_copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
				menu.add(Menu.NONE, MENU_DELETE, 1, R.string.action_delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
				return true;
			}

			@Override
			public boolean onPrepareActionMode(android.view.ActionMode mode, Menu menu)
			{
				return false;
			}

			@Override
			public boolean onActionItemClicked(android.view.ActionMode mode, MenuItem item)
			{
				final List<JSONObject> selected = new ArrayList<JSONObject>();
				SparseBooleanArray checked = list.getCheckedItemPositions();
				for (int i = 0; i < list.getCount(); i++)
				{
					if (checked.get(i))
					{
						JSONObject p = adapter.getItem(i);
						if (p != null)
						{
							selected.add(p);
						}
					}
				}
				if (selected.isEmpty())
				{
					mode.finish();
					return true;
				}
				if (item.getItemId() == MENU_COPY)
				{
					StringBuilder sb = new StringBuilder();
					for (JSONObject p : selected)
					{
						try
						{
							if (sb.length() > 0)
							{
								sb.append('\n');
							}
							sb.append(AwgShare.toVpnKey(p));
						}
						catch (Exception ignored)
						{
						}
					}
					ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
					if (cm != null)
					{
						cm.setPrimaryClip(ClipData.newPlainText("AmneziaWG", sb.toString()));
						Toast.makeText(requireContext(), Str.get(R.string.awg_copy_done, selected.size()), Toast.LENGTH_SHORT).show();
					}
					mode.finish();
					return true;
				}
				if (item.getItemId() == MENU_DELETE)
				{
					mode.finish();
					new AlertDialog.Builder(requireActivity())
						.setTitle(R.string.delete_profiles_title)
						.setMessage(Str.get(R.string.delete_profiles_msg, selected.size()))
						.setPositiveButton(R.string.action_delete, (d, w) -> removeProfiles(selected))
						.setNegativeButton(android.R.string.cancel, null)
						.show();
					return true;
				}
				return false;
			}

			@Override
			public void onDestroyActionMode(android.view.ActionMode mode)
			{
				list.clearChoices();
				adapter.notifyDataSetChanged();
			}
		});
		root.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		LinearLayout empty = new LinearLayout(context);
		empty.setOrientation(LinearLayout.VERTICAL);
		empty.setGravity(Gravity.CENTER);
		empty.setPadding(pad, pad, pad, pad);
		TextView hint = new TextView(context);
		hint.setText(R.string.no_awg_profiles);
		hint.setGravity(Gravity.CENTER);
		hint.setTextSize(16);
		hint.setAlpha(0.7f);
		empty.addView(hint);
		getFree = new Button(context);
		getFree.setText(R.string.awg_get_server);
		UiStyle.fetchButton(getFree);
		getFree.setOnClickListener(v -> fetchFree());
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.topMargin = pad;
		empty.addView(getFree, lp);
		emptyView = empty;
		root.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		fab = new ExtendedFloatingActionButton(context);
		fab.setText(R.string.awg_get_server);
		fab.setIconResource(R.drawable.ic_add);
		fab.setIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#04210F")));
		fab.setTextColor(Color.parseColor("#04210F"));
		fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4ADE80")));
		fab.setOnClickListener(v -> fetchFree());
		FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		flp.gravity = Gravity.BOTTOM | Gravity.END;
		int m = UiStyle.dp(context, 16);
		flp.setMargins(m, m, m, m);
		root.addView(fab, flp);

		updateEmpty();
		return root;
	}

	@Override
	public void onStart()
	{
		super.onStart();
		ContextCompat.registerReceiver(requireContext(), stateReceiver, new IntentFilter(AwgVpnService.ACTION_STATE),
			ContextCompat.RECEIVER_NOT_EXPORTED);
		LocalBroadcastManager.getInstance(requireContext()).registerReceiver(changedReceiver, new IntentFilter(PROFILES_CHANGED));
		reload();
		fillMissingFlags();
	}

	private void fillMissingFlags()
	{
		if (flagging || adapter == null)
		{
			return;
		}
		final List<JSONObject> todo = new ArrayList<JSONObject>();
		for (JSONObject p : profiles)
		{
			String id = p.optString("id");
			if (!p.has("country") && !GEO_TRIED.contains(id))
			{
				todo.add(p);
			}
		}
		if (todo.isEmpty())
		{
			return;
		}
		flagging = true;
		executor.execute(() -> {
			final java.util.Map<String, String> found = new java.util.HashMap<String, String>();
			for (JSONObject p : todo)
			{
				found.put(p.optString("id"), AwgGeo.lookup(p.optString("host")));
			}
			Activity a = getActivity();
			if (a == null)
			{
				flagging = false;
				return;
			}
			a.runOnUiThread(() -> {
				try
				{
					boolean changed = false;
					for (JSONObject p : profiles)
					{
						String id = p.optString("id");
						String cc = found.get(id);
						if (cc == null)
						{
							continue;
						}
						GEO_TRIED.add(id);
						if (cc.isEmpty())
						{
							continue;
						}
						int index = 1;
						for (JSONObject q : profiles)
						{
							if (q != p && cc.equals(q.optString("country")))
							{
								index++;
							}
						}
						AwgGeo.applyCountry(p, cc, index);
						changed = true;
					}
					if (changed)
					{
						persist();
					}
				}
				catch (Exception ignored)
				{
				}
				flagging = false;
			});
		});
	}

	@Override
	public void onStop()
	{
		try
		{
			requireContext().unregisterReceiver(stateReceiver);
		}
		catch (Exception ignored)
		{
		}
		try
		{
			LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(changedReceiver);
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

	private void reload()
	{
		if (store == null || adapter == null)
		{
			return;
		}
		profiles.clear();
		profiles.addAll(store.load());
		adapter.notifyDataSetChanged();
		updateEmpty();
	}

	private void updateEmpty()
	{
		boolean isEmpty = profiles.isEmpty();
		if (emptyView != null)
		{
			emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
		}
		if (list != null)
		{
			list.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
		}
		if (fab != null)
		{
			fab.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
		}
	}

	private void persist()
	{
		store.save(profiles);
		adapter.notifyDataSetChanged();
		updateEmpty();
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

	private void removeProfiles(List<JSONObject> targets)
	{
		for (JSONObject t : targets)
		{
			if (AwgVpnService.isActive(t.optString("id")))
			{
				AwgVpnService.stop(requireContext());
			}
		}
		profiles.removeAll(targets);
		persist();
		Toast.makeText(requireContext(), Str.get(R.string.profiles_deleted_count, targets.size()), Toast.LENGTH_SHORT).show();
	}

	private void confirmDelete(final JSONObject p)
	{
		new AlertDialog.Builder(requireActivity())
			.setMessage(Str.get(R.string.awg_delete_confirm, p.optString("name")))
			.setPositiveButton(R.string.action_delete, (d, w) -> {
				if (AwgVpnService.isActive(p.optString("id")))
				{
					AwgVpnService.stop(requireContext());
				}
				profiles.remove(p);
				persist();
				Toast.makeText(requireContext(), R.string.profile_deleted, Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	private void edit(final JSONObject p)
	{
		AwgProfileEditor.show(requireActivity(), p, updated -> {
			int index = -1;
			for (int i = 0; i < profiles.size(); i++)
			{
				if (profiles.get(i).optString("id").equals(p.optString("id")))
				{
					index = i;
					break;
				}
			}
			if (index < 0)
			{
				return;
			}
			if (AwgVpnService.isActive(p.optString("id")))
			{
				AwgVpnService.stop(requireContext());
			}
			profiles.set(index, updated);
			persist();
			Toast.makeText(requireContext(), R.string.profile_updated, Toast.LENGTH_SHORT).show();
		});
	}

	private void share(final JSONObject p)
	{
		new AlertDialog.Builder(requireActivity())
			.setTitle(R.string.share_title)
			.setItems(new CharSequence[]{getString(R.string.awg_share_key), getString(R.string.awg_share_conf)}, (d, which) -> {
				try
				{
					String text = which == 0 ? AwgShare.toVpnKey(p) : AwgShare.toConf(p);
					Intent send = new Intent(Intent.ACTION_SEND);
					send.setType("text/plain");
					send.putExtra(Intent.EXTRA_TEXT, text);
					startActivity(Intent.createChooser(send, getString(R.string.share_title)));
				}
				catch (Exception e)
				{
					Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
				}
			})
			.show();
	}

	public void pingAllPublic()
	{
		if (profiles.isEmpty() || pinging)
		{
			return;
		}
		if (AwgVpnService.isActive())
		{
			Toast.makeText(requireContext(), R.string.ping_disconnect_first, Toast.LENGTH_SHORT).show();
			return;
		}
		final List<JSONObject> copy = new ArrayList<JSONObject>(profiles);
		final String running = getString(R.string.ping_running);
		final String timeout = getString(R.string.ping_timeout);
		adapter.clearPings();
		for (JSONObject p : copy)
		{
			adapter.setPing(p.optString("id"), running);
		}
		pinging = true;
		new Thread(() -> {
			ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, Math.max(1, copy.size())));
			List<java.util.concurrent.Future<?>> futures = new ArrayList<java.util.concurrent.Future<?>>();
			try
			{
				for (final JSONObject p : copy)
				{
					futures.add(pool.submit(() -> {
						int ms = AwgPing.ping(p.optString("host"));
						final String result = ms >= 0 ? ms + " ms" : timeout;
						Activity a = getActivity();
						if (a != null)
						{
							a.runOnUiThread(() -> {
								if (adapter != null)
								{
									adapter.setPing(p.optString("id"), result);
								}
							});
						}
					}));
				}
				for (java.util.concurrent.Future<?> f : futures)
				{
					try
					{
						f.get();
					}
					catch (Exception ignored)
					{
					}
				}
			}
			finally
			{
				pool.shutdownNow();
				pinging = false;
			}
		}).start();
	}

	public void deleteNoPingWithConfirm()
	{
		if (adapter == null || profiles.isEmpty())
		{
			Toast.makeText(requireContext(), R.string.delete_no_ping_none, Toast.LENGTH_SHORT).show();
			return;
		}
		if (!adapter.isPingFinished())
		{
			Toast.makeText(requireContext(), R.string.delete_no_ping_run_first, Toast.LENGTH_SHORT).show();
			return;
		}
		final List<JSONObject> targets = new ArrayList<JSONObject>();
		for (JSONObject p : profiles)
		{
			if (!adapter.hasMsPing(p.optString("id")) && !AwgVpnService.isActive(p.optString("id")))
			{
				targets.add(p);
			}
		}
		if (targets.isEmpty())
		{
			Toast.makeText(requireContext(), R.string.delete_no_ping_none, Toast.LENGTH_SHORT).show();
			return;
		}
		new AlertDialog.Builder(requireActivity())
			.setTitle(R.string.delete_no_ping)
			.setMessage(getString(R.string.delete_no_ping_confirm, targets.size()))
			.setPositiveButton(android.R.string.ok, (d, w) -> {
				profiles.removeAll(targets);
				persist();
				Toast.makeText(requireContext(), R.string.profiles_deleted, Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	public void fetchFreePublic()
	{
		fetchFree();
	}

	private void setBusy(final boolean busy)
	{
		fetching = busy;
		Activity a = getActivity();
		if (a == null)
		{
			return;
		}
		a.runOnUiThread(() -> {
			if (getFree != null)
			{
				getFree.setEnabled(!busy);
				getFree.setAlpha(busy ? 0.6f : 1f);
			}
			if (fab != null)
			{
				fab.setEnabled(!busy);
				fab.setAlpha(busy ? 0.6f : 1f);
			}
		});
	}

	private void toast(final String message)
	{
		Activity a = getActivity();
		if (a != null)
		{
			a.runOnUiThread(() -> Toast.makeText(a, message, Toast.LENGTH_LONG).show());
		}
	}

	private void fetchFree()
	{
		if (fetching)
		{
			return;
		}
		setBusy(true);
		toast(Str.get(R.string.awg_requesting));
		executor.execute(() -> {
			try
			{
				services = AmneziaGateway.listServices();
				handleResult(AmneziaGateway.requestConfig(services, null, null));
			}
			catch (Exception e)
			{
				toast(Str.get(R.string.awg_failed, e.getMessage()));
				setBusy(false);
			}
		});
	}

	private void handleResult(AmneziaGateway.ConfigResult result)
	{
		final Activity a = getActivity();
		if (a == null)
		{
			return;
		}
		if (result.config != null)
		{
			try
			{
				final JSONObject p = AwgConfig.fromAmnezia(result.config);
				final String country = p.has("country") ? p.optString("country") : AwgGeo.lookup(p.optString("host"));
				a.runOnUiThread(() -> {
					try
					{
						for (JSONObject existing : profiles)
						{
							if (existing.optString("host").equals(p.optString("host"))
								&& existing.optString("client_priv_key").equals(p.optString("client_priv_key")))
							{
								return;
							}
						}
						int index = 1;
						for (JSONObject existing : profiles)
						{
							if (country.equals(existing.optString("country")))
							{
								index++;
							}
						}
						AwgGeo.applyCountry(p, country, index);
						if (!p.has("id"))
						{
							p.put("id", UUID.randomUUID().toString());
						}
						profiles.add(p);
						persist();
						Toast.makeText(a, R.string.awg_server_added, Toast.LENGTH_SHORT).show();
					}
					catch (Exception e)
					{
						Toast.makeText(a, Str.get(R.string.awg_bad_config, e.getMessage()), Toast.LENGTH_LONG).show();
					}
				});
			}
			catch (Exception e)
			{
				toast(Str.get(R.string.awg_bad_config, e.getMessage()));
			}
			setBusy(false);
			return;
		}
		if (result.pending != null)
		{
			a.runOnUiThread(() -> showCaptcha(result.pending));
			return;
		}
		toast(Str.get(R.string.awg_no_config, result.httpStatus, result.message));
		setBusy(false);
	}

	private void showCaptcha(final AmneziaGateway.Pending pending)
	{
		Context context = requireContext();
		int pad = UiStyle.dp(context, 16);
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

		final EditText input = new EditText(context);
		input.setHint(R.string.awg_captcha_hint);
		input.setSingleLine(true);
		box.addView(input);

		new AlertDialog.Builder(context)
			.setTitle(R.string.awg_captcha_title)
			.setView(box)
			.setCancelable(false)
			.setPositiveButton(android.R.string.ok, (d, w) -> {
				final String solution = input.getText().toString();
				executor.execute(() -> {
					try
					{
						handleResult(AmneziaGateway.requestConfig(services, pending, solution));
					}
					catch (Exception e)
					{
						toast(Str.get(R.string.awg_failed, e.getMessage()));
						setBusy(false);
					}
				});
			})
			.setNegativeButton(android.R.string.cancel, (d, w) -> setBusy(false))
			.show();
	}
}
