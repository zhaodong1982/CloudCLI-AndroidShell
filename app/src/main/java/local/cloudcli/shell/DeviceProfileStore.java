package local.cloudcli.shell;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/** Fixed local-origin state for the CloudCLI service on this phone. */
final class DeviceProfileStore {
    static final String LOCAL_ID = "local";
    static final String LOCAL_URL = "http://127.0.0.1:3001/";

    private static final String PREFERENCES = "cloudcli_local_shell";
    private static final String KEY_LAST_URL = "last_url";

    private final SharedPreferences preferences;
    private final Profile localProfile = new Profile(LOCAL_ID, "本机", LOCAL_URL);

    DeviceProfileStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    List<Profile> getProfiles() {
        ArrayList<Profile> profiles = new ArrayList<Profile>();
        profiles.add(localProfile);
        return profiles;
    }

    Profile getActiveProfile() {
        return localProfile;
    }

    Profile create(String name, String rawUrl) {
        throw new IllegalArgumentException("CloudCLI Shell 仅支持本机服务");
    }

    Profile update(Profile existing, String name, String rawUrl) {
        throw new IllegalArgumentException("CloudCLI Shell 的本机地址不可修改");
    }

    void delete(Profile profile) {
        // The only profile is a fixed local security boundary.
    }

    void setActive(Profile profile) {
        if (profile == null || !LOCAL_ID.equals(profile.id)) {
            throw new IllegalArgumentException("CloudCLI Shell 仅支持本机服务");
        }
    }

    void saveLastUrl(Profile profile, String url) {
        if (!isRestorableUrl(profile, url)) return;
        preferences.edit().putString(KEY_LAST_URL, url).apply();
    }

    String getLastUrl(Profile profile) {
        String saved = preferences.getString(KEY_LAST_URL, null);
        if (!isRestorableUrl(profile, saved)) {
            preferences.edit().remove(KEY_LAST_URL).apply();
            return LOCAL_URL;
        }
        return saved;
    }

    boolean isRestorableUrl(Profile profile, String url) {
        if (profile == null || url == null) return false;
        Uri candidate = Uri.parse(url);
        Uri base = Uri.parse(LOCAL_URL);
        return sameOrigin(candidate, base)
                && candidate.getQuery() == null
                && normalizeRoutePath(candidate.getPath()).equals(normalizeRoutePath(base.getPath()));
    }

    static String cleanName(String rawName) {
        return "本机";
    }

    static String normalizeBaseUrl(String rawUrl) {
        try {
            URI parsed = new URI(rawUrl == null ? "" : rawUrl.trim());
            URI expected = new URI(LOCAL_URL);
            if (!expected.getScheme().equalsIgnoreCase(parsed.getScheme())
                    || !expected.getHost().equalsIgnoreCase(parsed.getHost())
                    || effectivePort(parsed) != effectivePort(expected)
                    || parsed.getUserInfo() != null
                    || parsed.getQuery() != null
                    || parsed.getFragment() != null) {
                throw new IllegalArgumentException("CloudCLI Shell 仅允许 http://127.0.0.1:3001/");
            }
            return LOCAL_URL;
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("CloudCLI Shell 仅允许 http://127.0.0.1:3001/");
        }
    }

    static boolean sameOrigin(Uri left, Uri right) {
        if (left == null || right == null) return false;
        String leftScheme = left.getScheme();
        String rightScheme = right.getScheme();
        String leftHost = left.getHost();
        String rightHost = right.getHost();
        return leftScheme != null && rightScheme != null
                && leftHost != null && rightHost != null
                && leftScheme.equalsIgnoreCase(rightScheme)
                && leftHost.equalsIgnoreCase(rightHost)
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(Uri uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String normalizeRoutePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        int end = path.length();
        while (end > 1 && path.charAt(end - 1) == '/') end--;
        return path.substring(0, end);
    }

    static final class Profile {
        final String id;
        final String name;
        final String baseUrl;

        Profile(String id, String name, String baseUrl) {
            this.id = id;
            this.name = name;
            this.baseUrl = baseUrl;
        }

        boolean isLocal() {
            return true;
        }
    }
}
