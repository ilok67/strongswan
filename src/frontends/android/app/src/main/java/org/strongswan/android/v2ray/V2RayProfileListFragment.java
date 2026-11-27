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
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.graphics.Color;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import org.strongswan.android.ui.widget.UiStyle;
import org.strongswan.android.utils.Str;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import org.strongswan.android.R;
import org.strongswan.android.ui.FreeServersActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.strongswan.android.v2ray.V2RayConfigTester;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class V2RayProfileListFragment extends Fragment
{
    private static final int MENU_EDIT = 1001;
    private static final int MENU_COPY = 1002;
    private static final int MENU_DELETE = 1003;

    private V2RayProfileStore store;
    private final List<String> links = new ArrayList<>();
    private V2RayProfileAdapter adapter;
    private String pendingLink;

    private ListView profileList;
    private ActionMode actionMode;
    private volatile boolean pinging;
    private volatile boolean fetching;
    private Button getFreeBtn;
    private ExtendedFloatingActionButton fab;
    private View emptyViewRef;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> vpnPermission =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK
                                && pendingLink != null)
                        {
                            SingBoxVpnService.start(
                                    requireContext(),
                                    pendingLink
                            );
                        }
                    }
            );

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
    private final BroadcastReceiver profilesChangedReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (adapter == null)
            {
                return;
            }
            links.clear();
            links.addAll(store.load());
            adapter.notifyDataSetChanged();
            updateEmptyVisibility();
        }
    };
    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState)
    {
        Context context = requireContext();

        store = new V2RayProfileStore(context);

        links.clear();
        links.addAll(store.load());

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        profileList = new ListView(context);
        profileList.setDivider(null);
        profileList.setDividerHeight(0);
        profileList.setClipToPadding(false);

        adapter = new V2RayProfileAdapter(context, links);
        adapter.setActionListener(new V2RayProfileAdapter.ActionListener()
        {
            @Override
            public void onEdit(String link)
            {
                editLink(link);
            }

            @Override
            public void onShare(String link)
            {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, link);
                startActivity(Intent.createChooser(send, getString(R.string.share_title)));
            }

            @Override
            public void onDelete(String link)
            {
                confirmDelete(link);
            }
        });
        profileList.setAdapter(adapter);

        LinearLayout emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        emptyView.setPadding(pad, pad, pad, pad);

        TextView hint = new TextView(context);
        hint.setText(R.string.no_singbox_profiles);
        hint.setGravity(android.view.Gravity.CENTER);
        emptyView.addView(hint);

        getFreeBtn = new Button(context);
        getFreeBtn.setText(R.string.awg_get_server);
        UiStyle.fetchButton(getFreeBtn);
        getFreeBtn.setOnClickListener(v -> fetchFree());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad;
        emptyView.addView(getFreeBtn, lp);
        emptyViewRef = emptyView;

        profileList.setEmptyView(emptyView);

        profileList.setOnItemClickListener(
                new AdapterView.OnItemClickListener()
                {
                    @Override
                    public void onItemClick(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id)
                    {
                        String link = adapter.getItem(position);

                        if (link == null)
                        {
                            return;
                        }

                        if (link.equals(SingBoxVpnService.getLink())
                                && SingBoxVpnService.isRunning())
                        {
                            SingBoxVpnService.stop(requireContext());
                        }
                        else
                        {
                            connect(link);
                        }
                    }
                }
        );


        profileList.setChoiceMode(
                ListView.CHOICE_MODE_MULTIPLE_MODAL
        );

        profileList.setMultiChoiceModeListener(
                new AbsListView.MultiChoiceModeListener()
                {
                    @Override
                    public void onItemCheckedStateChanged(
                            ActionMode mode,
                            int position,
                            long id,
                            boolean checked)
                    {
                        int count = profileList.getCheckedItemCount();

                        mode.setTitle(org.strongswan.android.utils.Str.get(R.string.selected_count, count));

                        MenuItem editItem =
                                mode.getMenu().findItem(MENU_EDIT);

                        if (editItem != null)
                        {
                            // Edit is available only for one profile.
                            editItem.setEnabled(count == 1);
                        }
                    }

                    @Override
                    public boolean onCreateActionMode(
                            ActionMode mode,
                            Menu menu)
                    {
                        actionMode = mode;

                        menu.add(
                                Menu.NONE,
                                MENU_EDIT,
                                0,
                                R.string.action_edit
                        ).setShowAsAction(
                                MenuItem.SHOW_AS_ACTION_IF_ROOM
                        );

                        menu.add(
                                Menu.NONE,
                                MENU_COPY,
                                1,
                                R.string.action_copy
                        ).setShowAsAction(
                                MenuItem.SHOW_AS_ACTION_IF_ROOM
                        );

                        menu.add(
                                Menu.NONE,
                                MENU_DELETE,
                                2,
                                R.string.action_delete
                        ).setShowAsAction(
                                MenuItem.SHOW_AS_ACTION_IF_ROOM
                        );

                        return true;
                    }

                    @Override
                    public boolean onPrepareActionMode(
                            ActionMode mode,
                            Menu menu)
                    {
                        return false;
                    }

                    @Override
                    public boolean onActionItemClicked(
                            ActionMode mode,
                            MenuItem item)
                    {
                        final List<String> selectedLinks =
                                new ArrayList<>();

                        android.util.SparseBooleanArray checkedItems =
                                profileList.getCheckedItemPositions();

                        for (int i = 0; i < profileList.getCount(); i++)
                        {
                            if (checkedItems.get(i))
                            {
                                String link = adapter.getItem(i);

                                if (link != null)
                                {
                                    selectedLinks.add(link);
                                }
                            }
                        }

                        if (selectedLinks.isEmpty())
                        {
                            mode.finish();
                            return true;
                        }

                        int itemId = item.getItemId();

                        /*
                         * EDIT
                         * Only one profile can be edited at a time.
                         */
                        if (itemId == MENU_EDIT)
                        {
                            if (selectedLinks.size() != 1)
                            {
                                return true;
                            }

                            final String selectedLink =
                                    selectedLinks.get(0);

                            mode.finish();

                            V2RayProfileEditor.show(
                                    requireContext(),
                                    selectedLink,
                                    false,
                                    (oldLink, newLink, copyMode) ->
                                    {
                                        int index = links.indexOf(oldLink);

                                        if (index < 0)
                                        {
                                            Toast.makeText(
                                                    requireContext(),
                                                    R.string.profile_not_found,
                                                    Toast.LENGTH_SHORT
                                            ).show();

                                            return;
                                        }

                                        if (!oldLink.equals(newLink)
                                                && links.contains(newLink))
                                        {
                                            Toast.makeText(
                                                    requireContext(),
                                                    R.string.profile_exists,
                                                    Toast.LENGTH_LONG
                                            ).show();

                                            return;
                                        }

                                        // Stop VPN if it uses the old link.
                                        if (oldLink.equals(
                                                SingBoxVpnService.getLink())
                                                && SingBoxVpnService.isRunning())
                                        {
                                            SingBoxVpnService.stop(
                                                    requireContext()
                                            );
                                        }

                                        links.set(index, newLink);

                                        store.save(links);
                                        adapter.notifyDataSetChanged();

                                        Toast.makeText(
                                                requireContext(),
                                                R.string.profile_updated,
                                                Toast.LENGTH_SHORT
                                        ).show();
                                    }
                            );

                            return true;
                        }


                        if (itemId == MENU_COPY)
                        {
                            StringBuilder clipboardText =
                                    new StringBuilder();

                            for (String link : selectedLinks)
                            {
                                if (clipboardText.length() > 0)
                                {
                                    clipboardText.append('\n');
                                }

                                clipboardText.append(link);
                            }

                            ClipboardManager clipboard =
                                    (ClipboardManager)
                                            requireContext().getSystemService(
                                                    Context.CLIPBOARD_SERVICE
                                            );

                            if (clipboard != null)
                            {
                                ClipData clip = ClipData.newPlainText(
                                        "V2Ray profile links",
                                        clipboardText.toString()
                                );

                                clipboard.setPrimaryClip(clip);

                                Toast.makeText(
                                        requireContext(),
                                        org.strongswan.android.utils.Str.get(
                                                R.string.awg_copy_done, selectedLinks.size()),
                                        Toast.LENGTH_SHORT
                                ).show();
                            }

                            mode.finish();
                            return true;
                        }


                        if (itemId == MENU_DELETE)
                        {
                            mode.finish();

                            final int deleteCount = selectedLinks.size();

                            new AlertDialog.Builder(requireActivity())
                                    .setTitle(R.string.delete_profiles_title)
                                    .setMessage(
                                            org.strongswan.android.utils.Str.get(
                                                    R.string.delete_profiles_msg, deleteCount)
                                    )
                                    .setPositiveButton(
                                            R.string.action_delete,
                                            (dialog, which) ->
                                            {
                                                // Stop VPN if its profile
                                                // is among the deleted ones.
                                                String runningLink =
                                                        SingBoxVpnService.getLink();

                                                if (runningLink != null
                                                        && selectedLinks.contains(
                                                                runningLink)
                                                        && SingBoxVpnService.isRunning())
                                                {
                                                    SingBoxVpnService.stop(
                                                            requireContext()
                                                    );
                                                }

                                                links.removeAll(selectedLinks);

                                                store.save(links);
                                                adapter.notifyDataSetChanged();

                                                Toast.makeText(
                                                        requireContext(),
                                                        org.strongswan.android.utils.Str.get(
                                                                R.string.profiles_deleted_count, deleteCount),
                                                        Toast.LENGTH_SHORT
                                                ).show();
                                            }
                                    )
                                    .setNegativeButton(
                                            android.R.string.cancel,
                                            null
                                    )
                                    .show();

                            return true;
                        }

                        return false;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode mode)
                    {
                        actionMode = null;

                        profileList.clearChoices();
                        adapter.notifyDataSetChanged();
                    }
                }
        );

        LinearLayout.LayoutParams fill = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f);
        FrameLayout frame = new FrameLayout(context);
        LinearLayout.LayoutParams fillMatch = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        frame.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(emptyView, fill);
        root.addView(profileList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        Context fabContext = new android.view.ContextThemeWrapper(
                context, com.google.android.material.R.style.Theme_MaterialComponents_DayNight);
        fab = new ExtendedFloatingActionButton(fabContext);
        fab.setText(R.string.awg_get_server);
        fab.setIconResource(R.drawable.ic_add);
        fab.setIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#04210F")));
        fab.setTextColor(Color.parseColor("#04210F"));
        fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4ADE80")));
        fab.setOnClickListener(v -> fetchFree());
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        int m = UiStyle.dp(context, 16);
        flp.setMargins(m, m, m, m);
        frame.addView(fab, flp);

        profileList.setClipToPadding(false);
        profileList.setPadding(0, 0, 0, UiStyle.dp(context, 88));
        updateEmptyVisibility();

        return frame;
    }

    private void updateEmptyVisibility()
    {
        boolean isEmpty = links.isEmpty();
        if (emptyViewRef != null)
        {
            emptyViewRef.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (profileList != null)
        {
            profileList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        if (fab != null)
        {
            fab.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    private void fetchFree()
    {
        if (fetching)
        {
            return;
        }
        fetching = true;
        if (getFreeBtn != null) getFreeBtn.setEnabled(false);
        if (fab != null) fab.setEnabled(false);
        Toast.makeText(requireContext(), R.string.fetch_servers_loading, Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try
            {
                final int added = FreeServersActivity.importV2RayProfiles(requireContext().getApplicationContext());
                final Activity a = getActivity();
                if (a == null) return;
                a.runOnUiThread(() -> {
                    fetching = false;
                    if (getFreeBtn != null) getFreeBtn.setEnabled(true);
                    if (fab != null) fab.setEnabled(true);
                    links.clear();
                    links.addAll(store.load());
                    if (adapter != null) adapter.notifyDataSetChanged();
                    updateEmptyVisibility();
                    Toast.makeText(a, getString(R.string.fetch_servers_done, added), Toast.LENGTH_LONG).show();
                });
            }
            catch (Exception e)
            {
                final Activity a = getActivity();
                if (a == null) return;
                a.runOnUiThread(() -> {
                    fetching = false;
                    if (getFreeBtn != null) getFreeBtn.setEnabled(true);
                    if (fab != null) fab.setEnabled(true);
                    String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                    Toast.makeText(a, getString(R.string.fetch_servers_failed, msg), Toast.LENGTH_LONG).show();
                });
            }
        });
    }



    @Override
    public void onStart()
    {
        super.onStart();

        ContextCompat.registerReceiver(
                requireContext(),
                receiver,
                new IntentFilter(SingBoxVpnService.ACTION_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
        profilesChangedReceiver,
        new IntentFilter("org.strongswan.android.V2RAY_PROFILES_CHANGED"));
        if (adapter != null && store != null)
        {
            links.clear();
            links.addAll(store.load());
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onStop()
    {
       try
 {
     LocalBroadcastManager.getInstance(requireContext())
             .unregisterReceiver(profilesChangedReceiver);
 }
 catch (IllegalArgumentException ignored)
 {
 }
 		try
         {
             requireContext().unregisterReceiver(receiver);
         }
         catch (IllegalArgumentException ignored)
         {
             // Receiver was not registered.
         }

         super.onStop();
    }

    @Override
    public void onDestroy()
    {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void editLink(final String selectedLink)
    {
        V2RayProfileEditor.show(
                requireContext(),
                selectedLink,
                false,
                (oldLink, newLink, copyMode) ->
                {
                    int index = links.indexOf(oldLink);
                    if (index < 0)
                    {
                        Toast.makeText(requireContext(), R.string.profile_not_found, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!oldLink.equals(newLink) && links.contains(newLink))
                    {
                        Toast.makeText(requireContext(), R.string.profile_exists, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (oldLink.equals(SingBoxVpnService.getLink()) && SingBoxVpnService.isRunning())
                    {
                        SingBoxVpnService.stop(requireContext());
                    }
                    links.set(index, newLink);
                    store.save(links);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(requireContext(), R.string.profile_updated, Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void confirmDelete(final String link)
    {
        new AlertDialog.Builder(requireActivity())
                .setMessage(
                        org.strongswan.android.utils.Str.get(
                                R.string.awg_delete_confirm, V2RayConfigBuilder.name(link))
                )
                .setPositiveButton(
                        R.string.action_delete,
                        (dialog, which) ->
                        {
                            if (link.equals(SingBoxVpnService.getLink()))
                            {
                                SingBoxVpnService.stop(requireContext());
                            }
                            links.remove(link);
                            store.save(links);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(requireContext(), R.string.profile_deleted, Toast.LENGTH_SHORT).show();
                        }
                )
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    public void deleteNoPingWithConfirm()
    {
        if (adapter == null || links.isEmpty())
        {
            Toast.makeText(requireContext(), R.string.delete_no_ping_none, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!adapter.isPingFinished())
        {
            Toast.makeText(requireContext(), R.string.delete_no_ping_run_first, Toast.LENGTH_SHORT).show();
            return;
        }

        final String runningLink = SingBoxVpnService.isRunning() ? SingBoxVpnService.getLink() : null;
        final List<String> targets = new ArrayList<>();
        for (String link : links)
        {
            if (!adapter.hasMsPing(link) && !link.equals(runningLink))
            {
                targets.add(link);
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
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                {
                    links.removeAll(targets);
                    store.save(links);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(requireContext(), R.string.profiles_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

 public void pingAllPublic()
 {
     pingAll();
 }
  private void pingAll()
 {
     if (links.isEmpty() || pinging)
     {
         return;
     }
     if (SingBoxVpnService.isRunning())
     {
         Toast.makeText(requireContext(), R.string.ping_disconnect_first, Toast.LENGTH_SHORT).show();
         return;
     }

     final List<String> copy = new ArrayList<>(links);
     final Context appContext = requireContext().getApplicationContext();
     final String running = appContext.getString(R.string.ping_running);
     final String timeout = appContext.getString(R.string.ping_timeout);

     adapter.clearPings();

     for (String link : copy)
     {
         adapter.setPing(link, running);
     }

     pinging = true;
     new Thread(() ->
     {
         try
         {
             V2RayConfigTester.pingAll(appContext, copy, 5000, 16, (link, delay) ->
             {
                 final String result = delay >= 0 ? delay + " ms" : timeout;
                 Activity a = getActivity();
                 if (a == null)
                 {
                     return;
                 }
                 a.runOnUiThread(() ->
                 {
                     if (adapter != null)
                     {
                         adapter.setPing(link, result);
                     }
                 });
             });
         }
         finally
         {
             pinging = false;
         }
     }).start();
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
