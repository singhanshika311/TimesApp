/*
 * MIT License
 *
 * Copyright (c) 2018 aSoft
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.addie.timesapp.ui;

import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.addie.timesapp.R;
import com.addie.timesapp.adapter.AppAdapter;
import com.addie.timesapp.data.AppColumns;
import com.addie.timesapp.extra.RecyclerViewDisabler;
import com.addie.timesapp.model.App;
import com.addie.timesapp.utils.Utils;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import timber.log.Timber;

import static com.addie.timesapp.data.AppProvider.Apps.URI_APPS;

/**
 * Displays a list of apps from which an app is selected for launching with a timer or app shortcuts are created on home screen
 */
//TODO [FUTURE]Layout to display past usage of apps with/without usage of timers
//TODO: Correct everything for rotation
public class MainActivity extends AppCompatActivity implements AppAdapter.AppOnClickHandler {

    private static final int APPS_LOADER_MANAGER_ID = 131;
    private static final int APPS_LOADER_DB_ID = 486;
    private static final String APPS_LIST_KEY = "apps_list";
    private static final String APP_COLOR_KEY = "app_color";
    private static final String TEXT_COLOR_KEY = "text_color";


    @BindView(R.id.rv_apps)
    RecyclerView mAppsRecyclerView;
    @BindView(R.id.pb_apps_loading_indicator)
    ProgressBar mLoadingIndicator;


    private static final String TARGET_PACKAGE_KEY = "target_package";

