/**
 * FeedEx
 *
 * Copyright (c) 2012-2013 Frederic Julian
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * Some parts of this software are based on "Sparse rss" under the MIT license (see
 * below). Please refers to the original project to identify which parts are under the
 * MIT license.
 *
 * Copyright (c) 2010-2012 Stefan Handschuh
 *
 *     Permission is hereby granted, free of charge, to any person obtaining a copy
 *     of this software and associated documentation files (the "Software"), to deal
 *     in the Software without restriction, including without limitation the rights
 *     to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *     copies of the Software, and to permit persons to whom the Software is
 *     furnished to do so, subject to the following conditions:
 *
 *     The above copyright notice and this permission notice shall be included in
 *     all copies or substantial portions of the Software.
 *
 *     THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *     IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *     FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *     AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *     LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *     OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *     THE SOFTWARE.
 */

package net.woocheol.inventoryman.activity;

import java.util.Date;

import net.woocheol.inventoryman.Constants;
import net.woocheol.inventoryman.R;
import net.woocheol.inventoryman.provider.FeedData;
import net.woocheol.inventoryman.provider.FeedData.EntryColumns;
import net.woocheol.inventoryman.provider.FeedData.FeedColumns;
import net.woocheol.inventoryman.provider.FeedData.TaskColumns;
import net.woocheol.inventoryman.provider.FeedDataContentProvider;
import net.woocheol.inventoryman.service.FetcherService;
import net.woocheol.inventoryman.utils.PrefUtils;
import net.woocheol.inventoryman.utils.ThrottledContentObserver;
import net.woocheol.inventoryman.utils.UiUtils;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.widget.ViewFlipper;

public class EntryActivity extends ProgressActivity {

    private static final String SAVE_INSTANCE_SCROLL_PERCENTAGE = "scrollPercentage";
    private static final String SAVE_INSTANCE_ENTRIES_IDS = "entriesIds";
    private static final String SAVE_INSTANCE_IS_FULLSCREEN = "isFullscreen";

    private static final long ANIM_DURATION = 250;
    private static final TranslateAnimation SLIDE_IN_RIGHT = generateAnimation(1, 0);
    private static final TranslateAnimation SLIDE_IN_LEFT = generateAnimation(-1, 0);
    private static final TranslateAnimation SLIDE_OUT_RIGHT = generateAnimation(0, 1);
    private static final TranslateAnimation SLIDE_OUT_LEFT = generateAnimation(0, -1);

    private static TranslateAnimation generateAnimation(float fromX, float toX) {
        TranslateAnimation anim = new TranslateAnimation(Animation.RELATIVE_TO_SELF, fromX, Animation.RELATIVE_TO_SELF, toX, 0, 0, 0, 0);
        anim.setDuration(ANIM_DURATION);
        return anim;
    }

    private static final String TEXT_HTML = "text/html";
    private static final String HTML_IMG_REGEX = "(?i)<[/]?[ ]?img(.|\n)*?>";

    private static final String BACKGROUND_COLOR = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? "#f6f6f6" : "#181b1f";
    private static final String TEXT_COLOR = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? "#000000" : "#C0C0C0";
    private static final String BUTTON_COLOR = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? "#D0D0D0" : "#505050";

    private static final String CSS = "<head><style type='text/css'>body {background-color:"
            + BACKGROUND_COLOR
            + "; max-width: 100%; font-family: sans-serif-light}\nimg {max-width: 100%; height: auto;}\ndiv[style] {max-width: 100%;}\npre {white-space: pre-wrap;}</style></head>";
    private static final String BODY_START = CSS + "<body link='#97ACE5' text='" + TEXT_COLOR + "'>";
    private static final String FONTSIZE_START = CSS + BODY_START + "<font size='+";
    private static final String FONTSIZE_MIDDLE = "'>";
    private static final String BODY_END = "<br/><br/><br/><br/></body>";
    private static final String FONTSIZE_END = "</font>" + BODY_END;
    private static final String TITLE_START = "<p style='margin-top:1cm; margin-bottom:0.6cm'><font size='+2'><a href='";
    private static final String TITLE_MIDDLE = "' style='text-decoration: none; color:inherit'>";
    private static final String TITLE_END = "</a></font></p>";
    private static final String SUBTITLE_START = "<font size='-1'>";
    private static final String SUBTITLE_END = "</font><div style='width:100%; border:0px; height:1px; margin-top:0.1cm; background:#33b5e5'/><br/><div align='justify'>";

