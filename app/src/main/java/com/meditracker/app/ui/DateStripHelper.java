package com.meditracker.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.meditracker.app.R;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Builds the same simple seven-day strip for both dashboards. */
public final class DateStripHelper {
    private DateStripHelper() {}

    public static void bind(Context context, LinearLayout container) {
        container.removeAllViews();
        Calendar day = Calendar.getInstance();
        day.add(Calendar.DAY_OF_YEAR, -3);
        SimpleDateFormat weekday = new SimpleDateFormat("EEE", Locale.getDefault());
        SimpleDateFormat date = new SimpleDateFormat("d", Locale.getDefault());

        for (int index = 0; index < 7; index++) {
            boolean today = index == 3;
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(context, 8), dp(context, 9), dp(context, 8), dp(context, 9));
            cell.setBackgroundResource(today ? R.drawable.bg_date_selected : R.drawable.bg_date_default);

            LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(dp(context, 52), dp(context, 66));
            if (index > 0) cellParams.setMarginStart(dp(context, 6));
            container.addView(cell, cellParams);

            TextView dayName = new TextView(context);
            dayName.setText(weekday.format(day.getTime()).toUpperCase(Locale.getDefault()));
            dayName.setTextSize(10);
            dayName.setGravity(Gravity.CENTER);
            dayName.setTextColor(context.getColor(today ? R.color.white : R.color.muted));
            dayName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cell.addView(dayName, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 19)));

            TextView dayNumber = new TextView(context);
            dayNumber.setText(date.format(day.getTime()));
            dayNumber.setTextSize(19);
            dayNumber.setGravity(Gravity.CENTER);
            dayNumber.setTextColor(context.getColor(today ? R.color.white : R.color.navy));
            dayNumber.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cell.addView(dayNumber, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 28)));

            if (today) cell.setContentDescription(context.getString(R.string.today_accessibility, weekday.format(day.getTime()), date.format(day.getTime())));
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        container.post(() -> {
            View parent = (View) container.getParent();
            parent.scrollTo(Math.max(0, container.getWidth() / 2 - parent.getWidth() / 2), 0);
        });
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