    private AppAdapter mAdapter;
    public static ArrayList<App> mAppsList;
    private App mSelectedApp;
    private static PackageManager mPackageManager;
    private RecyclerView.OnItemTouchListener mRecyclerViewDisabler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        if (mAppsList != null) {
            if (mAppsList.isEmpty()) {

                // Start Loading Apps
                loadAppsFromManagerOrDb();
            } else {
                // Data has already been loaded by SplashActivity and assigned to public static variable
                initViews();
            }
        } else {
            loadAppsFromManagerOrDb();
        }
    }

    /**
     * Initialises the views of this activity as data is ready
     */
    private void initViews() {
        showRecyclerView(true);

        mAdapter = new AppAdapter(MainActivity.this, MainActivity.this);
        mAppsRecyclerView.setAdapter(mAdapter);

        mAdapter.setListData(mAppsList);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        if (!preferences.contains(getString(R.string.pref_display_tap_target_apps)) || preferences.getBoolean(getString(R.string.pref_display_tap_target_apps), true)) {

            mRecyclerViewDisabler = new RecyclerViewDisabler();

            mAppsRecyclerView.addOnItemTouchListener(mRecyclerViewDisabler);
            displayTapTargetView();
        }


        mAppsRecyclerView.setLayoutManager(new GridLayoutManager(MainActivity.this, 4, LinearLayoutManager.VERTICAL, false));
        mAppsRecyclerView.setHasFixedSize(true);

    }

    /**
     * Checks if app data is present in database. If yes, then loads it otherwise fetches data from PackageManager and saves in database
     */
    private void loadAppsFromManagerOrDb() {
        Cursor cursor = getContentResolver().query(URI_APPS, null, null, null, null);
        if (cursor != null && cursor.getCount() != 0) {
            getSupportLoaderManager().initLoader(APPS_LOADER_DB_ID, null, fetchAppsListener);
        } else {
            getSupportLoaderManager().initLoader(APPS_LOADER_MANAGER_ID, null, fetchAppsListener);
        }
        if (cursor != null) {
            cursor.close();
        }

    }

    /**
     * Called by AppAdapter when an app is selected in the RecyclerView
     *
     * @param selectedApp the app that is selected
     */
    @Override
    public void onClick(App selectedApp) {
        mSelectedApp = selectedApp;
        try {
            createShortcut();
            Toast.makeText(this, R.string.shortcut_created, Toast.LENGTH_SHORT).show();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

    }

    /**
     * Called by AppAdapter when an app is long clicked in the RecyclerView
     *
     * @param selectedApp the app that is selected
     */
    @Override
    public void onLongClick(App selectedApp) {
        mSelectedApp = selectedApp;

        showTimerDialog();
    }

    /**
     * Shows timer dialog to select the duration for which the selected app is to be run
     */
    public void showTimerDialog() {

        TimeDialog tdialog = new TimeDialog(this, mSelectedApp.getmPackage()
                , mSelectedApp.getmAppColor(), mSelectedApp.getmTextColor());
        tdialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        tdialog.show();

        tdialog.getWindow().getDecorView().setBackgroundColor(mSelectedApp.getmAppColor());
        tdialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {
                Window window = ((AlertDialog) dialog).getWindow();
                window.getDecorView().setBackgroundColor(mSelectedApp.getmAppColor());
            }
        });


    }

    /**
     * Displays either the recyclerView or the progressbar depending upon showRV
     *
     * @param showRV decides which view to be displayed
     */
    private void showRecyclerView(boolean showRV) {
        if (showRV) {

            mAppsRecyclerView.setVisibility(View.VISIBLE);
            mLoadingIndicator.setVisibility(View.GONE);
        } else {
            mAppsRecyclerView.setVisibility(View.GONE);
            mLoadingIndicator.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_apps_action_refresh_list:
                refreshAppsList();
                break;
            case R.id.menu_apps_action_settings:
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * Clears database and reloads data using packagemanager
     */
    private void refreshAppsList() {
        if (mAdapter != null) {

            mAdapter.setListData(null);
        }
        getContentResolver().delete(URI_APPS, null, null);
        loadAppsFromManagerOrDb();

    }

    private LoaderManager.LoaderCallbacks<ArrayList> fetchAppsListener
            = new LoaderManager.LoaderCallbacks<ArrayList>() {
        @NonNull
        @Override
        public Loader<ArrayList> onCreateLoader(int id, @Nullable Bundle args) {
            Timber.d("onCreateLoader");
            showRecyclerView(false);
            return new AppLoader(MainActivity.this, id);
        }

        /**
         * Initialises the RecyclerView displaying the list of apps
         */
        @Override
        public void onLoadFinished(@NonNull Loader<ArrayList> loader, ArrayList data) {
            showRecyclerView(true);
            Timber.d("onLoadFinished");
            mAdapter = new AppAdapter(MainActivity.this, MainActivity.this);
            mAppsRecyclerView.setAdapter(mAdapter);

            mAdapter.setListData(data);

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            if (!preferences.contains(getString(R.string.pref_display_tap_target_apps)) || preferences.getBoolean(getString(R.string.pref_display_tap_target_apps), true)) {

                mRecyclerViewDisabler = new RecyclerViewDisabler();

                mAppsRecyclerView.addOnItemTouchListener(mRecyclerViewDisabler);
                displayTapTargetView();
            }


            mAppsRecyclerView.setLayoutManager(new GridLayoutManager(MainActivity.this, 4, LinearLayoutManager.VERTICAL, false));
            mAppsRecyclerView.setHasFixedSize(true);

            getSupportLoaderManager().destroyLoader(APPS_LOADER_MANAGER_ID);

        }

        @Override
        public void onLoaderReset(@NonNull Loader<ArrayList> loader) {

        }
    };


    /**
     * Displays the tapTargetView tutorial on using the app icons
     */
    private void displayTapTargetView() {


        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {

                int childNum = 1;
                if (mAppsRecyclerView.getChildCount() >= 8) {
                    childNum = 8;
                }

                new TapTargetSequence(MainActivity.this)
                        .targets(
                                TapTarget.forView(mAppsRecyclerView.getChildAt(childNum).findViewById(R.id.iv_app_list_item_icon), getString(R.string.tap_target_apps_title), getString(R.string.tap_target_apps_message))
                                        .cancelable(false).transparentTarget(true).targetRadius(60),

                                TapTarget.forView(findViewById(R.id.menu_apps_action_refresh_list), "Refresh", "Reload the list of apps").cancelable(false),
                                TapTarget.forView(findViewById(R.id.menu_apps_action_settings), "Settings", "Manage preferences")
                                        .cancelable(false)
                        ).listener(new TapTargetSequence.Listener() {
                    // This listener will tell us when interesting(tm) events happen in regards
                    // to the sequence
                    @Override
                    public void onSequenceFinish() {
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        preferences.edit().putBoolean(getString(R.string.pref_display_tap_target_apps), false).apply();

                    }

                    @Override
                    public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {

                    }

                    @Override
                    public void onSequenceCanceled(TapTarget lastTarget) {
                        // Boo
                    }
                }).start();



                mAppsRecyclerView.removeOnItemTouchListener(mRecyclerViewDisabler);

            }
        }, 500);


    }


    public static class AppLoader extends AsyncTaskLoader<ArrayList> {

        int mId;

        public AppLoader(@NonNull Context context, int id) {
            super(context);
            mPackageManager = context.getPackageManager();
            mId = id;
        }

        /**
         * Loads a list of installed apps on the device using PackageManager
         */
        @Nullable
        @Override
        public ArrayList loadInBackground() {
            Timber.d("loadInBackground");
            ArrayList mAppsList = new ArrayList<>();
            if (mId == APPS_LOADER_MANAGER_ID) {

                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> activities = mPackageManager.queryIntentActivities(intent, 0);
                Collections.sort(activities, new ResolveInfo.DisplayNameComparator(mPackageManager));
                for (ResolveInfo ri : activities) {
                    ContentValues values = new ContentValues();
                    App app = new App();
                    app.setmPackage(ri.activityInfo.packageName);
                    app.setmTitle((String) ri.loadLabel(mPackageManager));
                    app.setmIcon(ri.activityInfo.loadIcon(mPackageManager));

                    Palette p = Palette.from(Utils.getBitmapFromDrawable(app.getmIcon())).generate();
                    app.setmAppColor(p.getVibrantColor(getContext().getResources().getColor(R.color.black)));
                    app.setmTextColor(Utils.getTextColor(app.getmAppColor()));
                    Timber.d("APP:" + app.getmAppColor() + " TEXT " + app.getmTextColor());

                    mAppsList.add(app);
                    values.put(AppColumns.APP_TITLE, app.getmTitle());
                    values.put(AppColumns.PACKAGE_NAME, app.getmPackage());
                    values.put(AppColumns.PALETTE_COLOR, app.getmAppColor());
                    values.put(AppColumns.TEXT_COLOR, app.getmTextColor());
                    try{

                    getContext().getContentResolver().insert(URI_APPS, values);
                    }catch (Exception e){
                        Timber.d("COULD NOT INSERT APP in Database");
                    }
                }


            } else if (mId == APPS_LOADER_DB_ID) {

                Cursor cursor = getContext().getContentResolver().query(URI_APPS, null, null, null, null);
                if (cursor != null) {

                    cursor.moveToFirst();
                    while (!cursor.isAfterLast()) {
                        try {
                            App app = new App();
                            app.setmTitle(cursor.getString(cursor.getColumnIndexOrThrow(AppColumns.APP_TITLE)));
                            app.setmPackage(cursor.getString(cursor.getColumnIndexOrThrow(AppColumns.PACKAGE_NAME)));
                            app.setmIcon(mPackageManager.getApplicationIcon(app.getmPackage()));
                            app.setmAppColor(cursor.getInt(cursor.getColumnIndexOrThrow(AppColumns.PALETTE_COLOR)));
                            app.setmTextColor(cursor.getInt(cursor.getColumnIndexOrThrow(AppColumns.TEXT_COLOR)));
                            mAppsList.add(app);

                        } catch (PackageManager.NameNotFoundException e) {
                            e.printStackTrace();
                        }
                        cursor.moveToNext();

                    }
                    cursor.close();
                }
            }

            return mAppsList;
        }

        @Override
        protected void onStartLoading() {

            Timber.d("onStartLoading");
            forceLoad();
        }


    }

    /**
     * Creates shortcut on home screen for selected app
     * @throws PackageManager.NameNotFoundException
     */
    public void createShortcut() throws PackageManager.NameNotFoundException {
        Bitmap icon = getAppIconShortcut();


        Intent shortcutintent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");

        shortcutintent.putExtra("duplicate", true);

        shortcutintent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        shortcutintent.putExtra(Intent.EXTRA_SHORTCUT_NAME, mSelectedApp.getmTitle());

        Intent appIntent = new Intent(getApplicationContext(), DialogActivity.class);
        appIntent.putExtra(TARGET_PACKAGE_KEY, mSelectedApp.getmPackage());
        appIntent.putExtra(APP_COLOR_KEY, mSelectedApp.getmAppColor());
        appIntent.putExtra(TEXT_COLOR_KEY, mSelectedApp.getmTextColor());
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        shortcutintent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, appIntent);
        sendBroadcast(shortcutintent);
    }

    /**
     * Returns an app icon which has the timer icon as overlay depending on preference
     * @return
     * @throws PackageManager.NameNotFoundException
     */
    private Bitmap getAppIconShortcut() throws PackageManager.NameNotFoundException {

        Drawable iconDrawable = getPackageManager().getApplicationIcon(mSelectedApp.getmPackage());
        Bitmap appIcon = Utils.getBitmapFromDrawable(iconDrawable);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean(getString(R.string.pref_shortcut_icon_key), true)) {
            Bitmap timerIcon;
            if (mSelectedApp.getmTextColor() == 0) {

                timerIcon = Utils.getBitmapFromVectorDrawable(this, R.drawable.sandclock_icon_black);
            } else {
                timerIcon = Utils.getBitmapFromVectorDrawable(this, R.drawable.sandclock_icon_white);
            }
            int appIconWidth = appIcon.getWidth();
            int appIconHeight = appIcon.getHeight();

            Bitmap thumbBitmap = ThumbnailUtils.extractThumbnail(timerIcon, appIconWidth/3, appIconHeight/2);

            Bitmap bmOverlay = Bitmap.createBitmap(appIcon.getWidth(), appIcon.getHeight(), appIcon.getConfig());
            Canvas canvas = new Canvas(bmOverlay);
            canvas.drawBitmap(appIcon, new

                    Matrix(), null);
            canvas.drawBitmap(thumbBitmap, appIconWidth-appIconWidth/3, appIconHeight-appIconHeight/2, null);

            return bmOverlay;
        } else
            return appIcon;
    }


}
