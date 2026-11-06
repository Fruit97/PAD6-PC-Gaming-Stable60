package com.example.gamethaitranslator;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;

public final class LogoActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean hasOpened = false;
    private final Runnable next = this::openApp;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10, 17, 33));
        getWindow().setNavigationBarColor(Color.rgb(10, 17, 33));

        FrameLayout frame = new FrameLayout(this);
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF071222,0xFF12264A,0xFF101528});
        frame.setBackground(gradient);

        LinearLayout content = new LinearLayout(this);
        content.setGravity(Gravity.CENTER);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_gamethai_logo);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imageP = new LinearLayout.LayoutParams(dp(132), dp(132));
        content.addView(icon,imageP);

        TextView headline = new TextView(this);
        headline.setText("GAME THAI\nTRANSLATOR");
        headline.setTextColor(Color.WHITE);
        headline.setTextSize(29f);
        headline.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        headline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams headingP = new LinearLayout.LayoutParams(-2,-2);
        headingP.topMargin=dp(16);
        content.addView(headline,headingP);

        TextView version = new TextView(this);
        version.setText("v1.7  •  VOICE → AUTO TRANSLATE");
        version.setTextSize(14f);
        version.setTextColor(0xFF71E7D0);
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionP = new LinearLayout.LayoutParams(-2,-2);
        versionP.topMargin=dp(13);
        content.addView(version,versionP);

        TextView subtitle = new TextView(this);
        subtitle.setText("พูดภาษาไทย → แปลอัตโนมัติเป็นอังกฤษหรือจีน\nไมค์ฟังต่อเนื่องจนกว่าจะกดปิดเอง");
        subtitle.setTextSize(15f);
        subtitle.setTextColor(0xFFB5C7E3);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subP = new LinearLayout.LayoutParams(-1,-2);
        subP.topMargin=dp(17);
        content.addView(subtitle,subP);

        frame.addView(content,new FrameLayout.LayoutParams(-1,-1));
        setContentView(frame);
        frame.setOnClickListener(v -> openApp());
        handler.postDelayed(next,950);
    }

    private void openApp() {
        if (hasOpened || isFinishing()) return;
        hasOpened = true;
        handler.removeCallbacks(next);
        Intent intent = new Intent(this,MainActivity.class);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(next);
        super.onDestroy();
    }
}
