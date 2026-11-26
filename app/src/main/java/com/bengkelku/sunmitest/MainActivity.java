package com.bengkelku.sunmitest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  LinearLayout root = new LinearLayout(this);
  root.setOrientation(LinearLayout.VERTICAL);
  root.setPadding(24, 28, 24, 24);
  root.setGravity(Gravity.CENTER_HORIZONTAL);
  TextView title = new TextView(this); title.setText("BIKE POS TEST\nUji kompatibilitas Sunmi V2"); title.setTextSize(22); title.setGravity(Gravity.CENTER); root.addView(title);
  TextView info = new TextView(this); info.setTextSize(16);
  String abis = android.text.TextUtils.join(", ", Build.SUPPORTED_ABIS);
  info.setText("Model: " + Build.MODEL + "\nAndroid: " + Build.VERSION.RELEASE + "\nSDK: " + Build.VERSION.SDK_INT + "\nABI: " + abis + "\n\nAplikasi berhasil dibuka."); root.addView(info);
  Button test = new Button(this); test.setText("TES SIMPAN DATA"); root.addView(test);
  TextView status = new TextView(this); status.setTextSize(16); root.addView(status);
  test.setOnClickListener(v -> { SharedPreferences p = getSharedPreferences("test", MODE_PRIVATE); p.edit().putString("hasil", "BERHASIL").apply(); status.setText("Penyimpanan: " + p.getString("hasil", "GAGAL")); });
  setContentView(root);
 }
}
