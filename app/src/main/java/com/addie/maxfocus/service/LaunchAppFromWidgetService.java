package com.addie.maxfocus.service;

import android.app.IntentService;
import android.content.Intent;

import com.addie.maxfocus.ui.DialogActivity;

import timber.log.Timber;


/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions and extra parameters.
 */
public class LaunchAppFromWidgetService extends IntentService {

    private static final String TARGET_PACKAGE_KEY = "target_package";
    private static final String IS_WIDGET_LAUNCH = "is_widget_launch";


    public LaunchAppFromWidgetService() {
        super("LaunchAppFromWidgetService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            Timber.d("Service launched");
            final String packageName = intent.getStringExtra(TARGET_PACKAGE_KEY);

            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            startActivity(launchIntent);

            try {
                Thread.sleep(500);

                // calling some methods here
                final Intent dialogIntent = new Intent(LaunchAppFromWidgetService.this, DialogActivity.class);
                dialogIntent.putExtra(IS_WIDGET_LAUNCH, true);
                dialogIntent.putExtra(TARGET_PACKAGE_KEY, packageName);
                startActivity(dialogIntent);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


        }

    }
}