    private static final String BUTTON_SEPARATION = "</div><br/>";

    private static final String BUTTON_START = "<div style='text-align: center'><input type='button' value='";
    private static final String BUTTON_MIDDLE = "' onclick='";
    private static final String BUTTON_END = "' style='background-color:" + BUTTON_COLOR + "; color:" + TEXT_COLOR
            + "; border: none; border-radius:0.2cm; padding: 0.3cm;'/></div>";

    private static final String LINK_BUTTON_START = "<div style='text-align: center; margin-top:0.4cm'><a href='";
    private static final String LINK_BUTTON_MIDDLE = "' style='background-color:" + BUTTON_COLOR + "; color:" + TEXT_COLOR
            + "; text-decoration: none; border: none; border-radius:0.2cm; padding: 0.3cm;'>";
    private static final String LINK_BUTTON_END = "</a></div>";

    private static final String IMAGE_ENCLOSURE = "[@]image/";

    private int titlePosition, datePosition, mobilizedHtmlPosition, abstractPosition, linkPosition, feedIdPosition, isFavoritePosition,
            isReadPosition, enclosurePosition, authorPosition;

    private long _id = -1;
    private long _nextId = -1;
    private long _previousId = -1;
    private long[] mEntriesIds;

    private Uri uri;
    private Uri parentUri;
    private int feedId;
    private boolean favorite, preferFullText = true;
    private byte[] iconBytes = null;

    private WebView webView;
    private WebView webView0; // only needed for the animation

    private ViewFlipper viewFlipper;

    private float mScrollPercentage = 0;

    private String link, title, enclosure;
    private LayoutParams layoutParams;
    private View cancelFullscreenBtn, backBtn, forwardBtn;

    private boolean mFromWidget = false;

