package local.cloudcli.shell;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 3010;
    private static final int SAVE_FILE_REQUEST = 3011;
    private static final int MICROPHONE_PERMISSION_REQUEST = 3012;
    private static final String STATE_URL = "cloudcli_url";
    private static final int SURFACE = 0xff07132f;
    private static final int SURFACE_ELEVATED = 0xff12224a;
    private static final int DEVICE_BAR_HEIGHT_DP = 40;
    private static final int TEXT_PRIMARY = 0xffeef6ff;
    private static final int TEXT_SECONDARY = 0xff9fb3d9;
    private static final int ACCENT = 0xff22d3ee;

    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();
    private DeviceProfileStore deviceStore;
    private DeviceProfileStore.Profile activeProfile;
    private WebView webView;
    private ProgressBar progressBar;
    private FrameLayout stateOverlay;
    private ProgressBar stateSpinner;
    private TextView stateTitle;
    private TextView stateMessage;
    private Button primaryAction;
    private Button secondaryAction;
    private Button deviceButton;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri capturedPhotoUri;
    private PermissionRequest pendingPermissionRequest;
    private PendingDownload pendingDownload;
    private boolean serviceUnavailable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView.setWebContentsDebuggingEnabled(false);
        configureWindow();
        deviceStore = new DeviceProfileStore(this);
        activeProfile = deviceStore.getActiveProfile();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(SURFACE);
        applySystemBarInsets(root);

        webView = new GestureSafeWebView(this);
        webView.setBackgroundColor(SURFACE);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        FrameLayout.LayoutParams webParams = matchParent();
        webParams.topMargin = dp(DEVICE_BAR_HEIGHT_DP);
        root.addView(webView, webParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        progressBar.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        progressParams.topMargin = dp(DEVICE_BAR_HEIGHT_DP);
        root.addView(progressBar, progressParams);

        createStateOverlay(root);
        createDeviceBar(root);
        setContentView(root);
        root.requestApplyInsets();
        pruneCaptureCache();

        configureWebView();
        configureBackNavigation();
        String initialUrl = deviceStore.getLastUrl(activeProfile);
        if (savedInstanceState != null) {
            String savedUrl = savedInstanceState.getString(STATE_URL);
            if (savedUrl != null && isActiveCloudCLIUrl(Uri.parse(savedUrl))) {
                initialUrl = savedUrl;
            }
        }
        // Restore the route, not the previous WebView DOM snapshot. A DOM
        // snapshot can resurrect obsolete hashed assets after a localhost
        // rebuild or APK update, bypassing the cache policy entirely.
        showConnectingState();
        webView.loadUrl(initialUrl);
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(0xff050b1d);
        window.setNavigationBarColor(0xff050b1d);
        if (Build.VERSION.SDK_INT >= 28) {
            window.setNavigationBarDividerColor(0xff050b1d);
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void applySystemBarInsets(final View root) {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                } else {
                    view.setPadding(
                            insets.getSystemWindowInsetLeft(),
                            insets.getSystemWindowInsetTop(),
                            insets.getSystemWindowInsetRight(),
                            insets.getSystemWindowInsetBottom());
                }
                return insets;
            }
        });
    }

    private void createStateOverlay(FrameLayout root) {
        stateOverlay = new FrameLayout(this);
        stateOverlay.setBackgroundColor(SURFACE);
        stateOverlay.setClickable(true);
        stateOverlay.setFocusable(true);
        FrameLayout.LayoutParams overlayParams = matchParent();
        overlayParams.topMargin = dp(DEVICE_BAR_HEIGHT_DP);
        root.addView(stateOverlay, overlayParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(28), dp(28), dp(28), dp(28));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        cardParams.leftMargin = dp(24);
        cardParams.rightMargin = dp(24);
        stateOverlay.addView(card, cardParams);

        stateSpinner = new ProgressBar(this);
        stateSpinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        spinnerParams.bottomMargin = dp(22);
        card.addView(stateSpinner, spinnerParams);

        stateTitle = new TextView(this);
        stateTitle.setTextColor(TEXT_PRIMARY);
        stateTitle.setTextSize(22);
        stateTitle.setGravity(Gravity.CENTER);
        stateTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        card.addView(stateTitle, wrapContent());

        stateMessage = new TextView(this);
        stateMessage.setTextColor(TEXT_SECONDARY);
        stateMessage.setTextSize(15);
        stateMessage.setGravity(Gravity.CENTER);
        stateMessage.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams messageParams = wrapContent();
        messageParams.topMargin = dp(10);
        messageParams.bottomMargin = dp(24);
        card.addView(stateMessage, messageParams);

        primaryAction = createButton(true);
        primaryAction.setText("重新连接");
        primaryAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showConnectingState();
                webView.loadUrl(deviceStore.getLastUrl(activeProfile));
            }
        });
        card.addView(primaryAction, fullWidthButton());

        secondaryAction = createButton(false);
        secondaryAction.setText("关闭");
        secondaryAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveTaskToBack(true);
            }
        });
        LinearLayout.LayoutParams secondaryParams = fullWidthButton();
        secondaryParams.topMargin = dp(10);
        card.addView(secondaryAction, secondaryParams);
    }

    private void createDeviceBar(FrameLayout root) {
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(0xff050b1d);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(DEVICE_BAR_HEIGHT_DP), Gravity.TOP);
        root.addView(bar, barParams);

        TextView label = new TextView(this);
        label.setText("CloudCLI Shell");
        label.setTextColor(TEXT_SECONDARY);
        label.setTextSize(13);
        label.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        labelParams.leftMargin = dp(14);
        bar.addView(label, labelParams);

        deviceButton = createButton(false);
        deviceButton.setTextSize(13);
        deviceButton.setSingleLine(true);
        deviceButton.setEllipsize(TextUtils.TruncateAt.END);
        deviceButton.setMinHeight(dp(38));
        deviceButton.setMinWidth(dp(72));
        deviceButton.setPadding(dp(12), 0, dp(12), 0);
        deviceButton.setAlpha(0.94f);
        deviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showConnectingState();
                webView.loadUrl(DeviceProfileStore.LOCAL_URL);
            }
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38), Gravity.CENTER_VERTICAL | Gravity.END);
        params.rightMargin = dp(8);
        bar.addView(deviceButton, params);
        updateDeviceButton();
    }

    private void showDevicePicker() {
        final List<DeviceProfileStore.Profile> profiles = deviceStore.getProfiles();
        String[] labels = new String[profiles.size()];
        for (int index = 0; index < profiles.size(); index++) {
            DeviceProfileStore.Profile profile = profiles.get(index);
            labels[index] = (profile.id.equals(activeProfile.id) ? "✓ " : "    ")
                    + profile.name + "\n" + profile.baseUrl;
        }
        new AlertDialog.Builder(this)
                .setTitle("CloudCLI 设备")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switchToDevice(profiles.get(which));
                    }
                })
                .setPositiveButton("添加设备", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showDeviceEditor(null);
                    }
                })
                .setNeutralButton("编辑当前", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showDeviceEditor(activeProfile);
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showDeviceEditor(final DeviceProfileStore.Profile existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("本机");
        nameInput.setSingleLine(true);
        nameInput.setText(existing == null ? "" : existing.name);
        form.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText urlInput = new EditText(this);
        urlInput.setHint(DeviceProfileStore.LOCAL_URL);
        urlInput.setSingleLine(true);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setText(existing == null ? "" : existing.baseUrl);
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        urlParams.topMargin = dp(8);
        form.addView(urlInput, urlParams);

        TextView hint = new TextView(this);
        hint.setText("CloudCLI Shell 固定连接这台手机上的本机服务，地址不可修改。");
        hint.setTextColor(TEXT_SECONDARY);
        hint.setTextSize(13);
        LinearLayout.LayoutParams hintParams = wrapContent();
        hintParams.topMargin = dp(10);
        form.addView(hint, hintParams);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "添加 CloudCLI 设备" : "编辑 CloudCLI 设备")
                .setView(form)
                .setPositiveButton("保存并连接", null)
                .setNegativeButton("取消", null)
                .setNeutralButton(existing != null && !existing.isLocal() ? "删除" : null, null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        try {
                            DeviceProfileStore.Profile saved = existing == null
                                    ? deviceStore.create(nameInput.getText().toString(), urlInput.getText().toString())
                                    : deviceStore.update(existing, nameInput.getText().toString(), urlInput.getText().toString());
                            dialog.dismiss();
                            switchToDevice(saved);
                        } catch (IllegalArgumentException error) {
                            Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
                if (existing != null && !existing.isLocal()) {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            confirmDeleteDevice(dialog, existing);
                        }
                    });
                }
            }
        });
        dialog.show();
    }

    private void confirmDeleteDevice(final AlertDialog editor, final DeviceProfileStore.Profile profile) {
        new AlertDialog.Builder(this)
                .setTitle("删除 " + profile.name + "？")
                .setMessage("只会删除手机上的设备入口，不会删除远端 CloudCLI 数据。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean deletingActive = profile.id.equals(activeProfile.id);
                        deviceStore.delete(profile);
                        editor.dismiss();
                        if (deletingActive) {
                            activeProfile = deviceStore.getActiveProfile();
                            updateDeviceButton();
                            showConnectingState();
                            webView.loadUrl(deviceStore.getLastUrl(activeProfile));
                        } else {
                            showDevicePicker();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void switchToDevice(DeviceProfileStore.Profile profile) {
        if (webView != null && activeProfile != null) {
            deviceStore.saveLastUrl(activeProfile, webView.getUrl());
            webView.stopLoading();
        }
        activeProfile = profile;
        deviceStore.setActive(profile);
        updateDeviceButton();
        showConnectingState();
        if (webView != null) webView.loadUrl(deviceStore.getLastUrl(profile));
    }

    private void updateDeviceButton() {
        if (deviceButton != null && activeProfile != null) {
            deviceButton.setText("刷新");
            deviceButton.setContentDescription("重新加载本机 CloudCLI");
        }
    }

    private Button createButton(boolean primary) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(primary ? Color.WHITE : TEXT_PRIMARY);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                primary ? ACCENT : SURFACE_ELEVATED));
        button.setMinHeight(dp(50));
        return button;
    }

    private void showConnectingState() {
        serviceUnavailable = false;
        stateTitle.setText("正在连接 CloudCLI");
        stateMessage.setText("正在连接这台手机上的本机 Agent 服务…");
        stateSpinner.setVisibility(View.VISIBLE);
        primaryAction.setVisibility(View.GONE);
        secondaryAction.setVisibility(View.GONE);
        stateOverlay.setVisibility(View.VISIBLE);
    }

    private void showServiceUnavailable() {
        serviceUnavailable = true;
        stateTitle.setText("CloudCLI 服务未启动");
        if (activeProfile.isLocal()) {
            stateMessage.setText("请确认 Termux 服务正在运行，然后重新连接。\n\nsv up cloudcli");
        } else {
            stateMessage.setText(activeProfile.baseUrl
                    + "\n\n请确认设备在线、CloudCLI 已启动，并且手机能访问该局域网或 VPN 地址。");
        }
        stateSpinner.setVisibility(View.GONE);
        primaryAction.setVisibility(View.VISIBLE);
        secondaryAction.setVisibility(View.VISIBLE);
        stateOverlay.setVisibility(View.VISIBLE);
    }

    private void hideStateOverlay() {
        serviceUnavailable = false;
        stateOverlay.setVisibility(View.GONE);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        // The UI is served by a local process and rebuilt in place. Reusing a
        // cached index can pin the WebView to obsolete hashed assets even after
        // the service restarts, which makes a successful deployment look as if
        // it never refreshed. Always revalidate from localhost; cookies, DOM
        // storage, drafts, and CloudCLI sessions are intentionally left intact.
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        settings.setUserAgentString(settings.getUserAgentString() + " CloudCLIShell/1.0");

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new DeviceBoundClient());
        webView.setWebChromeClient(new ShellChromeClient());
        webView.setDownloadListener(new LocalDownloadListener());
    }

    private void configureBackNavigation() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    new android.window.OnBackInvokedCallback() {
                        @Override
                        public void onBackInvoked() {
                            handleBack();
                        }
                    });
        }
    }

    private void handleBack() {
        // Android back must never expose the WebView's browser history. Keep the
        // current conversation alive in the task and return to the previous app.
        moveTaskToBack(true);
    }

    private final class ShellChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public boolean onShowFileChooser(
                WebView view,
                ValueCallback<Uri[]> callback,
                FileChooserParams params) {
            cancelFileChooser();
            filePathCallback = callback;
            Intent picker = buildFilePicker(params);
            List<Intent> initialIntents = new ArrayList<Intent>();
            if (acceptsImages(params) && params.getMode() != FileChooserParams.MODE_OPEN_MULTIPLE) {
                Intent camera = buildCameraIntent();
                if (camera != null) initialIntents.add(camera);
            }
            Intent chooser = Intent.createChooser(picker, "选择要发送的内容");
            if (!initialIntents.isEmpty()) {
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                        initialIntents.toArray(new Intent[initialIntents.size()]));
            }
            try {
                startActivityForResult(chooser, FILE_CHOOSER_REQUEST);
                return true;
            } catch (ActivityNotFoundException error) {
                cancelFileChooser();
                Toast.makeText(MainActivity.this, "没有可用的文件选择器", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleWebPermissionRequest(request);
                }
            });
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            if (pendingPermissionRequest == request) pendingPermissionRequest = null;
        }
    }

    private Intent buildFilePicker(WebChromeClient.FileChooserParams params) {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        String[] types = cleanAcceptTypes(params.getAcceptTypes());
        if (types.length == 1) {
            picker.setType(types[0]);
        } else {
            picker.setType("*/*");
            if (types.length > 1) picker.putExtra(Intent.EXTRA_MIME_TYPES, types);
        }
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE);
        return picker;
    }

    private Intent buildCameraIntent() {
        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (camera.resolveActivity(getPackageManager()) == null) return null;
        try {
            File captureDir = new File(getCacheDir(), "capture");
            if (!captureDir.exists() && !captureDir.mkdirs()) return null;
            File photo = new File(captureDir, "photo-" + System.currentTimeMillis() + ".jpg");
            capturedPhotoUri = LocalFileProvider.uriForCapture(photo);
            camera.putExtra(MediaStore.EXTRA_OUTPUT, capturedPhotoUri);
            camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            camera.setClipData(ClipData.newRawUri("photo", capturedPhotoUri));
            return camera;
        } catch (RuntimeException error) {
            capturedPhotoUri = null;
            return null;
        }
    }

    private static String[] cleanAcceptTypes(String[] rawTypes) {
        ArrayList<String> types = new ArrayList<String>();
        if (rawTypes != null) {
            for (String type : rawTypes) {
                if (type == null) continue;
                String clean = type.trim().toLowerCase(Locale.ROOT);
                if (!clean.isEmpty() && clean.contains("/") && !types.contains(clean)) {
                    types.add(clean);
                }
            }
        }
        if (types.isEmpty()) types.add("*/*");
        return types.toArray(new String[types.size()]);
    }

    private static boolean acceptsImages(WebChromeClient.FileChooserParams params) {
        for (String type : cleanAcceptTypes(params.getAcceptTypes())) {
            if ("*/*".equals(type) || type.startsWith("image/")) return true;
        }
        return false;
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        if (!isActiveCloudCLIUrl(request.getOrigin())) {
            request.deny();
            return;
        }
        List<String> resources = Arrays.asList(request.getResources());
        if (!resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
            request.deny();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            return;
        }
        pendingPermissionRequest = request;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE_PERMISSION_REQUEST);
    }

    private final class DeviceBoundClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isActiveCloudCLIUrl(uri)) return false;
            openExternal(uri);
            return true;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            if (isActiveCloudCLIUrl(uri)) return false;
            openExternal(uri);
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri == null ? null : uri.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && !isActiveCloudCLIUrl(uri)) {
                return blockedWebResource();
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            if (isActiveCloudCLIUrl(Uri.parse(url))) {
                disableBrowserHistoryGestures(view);
                hideStateOverlay();
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (isActiveCloudCLIUrl(Uri.parse(url))) deviceStore.saveLastUrl(activeProfile, url);
            CookieManager.getInstance().flush();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) showServiceUnavailable();
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (Build.VERSION.SDK_INT < 23) showServiceUnavailable();
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            Toast.makeText(MainActivity.this, "页面进程已恢复", Toast.LENGTH_SHORT).show();
            ViewParent parent = view.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(view);
            view.destroy();
            webView = null;
            recreate();
            return true;
        }
    }

    private void disableBrowserHistoryGestures(WebView view) {
        view.evaluateJavascript(
                "(function(){"
                        + "var id='cloudcli-native-gesture-policy';"
                        + "if(document.getElementById(id))return;"
                        + "var style=document.createElement('style');"
                        + "style.id=id;"
                        + "style.textContent='html,body{overscroll-behavior-x:none!important;}'"
                        + ";(document.head||document.documentElement).appendChild(style);"
                        + "})();",
                null);
    }

    private final class LocalDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(
                String url,
                String userAgent,
                String contentDisposition,
                String mimeType,
                long contentLength) {
            Uri uri = Uri.parse(url);
            if (!isActiveCloudCLIUrl(uri)) {
                Toast.makeText(MainActivity.this, "已阻止非本机下载", Toast.LENGTH_SHORT).show();
                return;
            }
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            pendingDownload = new PendingDownload(url, userAgent, mimeType, fileName);
            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            save.addCategory(Intent.CATEGORY_OPENABLE);
            save.setType(mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType);
            save.putExtra(Intent.EXTRA_TITLE, fileName);
            try {
                startActivityForResult(save, SAVE_FILE_REQUEST);
            } catch (ActivityNotFoundException error) {
                pendingDownload = null;
                Toast.makeText(MainActivity.this, "没有可用的保存位置", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveDownload(final PendingDownload download, final Uri destination) {
        Toast.makeText(this, "正在下载…", Toast.LENGTH_SHORT).show();
        downloadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String failure = null;
                try {
                    copyLocalDownload(download, destination);
                } catch (Exception error) {
                    failure = error.getMessage();
                }
                final String result = failure;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null) {
                            Toast.makeText(MainActivity.this, "文件已保存", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this, "下载失败，请重试", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }

    private void copyLocalDownload(PendingDownload download, Uri destination) throws Exception {
        URL current = new URL(download.url);
        HttpURLConnection connection = null;
        for (int redirects = 0; redirects <= 5; redirects++) {
            Uri checked = Uri.parse(current.toString());
            if (!isActiveCloudCLIUrl(checked)) throw new SecurityException("Cross-origin redirect blocked");
            connection = (HttpURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            if (download.userAgent != null) connection.setRequestProperty("User-Agent", download.userAgent);
            String cookie = CookieManager.getInstance().getCookie(current.toString());
            if (cookie != null) connection.setRequestProperty("Cookie", cookie);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new java.io.IOException("Redirect without location");
                current = new URL(current, location);
                connection = null;
                continue;
            }
            if (status < 200 || status >= 300) {
                throw new java.io.IOException("HTTP " + status);
            }
            break;
        }
        if (connection == null) throw new java.io.IOException("Too many redirects");
        try (InputStream input = connection.getInputStream();
             OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
            if (output == null) throw new java.io.IOException("Cannot open destination");
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
        } finally {
            connection.disconnect();
        }
    }

    private void openExternal(Uri uri) {
        Toast.makeText(this, "本机模式已阻止外部链接", Toast.LENGTH_SHORT).show();
    }

    private boolean isActiveCloudCLIUrl(Uri uri) {
        return activeProfile != null
                && DeviceProfileStore.sameOrigin(uri, Uri.parse(activeProfile.baseUrl));
    }

    private static WebResourceResponse blockedWebResource() {
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                403,
                "Blocked by CloudCLI local-only policy",
                Collections.<String, String>emptyMap(),
                new ByteArrayInputStream(new byte[0]));
    }

    private void cancelFileChooser() {
        if (filePathCallback != null) filePathCallback.onReceiveValue(null);
        filePathCallback = null;
        capturedPhotoUri = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK) {
                if (data == null && capturedPhotoUri != null) {
                    result = new Uri[]{capturedPhotoUri};
                } else {
                    result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                    persistReadPermissions(data);
                }
            }
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
            capturedPhotoUri = null;
        } else if (requestCode == SAVE_FILE_REQUEST) {
            PendingDownload download = pendingDownload;
            pendingDownload = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && download != null) {
                saveDownload(download, data.getData());
            }
        }
    }

    private void persistReadPermissions(Intent data) {
        if (data == null) return;
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try {
            if (data.getData() != null) {
                getContentResolver().takePersistableUriPermission(data.getData(), flags);
            }
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int index = 0; index < clip.getItemCount(); index++) {
                    Uri uri = clip.getItemAt(index).getUri();
                    if (uri != null) getContentResolver().takePersistableUriPermission(uri, flags);
                }
            }
        } catch (SecurityException ignored) {
            // Some document providers grant access only for the current activity result.
        }
    }

    private void pruneCaptureCache() {
        File directory = new File(getCacheDir(), "capture");
        File[] files = directory.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoff) file.delete();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != MICROPHONE_PERMISSION_REQUEST || pendingPermissionRequest == null) return;
        PermissionRequest request = pendingPermissionRequest;
        pendingPermissionRequest = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else {
            request.deny();
            Toast.makeText(this, "需要麦克风权限才能使用语音输入", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            if (serviceUnavailable) {
                showConnectingState();
                webView.loadUrl(deviceStore.getLastUrl(activeProfile));
            }
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        String currentUrl = webView == null ? null : webView.getUrl();
        if (currentUrl != null && isActiveCloudCLIUrl(Uri.parse(currentUrl))) {
            deviceStore.saveLastUrl(activeProfile, currentUrl);
            outState.putString(STATE_URL, currentUrl);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT < 33) handleBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        cancelFileChooser();
        if (pendingPermissionRequest != null) {
            pendingPermissionRequest.deny();
            pendingPermissionRequest = null;
        }
        downloadExecutor.shutdownNow();
        if (webView != null) {
            webView.stopLoading();
            webView.setDownloadListener(null);
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams wrapContent() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams fullWidthButton() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static final class PendingDownload {
        final String url;
        final String userAgent;
        final String mimeType;
        final String fileName;

        PendingDownload(String url, String userAgent, String mimeType, String fileName) {
            this.url = url;
            this.userAgent = userAgent;
            this.mimeType = mimeType;
            this.fileName = fileName;
        }
    }
}
