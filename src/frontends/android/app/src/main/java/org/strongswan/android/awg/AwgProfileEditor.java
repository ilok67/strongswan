package org.strongswan.android.awg;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;
import org.strongswan.android.R;
import org.strongswan.android.ui.widget.UiStyle;
import org.strongswan.android.utils.Str;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AwgProfileEditor
{
	public interface Callback
	{
		void onSaved(JSONObject updated);
	}

	private static final String[][] BASIC = {
		{"name", "awg_f_name"},
		{"host", "awg_f_server"},
		{"port", "awg_f_port"},
		{"client_ip", "awg_f_address"},
		{"dns1", "awg_f_dns1"},
		{"dns2", "awg_f_dns2"},
		{"mtu", "awg_f_mtu"},
		{"client_priv_key", "awg_f_private_key"},
		{"server_pub_key", "awg_f_public_key"},
		{"psk_key", "awg_f_psk"},
		{"allowed_ips", "awg_f_allowed"},
		{"persistent_keep_alive", "awg_f_keepalive"}
	};

	private static final String[] ADVANCED = {
		"Jc", "Jmin", "Jmax", "S1", "S2", "S3", "S4", "H1", "H2", "H3", "H4", "I1", "I2", "I3", "I4", "I5"
	};

	private AwgProfileEditor()
	{
	}

	private static int label(Context context, String name)
	{
		return context.getResources().getIdentifier(name, "string", context.getPackageName());
	}

	public static void show(final Context context, final JSONObject original, final Callback callback)
	{
		final LinearLayout layout = new LinearLayout(context);
		layout.setOrientation(LinearLayout.VERTICAL);
		int pad = UiStyle.dp(context, 16);
		layout.setPadding(pad, pad / 2, pad, pad);

		final Map<String, EditText> fields = new LinkedHashMap<String, EditText>();
		for (String[] row : BASIC)
		{
			String value = original.optString(row[0], "");
			if (row[0].equals("port"))
			{
				value = original.optInt("port", 0) > 0 ? String.valueOf(original.optInt("port")) : "";
			}
			addField(context, layout, fields, row[0], context.getString(label(context, row[1])), value);
		}

		TextView advanced = new TextView(context);
		advanced.setText(R.string.awg_f_advanced);
		advanced.setTextSize(14);
		advanced.setTypeface(null, android.graphics.Typeface.BOLD);
		advanced.setPadding(0, UiStyle.dp(context, 18), 0, UiStyle.dp(context, 2));
		layout.addView(advanced);

		for (String key : ADVANCED)
		{
			addField(context, layout, fields, key, key, original.optString(key, ""));
		}

		ScrollView scroll = new ScrollView(context);
		scroll.addView(layout);

		final AlertDialog dialog = new AlertDialog.Builder(context)
			.setTitle(R.string.awg_edit_title)
			.setView(scroll)
			.setPositiveButton(R.string.action_save, null)
			.setNegativeButton(android.R.string.cancel, null)
			.create();
		dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
			try
			{
				JSONObject updated = build(original, fields);
				if (callback != null)
				{
					callback.onSaved(updated);
				}
				dialog.dismiss();
			}
			catch (Exception e)
			{
				Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
			}
		}));
		dialog.show();
	}

	private static void addField(Context context, LinearLayout layout, Map<String, EditText> fields, String key, String title, String value)
	{
		TextView label = new TextView(context);
		label.setText(title);
		label.setTextSize(13);
		label.setAlpha(0.75f);
		label.setPadding(0, UiStyle.dp(context, 10), 0, UiStyle.dp(context, 2));
		layout.addView(label);

		EditText edit = new EditText(context);
		edit.setSingleLine(true);
		edit.setText(value == null ? "" : value);
		layout.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		fields.put(key, edit);
	}

	private static JSONObject build(JSONObject original, Map<String, EditText> fields) throws Exception
	{
		JSONObject p = new JSONObject(original.toString());
		for (Map.Entry<String, EditText> e : fields.entrySet())
		{
			String key = e.getKey();
			String value = e.getValue().getText().toString().trim();
			if (key.equals("port"))
			{
				continue;
			}
			if (value.isEmpty())
			{
				p.remove(key);
			}
			else
			{
				p.put(key, value);
			}
		}
		String name = p.optString("name", "");
		String host = p.optString("host", "");
		if (name.isEmpty())
		{
			throw new Exception(Str.get(R.string.ve_err_name));
		}
		if (host.isEmpty())
		{
			throw new Exception(Str.get(R.string.ve_err_server));
		}
		int port;
		try
		{
			port = Integer.parseInt(fields.get("port").getText().toString().trim());
		}
		catch (Exception e)
		{
			throw new Exception(Str.get(R.string.ve_err_port_number));
		}
		if (port < 1 || port > 65535)
		{
			throw new Exception(Str.get(R.string.ve_err_port_range));
		}
		p.put("port", port);
		if (p.optString("client_priv_key", "").isEmpty() || p.optString("server_pub_key", "").isEmpty()
			|| p.optString("client_ip", "").isEmpty())
		{
			throw new Exception(Str.get(R.string.awg_err_keys));
		}
		return p;
	}
}
