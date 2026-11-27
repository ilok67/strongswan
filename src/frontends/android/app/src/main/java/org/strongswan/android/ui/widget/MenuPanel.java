package org.strongswan.android.ui.widget;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.strongswan.android.R;

import java.util.List;

public final class MenuPanel
{
	private MenuPanel()
	{
	}

	private static int iconFor(int id)
	{
		if (id == R.id.fetch_servers)
		{
			return R.drawable.ic_cloud_download;
		}
		if (id == R.id.app_filter)
		{
			return R.drawable.ic_apps;
		}
		if (id == R.id.delete_no_ping)
		{
			return R.drawable.ic_delete_sweep;
		}
		if (id == R.id.backup_profiles)
		{
			return R.drawable.ic_backup;
		}
		if (id == R.id.check_update)
		{
			return R.drawable.ic_refresh;
		}
		if (id == R.id.help)
		{
			return R.drawable.ic_help;
		}
		if (id == R.id.help)
		{
			return R.drawable.ic_help;
		}
		if (id == R.id.about)
		{
			return R.drawable.ic_info;
		}
		return R.drawable.ic_dot;
	}

	public static void show(final Context context, final Menu menu, List<MenuItem> items, int topOffset)
	{
		final Dialog dialog = new Dialog(context);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

		LinearLayout card = new LinearLayout(context);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(Color.parseColor("#1E293B"));
		bg.setCornerRadius(UiStyle.dp(context, 20));
		bg.setStroke(UiStyle.dp(context, 1), Color.parseColor("#334155"));
		card.setBackground(bg);
		int pad = UiStyle.dp(context, 8);
		card.setPadding(pad, pad, pad, pad);

		LinearLayout header = new LinearLayout(context);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(UiStyle.dp(context, 10), UiStyle.dp(context, 10), UiStyle.dp(context, 10), UiStyle.dp(context, 12));
		ImageView logo = new ImageView(context);
		logo.setImageResource(R.mipmap.ic_launcher);
		header.addView(logo, new LinearLayout.LayoutParams(UiStyle.dp(context, 40), UiStyle.dp(context, 40)));
		TextView appName = new TextView(context);
		appName.setText(R.string.app_name);
		appName.setTextColor(Color.WHITE);
		appName.setTextSize(18);
		appName.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
		LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		nameLp.setMarginStart(UiStyle.dp(context, 12));
		header.addView(appName, nameLp);
		card.addView(header);

		View divider = new View(context);
		divider.setBackgroundColor(Color.parseColor("#334155"));
		LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiStyle.dp(context, 1));
		dividerLp.setMargins(UiStyle.dp(context, 6), 0, UiStyle.dp(context, 6), UiStyle.dp(context, 6));
		card.addView(divider, dividerLp);

		TypedValue tv = new TypedValue();
		context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);

		LinearLayout rows = new LinearLayout(context);
		rows.setOrientation(LinearLayout.VERTICAL);
		for (final MenuItem item : items)
		{
			LinearLayout row = new LinearLayout(context);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setMinimumHeight(UiStyle.dp(context, 52));
			row.setPadding(UiStyle.dp(context, 12), UiStyle.dp(context, 6), UiStyle.dp(context, 16), UiStyle.dp(context, 6));
			row.setBackgroundResource(tv.resourceId);
			row.setClickable(true);

			ImageView icon = new ImageView(context);
			icon.setImageResource(iconFor(item.getItemId()));
			boolean danger = item.getItemId() == R.id.delete_no_ping;
			icon.setImageTintList(ColorStateList.valueOf(Color.parseColor(danger ? "#F87171" : "#4ADE80")));
			row.addView(icon, new LinearLayout.LayoutParams(UiStyle.dp(context, 24), UiStyle.dp(context, 24)));

			TextView title = new TextView(context);
			title.setText(item.getTitle());
			title.setTextColor(Color.parseColor("#F1F5F9"));
			title.setTextSize(15);
			LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			titleLp.setMarginStart(UiStyle.dp(context, 16));
			row.addView(title, titleLp);

			row.setOnClickListener(v -> {
				dialog.dismiss();
				menu.performIdentifierAction(item.getItemId(), 0);
			});
			rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		ScrollView scroll = new ScrollView(context);
		scroll.addView(rows);
		card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		dialog.setContentView(card);
		Window w = dialog.getWindow();
		if (w != null)
		{
			w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			boolean rtl = context.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
			WindowManager.LayoutParams lp = w.getAttributes();
			lp.width = UiStyle.dp(context, 290);
			lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
			lp.gravity = (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP;
			lp.x = UiStyle.dp(context, 8);
			lp.y = topOffset + UiStyle.dp(context, 4);
			lp.dimAmount = 0.45f;
			w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
			w.setAttributes(lp);
			w.setWindowAnimations(android.R.style.Animation_Dialog);
		}
		dialog.show();
	}
}
