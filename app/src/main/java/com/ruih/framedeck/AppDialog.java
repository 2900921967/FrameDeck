package com.ruih.framedeck;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

/** One visual language for menus, playlists, messages and input dialogs. */
final class AppDialog {
    private static final int SURFACE = 0xff151f19, ACCENT = 0xff8be4bc;

    static final class Builder {
        private final Context context;
        private CharSequence title = "", message;
        private String icon = "folder";
        private String[] items, descriptions, icons;
        private int checked = -1;
        private boolean singleChoice;
        private View custom;
        private DialogInterface.OnClickListener itemClick;
        private CharSequence positive, neutral, negative;
        private DialogInterface.OnClickListener positiveClick, neutralClick, negativeClick;
        Builder(Context context) { this.context = context; }
        Builder setTitle(CharSequence title) { this.title = title; return this; }
        Builder setMessage(CharSequence message) { this.message = message; return this; }
        Builder setIconKind(String icon) { this.icon = icon; return this; }
        Builder setItemDetails(String[] descriptions, String[] icons) {
            this.descriptions = descriptions; this.icons = icons; return this;
        }
        Builder setItems(String[] items, DialogInterface.OnClickListener listener) {
            this.items = items; itemClick = listener; singleChoice = false; return this;
        }
        Builder setSingleChoiceItems(String[] items, int checked, DialogInterface.OnClickListener listener) {
            this.items = items; this.checked = checked; itemClick = listener; singleChoice = true; return this;
        }
        Builder setView(View view) { custom = view; return this; }
        Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
            positive = text; positiveClick = listener; return this;
        }
        Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) {
            neutral = text; neutralClick = listener; return this;
        }
        Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
            negative = text; negativeClick = listener; return this;
        }
        AlertDialog create() {
            StyledDialog dialog = new StyledDialog(context);
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(context, 20), dp(context, 24), dp(context, 20), dp(context, 8));
            LinearLayout heading = new LinearLayout(context);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            PlayerIcon hero = new PlayerIcon(context, icon);
            hero.setBackground(shape(context, 0xff22372a, 14));
            hero.setPadding(dp(context, 7), dp(context, 7), dp(context, 7), dp(context, 7));
            heading.addView(hero, new LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)));
            TextView titleView = text(context, title, 20, 0xfff1f5f2);
            titleView.setTypeface(null, Typeface.BOLD);
            titleView.setMaxLines(2);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
            titleParams.leftMargin = dp(context, 14);
            heading.addView(titleView, titleParams);
            content.addView(heading);
            View divider = new View(context);
            divider.setBackgroundColor(0xff2b3b31);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(context, 1));
            dividerParams.topMargin = dp(context, 18); dividerParams.bottomMargin = dp(context, 12);
            content.addView(divider, dividerParams);
            int available = Math.max(dp(context, 80), Math.min(dp(context, 420),
                    (int) (context.getResources().getDisplayMetrics().heightPixels * 0.8f) - dp(context, 170)));
            TextView messageView = text(context, message == null ? "" : message, 14, 0xffb2c5b8);
            messageView.setLineSpacing(dp(context, 5), 1);
            messageView.setPadding(dp(context, 2), dp(context, 4), dp(context, 2), dp(context, 10));
            BoundedScroll messageScroll = new BoundedScroll(context, available);
            messageScroll.setFillViewport(false);
            messageScroll.addView(messageView, new ViewGroup.LayoutParams(-1, -2));
            messageScroll.setVisibility(message == null ? View.GONE : View.VISIBLE);
            content.addView(messageScroll, new LinearLayout.LayoutParams(-1, -2));
            dialog.messageView = messageView;
            dialog.messageContainer = messageScroll;
            if (items != null) {
                ListView list = new ListView(context);
                list.setDivider(null); list.setDividerHeight(dp(context, 6));
                list.setSelector(android.R.color.transparent);
                list.setVerticalScrollBarEnabled(true);
                MenuAdapter adapter = new MenuAdapter(context, items, descriptions, icons, icon, singleChoice, checked);
                list.setAdapter(adapter);
                list.setOnItemClickListener((parent, view, position, id) -> {
                    adapter.checked = position;
                    adapter.notifyDataSetChanged();
                    if (itemClick != null) itemClick.onClick(dialog, position);
                    if (!singleChoice) dialog.dismiss();
                });
                int listHeight = Math.min(items.length * dp(context, 76), available);
                content.addView(list, new LinearLayout.LayoutParams(-1, listHeight));
            }
            if (custom != null) content.addView(custom, new LinearLayout.LayoutParams(-1, -2));
            dialog.setView(content, 0, 0, 0, 0);
            if (positive != null) dialog.setButton(DialogInterface.BUTTON_POSITIVE, positive, positiveClick);
            if (neutral != null) dialog.setButton(DialogInterface.BUTTON_NEUTRAL, neutral, neutralClick);
            if (negative != null) dialog.setButton(DialogInterface.BUTTON_NEGATIVE, negative, negativeClick);
            return dialog;
        }
        AlertDialog show() { AlertDialog dialog = create(); dialog.show(); return dialog; }
    }

    private static final class StyledDialog extends AlertDialog {
        TextView messageView;
        View messageContainer;
        StyledDialog(Context context) { super(context, R.style.FrameDeckDialog); }
        @Override public void setMessage(CharSequence message) {
            if (messageView == null) { super.setMessage(message); return; }
            messageView.setText(message);
            messageContainer.setVisibility(message == null || message.length() == 0 ? View.GONE : View.VISIBLE);
        }
        @Override protected void onStart() {
            super.onStart();
            Context context = getContext();
            Window window = getWindow();
            if (window != null) {
                GradientDrawable background = shape(context, SURFACE, 26);
                background.setStroke(dp(context, 1), 0xff304336);
                window.setBackgroundDrawable(background);
                window.setLayout(Math.min(dp(context, 440), context.getResources().getDisplayMetrics().widthPixels - dp(context, 32)),
                        WindowManager.LayoutParams.WRAP_CONTENT);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                WindowManager.LayoutParams params = window.getAttributes();
                params.dimAmount = 0.68f; window.setAttributes(params);
                window.getDecorView().setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                window.getDecorView().setClipToOutline(true);
                window.getDecorView().setElevation(dp(context, 24));
            }
            for (int which : new int[]{BUTTON_NEGATIVE, BUTTON_NEUTRAL, BUTTON_POSITIVE}) {
                Button button = getButton(which);
                if (button == null) continue;
                boolean primary = which == BUTTON_POSITIVE;
                button.setTextColor(primary ? 0xff102219 : 0xffc0d5c7);
                button.setTextSize(14);
                button.setTypeface(null, Typeface.BOLD);
                button.setAllCaps(false);
                button.setPadding(dp(context, 15), 0, dp(context, 15), 0);
                button.setMinHeight(dp(context, 44)); button.setMinimumHeight(dp(context, 44));
                button.setMinWidth(0); button.setMinimumWidth(0);
                button.setBackground(shape(context, primary ? ACCENT : 0xff233229, 12));
                button.setStateListAnimator(null);
                ViewGroup.LayoutParams raw = button.getLayoutParams();
                raw.height = dp(context, 44);
                if (raw instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) raw;
                    margins.leftMargin = dp(context, 5); margins.rightMargin = dp(context, 5);
                }
                button.setLayoutParams(raw);
                if (button.getParent() instanceof View) {
                    ((View) button.getParent()).setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 16));
                }
            }
        }
    }

    private static final class MenuAdapter extends BaseAdapter {
        private final Context context;
        private final String[] items, descriptions, icons;
        private final String defaultIcon;
        private final boolean singleChoice;
        int checked;
        MenuAdapter(Context context, String[] items, String[] descriptions, String[] icons,
                String defaultIcon, boolean singleChoice, int checked) {
            this.context = context; this.items = items; this.descriptions = descriptions;
            this.icons = icons; this.defaultIcon = defaultIcon; this.singleChoice = singleChoice; this.checked = checked;
        }
        @Override public int getCount() { return items.length; }
        @Override public Object getItem(int position) { return items[position]; }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            boolean selected = singleChoice && position == checked;
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(context, 70));
            row.setPadding(dp(context, 12), dp(context, 10), dp(context, 10), dp(context, 10));
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2878e1be),
                    shape(context, selected ? 0xff233e2e : 0xff1c2a21, 14), shape(context, Color.WHITE, 14)));
            String icon = icons != null && position < icons.length ? icons[position] : singleChoice ? "video" : defaultIcon;
            PlayerIcon leading = new PlayerIcon(context, icon);
            leading.setBackground(shape(context, selected ? 0xff2b513b : 0xff273b2e, 11));
            row.addView(leading, new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)));
            String[] lines = items[position].split("\n", 2);
            String subtitle = descriptions != null && position < descriptions.length ? descriptions[position]
                    : lines.length > 1 ? lines[1] : "";
            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView primary = text(context, lines[0], 15, selected ? 0xffa7edc6 : 0xffe5eee8);
            primary.setMaxLines(2); primary.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(primary);
            if (!subtitle.isEmpty()) {
                TextView secondary = text(context, subtitle, 12, 0xff93ad9c);
                secondary.setMaxLines(2); secondary.setEllipsize(TextUtils.TruncateAt.END);
                secondary.setPadding(0, dp(context, 4), 0, 0);
                labels.addView(secondary);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.leftMargin = dp(context, 12); lp.rightMargin = dp(context, 8);
            row.addView(labels, lp);
            PlayerIcon trailing = new PlayerIcon(context, singleChoice ? "radio" : "chevron");
            trailing.setChecked(selected);
            row.addView(trailing, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));
            row.setSelected(selected);
            row.setContentDescription(lines[0] + "，" + subtitle + (selected ? "，已选中" : ""));
            return row;
        }
    }

    private static final class BoundedScroll extends ScrollView {
        private final int maxHeight;
        BoundedScroll(Context context, int maxHeight) { super(context); this.maxHeight = maxHeight; }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int limit = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED ? maxHeight
                    : Math.min(maxHeight, MeasureSpec.getSize(heightSpec));
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
        }
    }
    private static TextView text(Context context, CharSequence text, int size, int color) {
        TextView view = new TextView(context);
        view.setText(text); view.setTextSize(size); view.setTextColor(color);
        return view;
    }
    private static GradientDrawable shape(Context context, int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color); background.setCornerRadius(dp(context, radius));
        return background;
    }
    private static int dp(Context context, int n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }
}
