package com.usetoolkit.iphoto;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * Render the selected LUT on the exact dimensions of a CameraX high-res JPEG.
 * Decodes a single mutable bitmap and processes 64-row strips (no second full
 * bitmap). Publishes via scoped MediaStore IS_PENDING transaction.
 *
 * For memory limits/OOM/EXIF failures, the ORIGINAL JPEG is left untouched.
 * Extreme 200 MP streams need a native streaming JPEG encoder; this Java
 * backend never silently downsamples or labels an unfiltered JPG as filtered.
 */
public final class FullResolutionPhotoProcessor {
    private static final String TAG="IPhotoJpeg";
    private FullResolutionPhotoProcessor(){}

    public static final class Result {
        public final Uri uri;
        public final boolean applied;
        public final int width,height;
        public final String message;
        Result(Uri uri,boolean applied,int w,int h,String message){
            this.uri=uri;this.applied=applied;this.width=w;this.height=h;
            this.message=message;
        }
    }

    public static Result apply(Context context,Uri original,
            ColorLooks.Look look,ColorLooks.Scene scene,float strength){
        if(original==null)return new Result(null,false,0,0,"저장 주소 없음");
        ContentResolver resolver=context.getContentResolver();
        Uri published=null;
        Bitmap image=null;
        int width=0,height=0;
        try{
            BitmapFactory.Options bounds=new BitmapFactory.Options();
            bounds.inJustDecodeBounds=true;
            try(InputStream stream=resolver.openInputStream(original)){
                if(stream==null)throw new IOException("Image open failed");
                BitmapFactory.decodeStream(stream,null,bounds);
            }
            width=bounds.outWidth;height=bounds.outHeight;
            if(width<=0||height<=0)throw new IOException("Invalid JPEG resolution");

            // A true 50MP bitmap costs ~200MB in ARGB_8888. Keep headroom for
            // active CameraX + GPU and the JPEG compressor; NEVER downsample.
            long bitmapBytes=(long)width*height*4L;
            long maxHeap=Runtime.getRuntime().maxMemory();
            long budget=Math.max(40L*1024*1024,
                (long)((maxHeap-64L*1024*1024)*.72d));
            if(bitmapBytes+((long)width*64L*4L)>budget){
                return new Result(original,false,width,height,
                    "사진 "+PhotoResolutionInfo.megapixelLabel(width,height)+
                    " 원본 저장 · 기기 메모리 한도로 필터 적용 생략");
            }

            // Snapshot the chosen look at shutter press; concurrent UI changes
            // cannot change the color grading of an already captured photo.
            PhotoLut lut=new PhotoLut(look,scene,strength);
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            options.inMutable=true;
            options.inSampleSize=1;
            try(InputStream stream=resolver.openInputStream(original)){
                if(stream==null)throw new IOException("Image decode failed");
                image=BitmapFactory.decodeStream(stream,null,options);
            }
            if(image==null || image.getWidth()!=width || image.getHeight()!=height ||
                !image.isMutable())
                throw new IOException("Cannot decode an unscaled mutable JPEG");

            final int stripHeight=64;
            int[] pixels=new int[width*Math.min(height,stripHeight)];
            for(int row=0;row<height;row+=stripHeight){
                int rows=Math.min(stripHeight,height-row);
                image.getPixels(pixels,0,width,0,row,width,rows);
                int len=width*rows;
                for(int i=0;i<len;i++)pixels[i]=lut.transform(pixels[i]);
                image.setPixels(pixels,0,width,0,row,width,rows);
            }

            ContentValues values=new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME,
                "Iphoto_Filter_"+System.currentTimeMillis()+".jpg");
            values.put(MediaStore.MediaColumns.MIME_TYPE,"image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES+"/I photo");
            values.put(MediaStore.MediaColumns.IS_PENDING,1);
            published=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(published==null)throw new IOException("Unable to reserve filtered JPG");
            try(OutputStream stream=resolver.openOutputStream(published,"w")){
                if(stream==null || !image.compress(Bitmap.CompressFormat.JPEG,97,stream))
                    throw new IOException("JPEG encode failed");
                stream.flush();
            }
            image.recycle();image=null;

            // Preserve orientation and critical shooting metadata. The
            // compressed pixel array is deliberately NOT auto-rotated.
            copyExif(resolver,original,published);

            BitmapFactory.Options verify=new BitmapFactory.Options();
            verify.inJustDecodeBounds=true;
            try(InputStream stream=resolver.openInputStream(published)){
                if(stream==null)throw new IOException("Cannot verify JPEG");
                BitmapFactory.decodeStream(stream,null,verify);
            }
            if(verify.outWidth!=width || verify.outHeight!=height)
                throw new IOException("Output pixel dimensions changed");

            ContentValues complete=new ContentValues();
            complete.put(MediaStore.MediaColumns.IS_PENDING,0);
            if(resolver.update(published,complete,null,null)!=1)
                throw new IOException("Could not publish filtered image");

            Uri filtered=published;
            published=null;
            boolean originalDeleted=false;
            try {originalDeleted=resolver.delete(original,null,null)>0;}
            catch(Exception ignored){Log.w(TAG,"Original JPEG retained alongside filtered");}
            return new Result(filtered,true,width,height,
                "색감 저장 완료 · "+PhotoResolutionInfo.describe(width,height)+
                (originalDeleted?"":" · 원본도 보관"));
        }catch(OutOfMemoryError mem){
            Log.e(TAG,"Not enough memory for full-resolution JPEG LUT",mem);
            return new Result(original,false,width,height,
                "원본 저장 · 고화소 색감 처리 중 메모리 부족");
        }catch(Exception failure){
            Log.e(TAG,"High-resolution photo grading failed",failure);
            return new Result(original,false,width,height,
                "원본 안전 보존 · 색감 처리 실패 ("+
                    failure.getClass().getSimpleName()+")");
        }finally{
            if(image!=null && !image.isRecycled())image.recycle();
            if(published!=null){
                try {resolver.delete(published,null,null);}
                catch(Exception ignored){Log.w(TAG,"Pending failed JPEG cleanup failed");}
            }
        }
    }

    static void copyExif(ContentResolver resolver,Uri source,Uri target)
            throws IOException {
        ExifInterface input;
        try(InputStream stream=resolver.openInputStream(source)){
            if(stream==null)throw new IOException("Original EXIF unavailable");
            input=new ExifInterface(stream);
        }
        // Output fd is owned by this app via MediaStore and must be seekable.
        try(ParcelFileDescriptor fd=resolver.openFileDescriptor(target,"rw")){
            if(fd==null)throw new IOException("Destination EXIF FD unavailable");
            ExifInterface output=new ExifInterface(fd.getFileDescriptor());
            String[] keep={
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_ISO_SPEED_RATINGS,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_EXPOSURE_BIAS_VALUE
            };
            for(String tag:keep){
                String value=input.getAttribute(tag);
                if(value!=null)output.setAttribute(tag,value);
            }
            output.saveAttributes();
        }
    }
}
