package local.cloudcli.shell;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class LocalFileProvider extends ContentProvider {
    static final String AUTHORITY = "local.cloudcli.shell.files";

    static Uri uriForCapture(File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("capture")
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        requireCaptureFile(uri);
        return "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = requireCaptureFile(uri);
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add(file.getName());
            else if (OpenableColumns.SIZE.equals(column)) row.add(file.length());
            else row.add(null);
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = requireCaptureFile(uri);
        int flags;
        if (mode != null && mode.contains("w")) {
            flags = ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE
                    | ParcelFileDescriptor.MODE_WRITE_ONLY;
        } else {
            flags = ParcelFileDescriptor.MODE_READ_ONLY;
        }
        return ParcelFileDescriptor.open(file, flags);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Insert is not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return requireCaptureFile(uri).delete() ? 1 : 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Update is not supported");
    }

    private File requireCaptureFile(Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme())
                || !AUTHORITY.equals(uri.getAuthority())
                || uri.getPathSegments().size() != 2
                || !"capture".equals(uri.getPathSegments().get(0))) {
            throw new SecurityException("Invalid capture URI");
        }
        String name = uri.getPathSegments().get(1);
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new SecurityException("Invalid capture filename");
        }
        File directory = new File(getContext().getCacheDir(), "capture");
        return new File(directory, name);
    }
}
