package org.strongswan.android.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import org.strongswan.android.R;

public final class UiStyle
{
	private UiStyle()
	{
	}

	public static int dp(Context context, int value)
	{
		return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
	}

	public static void fetchButton(Button button)
	{
		Context context = button.getContext();
		button.setBackgroundResource(R.drawable.btn_fetch);
		button.setTextColor(Color.parseColor("#04210F"));
		button.setAllCaps(false);
		button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
		button.setTextSize(15);
		button.setMinHeight(dp(context, 48));
		button.setMinimumHeight(dp(context, 48));
		button.setPadding(dp(context, 24), dp(context, 10), dp(context, 28), dp(context, 10));
		Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_add);
		if (icon != null)
		{
			icon = icon.mutate();
			icon.setTint(Color.parseColor("#04210F"));
			button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
			button.setCompoundDrawablePadding(dp(context, 8));
		}
		if (android.os.Build.VERSION.SDK_INT >= 21)
		{
			button.setStateListAnimator(null);
			button.setElevation(dp(context, 2));
		}
	}

	public static void tint(ImageButton button, int color)
	{
		ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(color));
	}

	public interface CardActions
	{
		void onEdit();

		void onShare();

		void onDelete();
	}

	public static void bindActions(View card, boolean canEdit, boolean canDelete, final CardActions actions)
	{
		ImageButton share = card.findViewById(R.id.profile_item_share);
		ImageButton edit = card.findViewById(R.id.profile_item_edit);
		ImageButton delete = card.findViewById(R.id.profile_item_delete);
		if (share == null || edit == null || delete == null)
		{
			return;
		}
		tint(share, Color.parseColor("#94A3B8"));
		tint(edit, Color.parseColor("#94A3B8"));
		tint(delete, Color.parseColor("#F87171"));
		edit.setVisibility(canEdit ? View.VISIBLE : View.GONE);
		delete.setVisibility(canDelete ? View.VISIBLE : View.GONE);
		share.setOnClickListener(v -> actions.onShare());
		edit.setOnClickListener(v -> actions.onEdit());
		delete.setOnClickListener(v -> actions.onDelete());
	}
}
