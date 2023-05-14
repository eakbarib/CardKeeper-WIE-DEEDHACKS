package com.example.cardkeeper;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.tech.NfcA;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "CardKeeperChannel";
    private static final int notificationId = 1;

    ImageView contentView;
    NfcAdapter adapter;
    String[][] techListsArray;
    IntentFilter[] intentFiltersArray;
    PendingIntent pendingIntent;

    SharedPreferences sharedPref;

    boolean timerIsOn = false;

    int timeSoFar = 0;

    int timer = 5;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPref = getPreferences(Context.MODE_PRIVATE);
        readTimerFromSharedPref();
        TextView textView = findViewById(R.id.compass_card_status);
        textView.setText(getString(R.string.compass_card_msg) + " " + getString(R.string.present));
        NfcManager manager = (NfcManager) getApplicationContext().getSystemService(Context.NFC_SERVICE);
        adapter = manager.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            // adapter exists and is enabled.
            textView.setText(getString(R.string.nfc_enabled));
        } else {
            textView.setText(getString(R.string.nfc_disabled));
        }

        pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE);
        techListsArray = new String[][]{new String[]{NfcA.class.getName()}};
        IntentFilter tech_discovered = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        intentFiltersArray = new IntentFilter[]{tech_discovered};
        contentView = findViewById(R.id.compass_card_image);
        createNotificationChannel();
        NumberPicker timerPicker = findViewById(R.id.compass_card_check_timer);
        timerPicker.setMinValue(1);
        timerPicker.setMaxValue(60);
        timerPicker.setValue(timer);
        timerPicker.setOnValueChangedListener((numberPicker, oldVal, newVal) -> timer = newVal);
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            // Register the channel with the system. You can't change the importance
            // or other notification behaviors after this.
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray);
        readTimerFromSharedPref();
        if(!timerIsOn) {
            Timer t = new Timer();
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    timeSoFar++;
                    if (timeSoFar >= timer * 60) {
                        runOnUiThread(() -> {
                            NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.this, CHANNEL_ID)
                                    .setSmallIcon(R.drawable.losticon)
                                    .setContentTitle(getString(R.string.card_not_present_title))
                                    .setContentText(getString(R.string.card_not_present))
                                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
                            contentView.setImageDrawable(getDrawable(R.drawable.nocompasscard));
                            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MainActivity.this);
                            timeSoFar = 0;
                            if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                Toast.makeText(MainActivity.this, getString(R.string.card_not_present), Toast.LENGTH_SHORT).show();
                                return;
                            }
                            notificationManager.notify(notificationId, builder.build());
                        });
                    }
                }
            };
            t.scheduleAtFixedRate(task, 0, 1000);
            timerIsOn = true;
        }
    }

    private void readTimerFromSharedPref(){
        timer = sharedPref.getInt(getString(R.string.timer_key), 0);
    }

    private void writeTimerToSharedPref(){
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(getString(R.string.timer_key), timer);
        editor.apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        adapter.disableForegroundDispatch(this);
        writeTimerToSharedPref();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())){
            Toast.makeText(this, getString(R.string.compass_card_msg)+" "+getString(R.string.present), Toast.LENGTH_SHORT).show();
            NotificationManagerCompat.from(getApplicationContext()).cancel(notificationId);
            showCompassImage();
            timeSoFar = 0;
            writeTimerToSharedPref();
        }
    }

    private void showCompassImage() {
        // Set the content view to 0% opacity but visible, so that it is visible
        // (but fully transparent) during the animation.
        contentView.setAlpha(0f);
        contentView.setVisibility(View.VISIBLE);
        contentView.setImageDrawable(getDrawable(R.drawable.compasscard));

        // Animate the content view to 100% opacity, and clear any animation
        // listener set on the view.
        contentView.animate()
                .alpha(1f)
                .setDuration(3000)
                .setListener(null);
    }

}