    final private OnKeyListener onKeyEventListener = new OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
                    scrollUp();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                    scrollDown();
                    return true;
                }
            }
            return false;
        }
    };

    private GestureDetector gestureDetector;

    final private OnTouchListener onTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
        }
    };

    private final ThrottledContentObserver mTasksObserver = new ThrottledContentObserver(new Handler(), 2000) {
        @Override
        public void onChangeThrottled() {
            boolean isMobilizing = FetcherService.getMobilizingTaskId(_id) != -1;
            if ((getProgressBar().getVisibility() == View.VISIBLE && isMobilizing)
                    || (getProgressBar().getVisibility() == View.GONE && !isMobilizing)) {
                return; // no change => no update
            }

            if (isMobilizing) { // We start a mobilization
                getProgressBar().setVisibility(View.VISIBLE);
            } else { // We finished one
                preferFullText = true;
                reload(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiUtils.setPreferenceTheme(this);
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.entry);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (Math.abs(velocityY) * 1.5 < Math.abs(velocityX)) {
                    if (velocityX > 800) {
                        if (_previousId != -1 && webView.getScrollX() == 0) {
                            previousEntry();
                        }
                    } else if (velocityX < -800) {
                        if (_nextId != -1) {
                            nextEntry();
                        }
                    }
                }

                return false;
            }
        });

        uri = getIntent().getData();
        parentUri = EntryColumns.PARENT_URI(uri.getPath());
        feedId = 0;

        Bundle b = getIntent().getExtras();
        if (b != null) {
            mFromWidget = b.getBoolean(Constants.INTENT_FROM_WIDGET, false);
        }

        Cursor entryCursor = getContentResolver().query(uri, null, null, null, null);

        titlePosition = entryCursor.getColumnIndex(EntryColumns.TITLE);
        datePosition = entryCursor.getColumnIndex(EntryColumns.DATE);
        abstractPosition = entryCursor.getColumnIndex(EntryColumns.ABSTRACT);
        mobilizedHtmlPosition = entryCursor.getColumnIndex(EntryColumns.MOBILIZED_HTML);
        linkPosition = entryCursor.getColumnIndex(EntryColumns.LINK);
        feedIdPosition = entryCursor.getColumnIndex(EntryColumns.FEED_ID);
        isFavoritePosition = entryCursor.getColumnIndex(EntryColumns.IS_FAVORITE);
        isReadPosition = entryCursor.getColumnIndex(EntryColumns.IS_READ);
        enclosurePosition = entryCursor.getColumnIndex(EntryColumns.ENCLOSURE);
        authorPosition = entryCursor.getColumnIndex(EntryColumns.AUTHOR);

        entryCursor.close();

        cancelFullscreenBtn = findViewById(R.id.cancelFullscreenBtn);
        backBtn = findViewById(R.id.backBtn);
        forwardBtn = findViewById(R.id.forwardBtn);

        viewFlipper = (ViewFlipper) findViewById(R.id.content_flipper);

        layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        webView = new WebView(this);
        setupWebview(webView);
        viewFlipper.addView(webView, layoutParams);

        webView0 = new WebView(this);
        setupWebview(webView0);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        outState.putLongArray(SAVE_INSTANCE_ENTRIES_IDS, mEntriesIds);
        outState.putBoolean(SAVE_INSTANCE_IS_FULLSCREEN, !getActionBar().isShowing());

        float positionTopView = webView.getTop();
        float contentHeight = webView.getContentHeight();
        float currentScrollPosition = webView.getScrollY();

        outState.putFloat(SAVE_INSTANCE_SCROLL_PERCENTAGE, (currentScrollPosition - positionTopView) / contentHeight);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mEntriesIds = savedInstanceState.getLongArray(SAVE_INSTANCE_ENTRIES_IDS);
        mScrollPercentage = savedInstanceState.getFloat(SAVE_INSTANCE_SCROLL_PERCENTAGE);
        if (savedInstanceState.getBoolean(SAVE_INSTANCE_IS_FULLSCREEN)) {
            toggleFullScreen();
        }
        webView.restoreState(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Constants.NOTIF_MGR != null) {
            Constants.NOTIF_MGR.cancel(0);
        }

        uri = getIntent().getData();
        parentUri = EntryColumns.PARENT_URI(uri.getPath());

        try {
            webView.onResume();
        } catch (Exception unused) { // Seems possible to have an NPE here on some phones...
        }

        reload(false);
    }

    @Override
    protected void onPause() {
        super.onPause();

        try {
            webView.onPause();
        } catch (Exception unused) { // Seems possible to have an NPE here on some phones...
        }

        getContentResolver().unregisterContentObserver(mTasksObserver);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private void toggleFullScreen() {
        if (getActionBar().isShowing()) {
            getActionBar().hide();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            cancelFullscreenBtn.setVisibility(View.VISIBLE);
        } else {
            getActionBar().show();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            cancelFullscreenBtn.setVisibility(View.GONE);
        }
    }

    private void reload(boolean forceUpdate) {
        long newId = Long.parseLong(uri.getLastPathSegment());
        if (!forceUpdate && _id == newId) {
            return;
        }

        _id = newId;

        ContentResolver cr = getContentResolver();
        Cursor entryCursor = cr.query(uri, null, null, null, null);

        if (entryCursor.moveToFirst()) {
            String contentText = entryCursor.getString(mobilizedHtmlPosition);
            if (contentText == null || (forceUpdate && !preferFullText)) {
                preferFullText = false;
                contentText = entryCursor.getString(abstractPosition);
            } else {
                preferFullText = true;
            }
            if (contentText == null) {
                contentText = "";
            }

            // Need to be done before the "mark as read" action
            setupNavigationButton();

            // Mark the article as read
            if (entryCursor.getInt(isReadPosition) != 1) {
                if (cr.update(uri, FeedData.getReadContentValues(), null, null) > 0) {
                    FeedDataContentProvider.notifyAllFromEntryUri(uri, false);
                }
            }

            int _feedId = entryCursor.getInt(feedIdPosition);
            if (feedId != _feedId) {
                if (feedId != 0) {
                    iconBytes = null; // triggers re-fetch of the icon
                }
                feedId = _feedId;
            }

            title = entryCursor.getString(titlePosition);
            Cursor cursor = cr.query(FeedColumns.CONTENT_URI(feedId), new String[]{FeedColumns.NAME, FeedColumns.URL}, null, null, null);
            if (cursor.moveToFirst()) {
                setTitle(cursor.isNull(0) ? cursor.getString(1) : cursor.getString(0));
            } else { // fallback, should not be possible
                setTitle(title);
            }
            cursor.close();

            if (iconBytes == null || iconBytes.length == 0) {
                Cursor iconCursor = cr.query(FeedColumns.CONTENT_URI(Integer.toString(feedId)),
                        new String[]{FeedColumns._ID, FeedColumns.ICON}, null, null, null);

                if (iconCursor.moveToFirst()) {
                    iconBytes = iconCursor.getBlob(1);
                }
                iconCursor.close();
            }

            if (iconBytes != null && iconBytes.length > 0) {
                int bitmapSizeInDip = UiUtils.dpToPixel(24);
                Bitmap bitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.length);
                if (bitmap != null) {
                    if (bitmap.getHeight() != bitmapSizeInDip) {
                        bitmap = Bitmap.createScaledBitmap(bitmap, bitmapSizeInDip, bitmapSizeInDip, false);
                    }

                    getActionBar().setIcon(new BitmapDrawable(getResources(), bitmap));
                } else {
                    getActionBar().setIcon(R.drawable.icon);
                }
            } else {
                getActionBar().setIcon(R.drawable.icon);
            }

            favorite = entryCursor.getInt(isFavoritePosition) == 1;
            invalidateOptionsMenu();

            // loadData does not recognize the encoding without correct html-header
            boolean localPictures = contentText.contains(Constants.IMAGEID_REPLACEMENT);

            if (localPictures) {
                contentText = contentText.replace(Constants.IMAGEID_REPLACEMENT, _id + Constants.IMAGEFILE_IDSEPARATOR);
            }

            if (PrefUtils.getBoolean(PrefUtils.DISABLE_PICTURES, false)) {
                contentText = contentText.replaceAll(HTML_IMG_REGEX, "");
                webView.getSettings().setBlockNetworkImage(true);
            } else {
                if (webView.getSettings().getBlockNetworkImage()) {
                    // setBlockNetwortImage(false) calls postSync, which takes time, so we clean up the html first and change the value afterwards
                    webView.loadData("", TEXT_HTML, Constants.UTF8);
                    webView.getSettings().setBlockNetworkImage(false);
                }
            }

            String author = entryCursor.getString(authorPosition);
            long timestamp = entryCursor.getLong(datePosition);
            link = entryCursor.getString(linkPosition);
            enclosure = entryCursor.getString(enclosurePosition);

            // String baseUrl = "";
            // try {
            // URL url = new URL(link);
            // baseUrl = url.getProtocol() + "://" + url.getHost();
            // } catch (MalformedURLException ignored) {
            // }
            webView.loadDataWithBaseURL("", generateHtmlContent(title, link, contentText, enclosure, author, timestamp), TEXT_HTML, Constants.UTF8,
                    null); // do not put 'null' to the base url...

            // Listen the mobilizing task
            long mobilizingTaskId = FetcherService.getMobilizingTaskId(_id);
            if (mobilizingTaskId != -1) {
                getProgressBar().setVisibility(View.VISIBLE);
                cr.unregisterContentObserver(mTasksObserver);
                cr.registerContentObserver(TaskColumns.CONTENT_URI(mobilizingTaskId), false, mTasksObserver);
            } else {
                getProgressBar().setVisibility(View.GONE);
                cr.unregisterContentObserver(mTasksObserver);
            }
        }

        entryCursor.close();
    }

    private String generateHtmlContent(String title, String link, String abstractText, String enclosure, String author, long timestamp) {
        StringBuilder content = new StringBuilder();

        int fontSize = Integer.parseInt(PrefUtils.getString(PrefUtils.FONT_SIZE, "0"));
        if (fontSize > 0) {
            content.append(FONTSIZE_START).append(fontSize).append(FONTSIZE_MIDDLE);
        } else {
            content.append(BODY_START);
        }

        if (link == null) {
            link = "";
        }
        content.append(TITLE_START).append(link).append(TITLE_MIDDLE).append(title).append(TITLE_END).append(SUBTITLE_START);
        Date date = new Date(timestamp);
        StringBuilder dateStringBuilder = new StringBuilder(DateFormat.getDateFormat(this).format(date)).append(' ').append(
                DateFormat.getTimeFormat(this).format(date));

        if (author != null && !author.isEmpty()) {
            dateStringBuilder.append(" &mdash; ").append(author);
        }
        content.append(dateStringBuilder).append(SUBTITLE_END).append(abstractText).append(BUTTON_SEPARATION).append(BUTTON_START);

        if (!preferFullText) {
            content.append(getString(R.string.get_full_text)).append(BUTTON_MIDDLE).append("injectedJSObject.onClickFullText();");
        } else {
            content.append(getString(R.string.original_text)).append(BUTTON_MIDDLE).append("injectedJSObject.onClickOriginalText();");
        }
        content.append(BUTTON_END);

        if (enclosure != null && enclosure.length() > 6 && !enclosure.contains(IMAGE_ENCLOSURE)) {
            content.append(BUTTON_START).append(getString(R.string.see_enclosure)).append(BUTTON_MIDDLE)
                    .append("injectedJSObject.onClickEnclosure();").append(BUTTON_END);
        }

        if (link != null && link.length() > 0) {
            content.append(LINK_BUTTON_START).append(link).append(LINK_BUTTON_MIDDLE).append(getString(R.string.see_link)).append(LINK_BUTTON_END);
        }

        if (fontSize > 0) {
            content.append(FONTSIZE_END);
        } else {
            content.append(BODY_END);
        }

        return content.toString();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebview(final WebView wv) {
        // For color
        wv.setBackgroundColor(Color.parseColor(BACKGROUND_COLOR));

        // For scrolling & gesture
        wv.setOnKeyListener(onKeyEventListener);
        wv.setOnTouchListener(onTouchListener);

        // For javascript
        wv.getSettings().setJavaScriptEnabled(true);
        wv.addJavascriptInterface(injectedJSObject, "injectedJSObject");

        // For HTML5 video
        wv.setWebChromeClient(new WebChromeClient());

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (mScrollPercentage != 0) {
                    view.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            float webviewsize = wv.getContentHeight() - wv.getTop();
                            float positionInWV = webviewsize * mScrollPercentage;
                            int positionY = Math.round(wv.getTop() + positionInWV);
                            wv.scrollTo(0, positionY);
                        }
                        // Delay the scrollTo to make it work
                    }, 150);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                try {
                    // Otherwise, the link is not for a page on my site, so launch another Activity that handles URLs
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(EntryActivity.this, R.string.cant_open_link, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
    }

    private void showEnclosure(Uri uri, String enclosure, int position1, int position2) {
        try {
            startActivityForResult(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, enclosure.substring(position1 + 3, position2)), 0);
        } catch (Exception e) {
            try {
                startActivityForResult(new Intent(Intent.ACTION_VIEW, uri), 0); // fallbackmode - let the browser handle this
            } catch (Throwable t) {
                Toast.makeText(EntryActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupNavigationButton() {
        _previousId = -1;
        backBtn.setVisibility(View.GONE);
        _nextId = -1;
        forwardBtn.setVisibility(View.GONE);

        if (mEntriesIds == null) {
            Cursor cursor = getContentResolver().query(parentUri, EntryColumns.PROJECTION_ID,
                    PrefUtils.getBoolean(PrefUtils.SHOW_READ, true) || EntryColumns.FAVORITES_CONTENT_URI.equals(parentUri) ? null
                            : EntryColumns.WHERE_UNREAD, null, EntryColumns.DATE + Constants.DB_DESC);

            mEntriesIds = new long[cursor.getCount()];
            int i = 0;
            while (cursor.moveToNext()) {
                mEntriesIds[i++] = cursor.getLong(0);
            }

            cursor.close();
        }

        for (int i = 0; i < mEntriesIds.length; ++i) {
            if (_id == mEntriesIds[i]) {
                if (i > 0) {
                    _previousId = mEntriesIds[i - 1];
                    backBtn.setVisibility(View.VISIBLE);
                }

                if (i < mEntriesIds.length - 1) {
                    _nextId = mEntriesIds[i + 1];
                    forwardBtn.setVisibility(View.VISIBLE);
                }

                break;
            }
        }
    }

    private void switchEntry(long id, Animation inAnimation, Animation outAnimation) {
        uri = parentUri.buildUpon().appendPath(String.valueOf(id)).build();
        getIntent().setData(uri);
        mScrollPercentage = 0;

        WebView tmp = webView; // switch reference

        webView = webView0;
        webView0 = tmp;

        reload(false);

        viewFlipper.setInAnimation(inAnimation);
        viewFlipper.setOutAnimation(outAnimation);
        viewFlipper.addView(webView, layoutParams);
        viewFlipper.showNext();
        viewFlipper.removeViewAt(0);

        // To clear memory and avoid possible glitches
        viewFlipper.postDelayed(new Runnable() {
            @Override
            public void run() {
                webView0.loadUrl("about:blank");
            }
        }, ANIM_DURATION);
    }

    private void nextEntry() {
        switchEntry(_nextId, SLIDE_IN_RIGHT, SLIDE_OUT_LEFT);
    }

    private void previousEntry() {
        switchEntry(_previousId, SLIDE_IN_LEFT, SLIDE_OUT_RIGHT);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.entry, menu);

        if (favorite) {
            MenuItem item = menu.findItem(R.id.menu_star);
            item.setTitle(R.string.menu_unstar).setIcon(R.drawable.rating_important);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (mFromWidget) {
                    Intent intent = new Intent(this, MainActivity.class);
                    startActivity(intent);
                }
                finish();

                return true;
            case R.id.menu_star:
                favorite = !favorite;

                ContentValues values = new ContentValues();
                values.put(EntryColumns.IS_FAVORITE, favorite ? 1 : 0);
                ContentResolver cr = getContentResolver();
                if (cr.update(uri, values, null, null) > 0) {
                    FeedDataContentProvider.notifyAllFromEntryUri(uri, true);
                }

                if (favorite) {
                    item.setTitle(R.string.menu_unstar).setIcon(R.drawable.rating_important);
                } else {
                    item.setTitle(R.string.menu_star).setIcon(R.drawable.rating_not_important);
                }
                break;
            case R.id.menu_share:
                if (link != null) {
                    startActivity(Intent.createChooser(
                            new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, link)
                                    .setType(Constants.MIMETYPE_TEXT_PLAIN), getString(R.string.menu_share)));
                }
                break;
            case R.id.menu_full_screen: {
                toggleFullScreen();
                break;
            }
            case R.id.menu_copy_clipboard: {
                ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = android.content.ClipData.newPlainText("Copied Text", link);
                clipboard.setPrimaryClip(clip);

                Toast.makeText(this, R.string.copied_clipboard, Toast.LENGTH_SHORT).show();
                break;
            }
            case R.id.menu_mark_as_unread:
                new Thread() {
                    @Override
                    public void run() {
                        ContentResolver cr = getContentResolver();
                        if (cr.update(uri, FeedData.getUnreadContentValues(), null, null) > 0) {
                            FeedDataContentProvider.notifyAllFromEntryUri(uri, false);
                        }
                    }
                }.start();
                finish();
                break;
        }

        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
                scrollUp();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                scrollDown();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void scrollUp() {
        if (webView != null) {
            webView.pageUp(false);
        }
    }

    private void scrollDown() {
        if (webView != null) {
            webView.pageDown(false);
        }
    }

    /**
     * Works around android issue 6191
     */
    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        try {
            super.unregisterReceiver(receiver);
        } catch (Exception e) {
            // do nothing
        }
    }

    public void onClickBackBtn(View view) {
        previousEntry();
    }

    public void onClickForwardBtn(View view) {
        nextEntry();
    }

    public void onClickCancelFullscreenBtn(View view) {
        toggleFullScreen();
    }

    private class JavaScriptObject {
        @Override
        @JavascriptInterface
        public String toString() {
            return "injectedJSObject";
        }

        @JavascriptInterface
        public void onClickOriginalText() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    preferFullText = false;
                    reload(true);
                }
            });
        }

        @JavascriptInterface
        public void onClickFullText() {
            if (getProgressBar().getVisibility() != View.VISIBLE) {
                ContentResolver cr = getContentResolver();
                Cursor entryCursor = cr.query(uri, null, null, null, null);
                final boolean alreadyMobilized = entryCursor.moveToFirst() && !entryCursor.isNull(mobilizedHtmlPosition);
                entryCursor.close();

                if (alreadyMobilized) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            preferFullText = true;
                            reload(true);
                        }
                    });
                } else {
                    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

                    // since we have acquired the networkInfo, we use it for basic checks
                    if (networkInfo != null && networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                        FetcherService.addEntriesToMobilize(new long[]{_id});
                        long mobilizingTaskId = FetcherService.getMobilizingTaskId(_id);
                        if (mobilizingTaskId != -1) {
                            cr.unregisterContentObserver(mTasksObserver);
                            cr.registerContentObserver(TaskColumns.CONTENT_URI(mobilizingTaskId), false, mTasksObserver);
                            startService(new Intent(EntryActivity.this, FetcherService.class).setAction(FetcherService.ACTION_MOBILIZE_FEEDS));
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    getProgressBar().setVisibility(View.VISIBLE);
                                }
                            });
                        }
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(EntryActivity.this, R.string.network_error, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        }

        @JavascriptInterface
        public void onClickEnclosure() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final int position1 = enclosure.indexOf(Constants.ENCLOSURE_SEPARATOR);
                    final int position2 = enclosure.indexOf(Constants.ENCLOSURE_SEPARATOR, position1 + 3);

                    Uri uri = Uri.parse(enclosure.substring(0, position1));
                    showEnclosure(uri, enclosure, position1, position2);
                }
            });
        }
    }

    private final JavaScriptObject injectedJSObject = new JavaScriptObject();
}
