package com.usetoolkit.iphoto;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Full-pixel Camera2 JPEG processing: center crop to the requested viewfinder ratio,
 * then apply the selected LUT without reducing the target pixel dimensions.
 * A failure preserves the untouched source JPEG rather than creating fake resolution.
 */
public final class UltraPhotoProcessor {
    private static final String TAG = "IPhotoUltraPost";
    private UltraPhotoProcessor() {}

    public static FullResolutionPhotoProcessor.Result apply(Context context, Uri source,
            String frameRatio, ColorLooks.Look look, ColorLooks.Scene scene, float intensity) {
        ContentResolver resolver = context.getContentResolver();
        Uri result = null;
        Bitmap bitmap = null;
        BitmapRegionDecoder decoder = null;
        int w = 0, h = 0;
        try {
            BitmapFactory.Options dimensions = new BitmapFactory.Options();
            dimensions.inJustDecodeBounds = true;
            try (InputStream input = resolver.openInputStream(source)) {
                if (input == null) throw new java.io.IOException("Source JPEG missing");
                BitmapFactory.decodeStream(input, null, dimensions);
            }
            int originalWidth = dimensions.outWidth, originalHeight = dimensions.outHeight;
            if (originalWidth < 1 || originalHeight < 1)
                throw new java.io.IOException("Source JPEG dimensions missing");

            Rect crop = centerCrop(originalWidth, originalHeight, frameRatio);
            w = crop.width(); h = crop.height();
            boolean cropNeeded = w != originalWidth || h != originalHeight;
            boolean filterNeeded = look != ColorLooks.Look.ORIGINAL && intensity > 0f;
            if (!cropNeeded && !filterNeeded)
                return new FullResolutionPhotoProcessor.Result(source, false, w, h,
                    "고화소 원본 저장 · " + PhotoResolutionInfo.describe(w, h));

            long bytes = (long) w * h * 4L;
            long heap = Runtime.getRuntime().maxMemory();
            long budget = Math.max(40L * 1024 * 1024,
                (long) ((heap - 64L * 1024 * 1024) * .72d));
            if (bytes + (long) w * 64 * 4 > budget)
                return new FullResolutionPhotoProcessor.Result(source, false,
                    originalWidth, originalHeight,
                    "원본 " + PhotoResolutionInfo.megapixelLabel(originalWidth, originalHeight)
                    + " 보존 · 메모리 한도로 비율 크롭/필터 생략");

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inMutable = true;
            options.inSampleSize = 1;
            try (InputStream input = resolver.openInputStream(source)) {
                if (input == null) throw new java.io.IOException("Source JPEG missing");
                decoder = BitmapRegionDecoder.newInstance(input, false);
                if (decoder == null) throw new java.io.IOException("Cannot decode JPEG region");
                bitmap = decoder.decodeRegion(crop, options);
            }
            if (bitmap == null || bitmap.getWidth() != w || bitmap.getHeight() != h
                    || !bitmap.isMutable())
                throw new java.io.IOException("Lossless-size crop unavailable");
            if (filterNeeded) {
                PhotoLut lut = new PhotoLut(look, scene, intensity);
                int rowsPerBatch = 64;
                int[] pixels = new int[w * Math.min(h, rowsPerBatch)];
                for (int top = 0; top < h; top += rowsPerBatch) {
                    int count = Math.min(rowsPerBatch, h - top);
                    bitmap.getPixels(pixels, 0, w, 0, top, w, count);
                    for (int i = 0; i < w * count; i++)
                        pixels[i] = lut.transform(pixels[i]);
                    bitmap.setPixels(pixels, 0, w, 0, top, w, count);
                }
            }

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME,
                "Iphoto_" + frameRatio.replace(':','_') + "_"
                    + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/I photo");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            result = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (result == null) throw new java.io.IOException("Output URI unavailable");
            try (OutputStream output = resolver.openOutputStream(result, "w")) {
                if (output == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 97, output))
                    throw new java.io.IOException("JPEG output failed");
                output.flush();
            }
            bitmap.recycle(); bitmap = null;
            // Camera2 JPEG orientation and shooting metadata must be preserved after crop.
            try { FullResolutionPhotoProcessor.copyExif(resolver, source, result); }
            catch (Exception exifError) { Log.w(TAG, "EXIF copy unavailable", exifError); }

            BitmapFactory.Options check = new BitmapFactory.Options();
            check.inJustDecodeBounds = true;
            try (InputStream input = resolver.openInputStream(result)) {
                BitmapFactory.decodeStream(input, null, check);
            }
            if (check.outWidth != w || check.outHeight != h)
                throw new java.io.IOException("Crop output dimensions changed");
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(result, ready, null, null) != 1)
                throw new java.io.IOException("Output publish failed");
            Uri finalUri = result;
            result = null;
            boolean deleted = false;
            try { deleted = resolver.delete(source, null, null) > 0; }
            catch (Exception e) { Log.w(TAG, "Original retained", e); }
            return new FullResolutionPhotoProcessor.Result(finalUri, filterNeeded,
                w, h, "고화소 촬영 저장 · " + PhotoResolutionInfo.describe(w, h)
                    + (filterNeeded ? " · 색감 적용" : "")
                    + (deleted ? "" : " · 원본도 보관"));
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "Insufficient RAM for full-size crop", oom);
            return new FullResolutionPhotoProcessor.Result(source, false,
                w, h, "원본 보존 · 고화소 색감/크롭 메모리 부족");
        } catch (Exception failure) {
            Log.e(TAG, "Ultra crop/filter failed", failure);
            return new FullResolutionPhotoProcessor.Result(source, false,
                w, h, "원본 보존 · 고화소 크롭/색감 실패: "
                    + failure.getClass().getSimpleName());
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (decoder != null) decoder.recycle();
            if (result != null) {
                try { resolver.delete(result, null, null); }
                catch (Exception ignored) {}
            }
        }
    }

    /** Always crop relative to the JPEG's actual pixel orientation. */
    static Rect centerCrop(int width, int height, String ratio) {
        double shortOverLong;
        if ("9:16".equals(ratio)) shortOverLong = 9d / 16d;
        else if ("1:1".equals(ratio)) shortOverLong = 1d;
        else if ("3:2".equals(ratio)) shortOverLong = 2d / 3d;
        else shortOverLong = 3d / 4d;

        double desired = width > height ? 1d / shortOverLong : shortOverLong;
        int w = width, h = height;
        double current = (double) width / height;
        if (current > desired + 0.0001d)
            w = Math.max(1, (int) Math.round(height * desired));
        else if (current < desired - 0.0001d)
            h = Math.max(1, (int) Math.round(width / desired));
        return new Rect((width - w) / 2, (height - h) / 2,
            (width - w) / 2 + w, (height - h) / 2 + h);
    }
}
