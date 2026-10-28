package com.reportcalendar.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Device-only avatar selection, keyed by UID; never sends a content URI to Firebase. */
final class ProfilePhotos {
    private static final ExecutorService images = Executors.newSingleThreadExecutor();
    static String get(Context context, String uid) {
        return context.getSharedPreferences("account_avatars", Context.MODE_PRIVATE).getString(uid, "");
    }
    static boolean save(Context context, String uid, String uri) {
        return context.getSharedPreferences("account_avatars", Context.MODE_PRIVATE).edit().putString(uid, uri).commit();
    }
    static void show(Context context, ImageView view, String value) {
        view.setImageResource(R.drawable.ic_person);
        view.setTag(value);
        if (value.isEmpty()) return;
        Context app = context.getApplicationContext();
        WeakReference<ImageView> target = new WeakReference<>(view);
        images.execute(() -> {
            Bitmap bitmap = null;
            try {
                Uri uri = Uri.parse(value);
                if (!"content".equals(uri.getScheme())) return;
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
                try (InputStream in = app.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, options); }
                if (options.outWidth <= 0 || options.outHeight <= 0) return;
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                try (InputStream in = app.getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(in, null, options); }
            } catch (Exception ignored) { /* Removed files / revoked grants use the default avatar. */ }
            Bitmap ready = bitmap;
            new Handler(Looper.getMainLooper()).post(() -> {
                ImageView current = target.get();
                if (current != null && value.equals(current.getTag()) && ready != null) current.setImageBitmap(ready);
            });
        });
    }
}
