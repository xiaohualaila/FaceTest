package xiahohu.facetest;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.faceplusplus.api.FaceDetecter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import kr.co.namee.permissiongen.PermissionFail;
import kr.co.namee.permissiongen.PermissionGen;
import kr.co.namee.permissiongen.PermissionSuccess;

public class MainActivity extends AppCompatActivity  implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private Camera camera;
    private String filePath;
    private SurfaceHolder holder;

    @Bind(R.id.camera_sf)
    SurfaceView camera_sf;
    @Bind(R.id.mask)
    FaceMask mask;
    @Bind(R.id.layout_wrap)
    FrameLayout layout_wrap;
    HandlerThread handleThread = null;
    Handler detectHandler = null;
    private int width = 320;
    private int height = 240;
    FaceDetecter facedetecter = null;
    private DisplayMetrics metrics;
    private boolean isFrontCamera = true;
    private boolean safeToTakePicture = true;//防止拍照重复调用
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);
        ButterKnife.bind(this);
        permiss();
        metrics = new DisplayMetrics();
        getWindow().getWindowManager().getDefaultDisplay().getMetrics(metrics);

        handleThread = new HandlerThread("dt");
        handleThread.start();
        detectHandler = new Handler(handleThread.getLooper());

        facedetecter = new FaceDetecter();
        if (!facedetecter.init(this, "d8ff9c739fc1b9127ea1549b7147c454")) {
            Log.e("diff", "有错误 ");
        }
        facedetecter.setTrackingMode(true);
    }

    @OnClick(R.id.tv_switch)
    public void tv_switch() {
        if (isFrontCamera) {
            isFrontCamera = false;
            stopPreview();
            closeCamera();
            openCamera();
            if (null != camera) {
                try {
                    camera.setPreviewDisplay(holder);
                } catch (IOException ex) {
                    closeCamera();
                    ex.printStackTrace();
                }
                startPreview();
            }
        } else {
            isFrontCamera = true;
            stopPreview();
            closeCamera();
            openCamera();
            if (null != camera) {
                try {
                    camera.setPreviewDisplay(holder);
                } catch (IOException ex) {
                    closeCamera();
                    ex.printStackTrace();
                }
                startPreview();
            }
        }
    }

    public long getTime() {
        return Calendar.getInstance().getTimeInMillis();
    }

    private Camera.PictureCallback jpeg = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Matrix matrix = new Matrix();
            matrix.reset();
            matrix.postRotate(270);
            filePath = FileUtil.getPath() + File.separator + getTime() + ".jpeg";
            Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
            Bitmap bm1 = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(),
                    bm.getHeight(), matrix, true);

            BufferedOutputStream bos = null;
            try {
                bos = new BufferedOutputStream(new FileOutputStream(new File(filePath)));
                bm1.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                bos.flush();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (bos != null) {
                    try {
                        bos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                bm.recycle();
                bm1.recycle();
              //  uploadFace(filePath);
                Log.i("sssss",filePath);
                safeToTakePicture = true;
                try {
                    camera.startPreview();
                } catch (RuntimeException e) {
                    Log.e("error", "========>" + e.toString());
                }

            }
        }
    };


    private void permiss(){
        PermissionGen.needPermission(this, 200, new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE
        });
    }


    @PermissionSuccess(requestCode = 200)
    public void toLocation() {


    }

    //
    @PermissionFail(requestCode = 200)
    public void toLocationFail() {
        Toast.makeText(this, "请打开相机权限！", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPreviewFrame(final byte[] data, final Camera mCamera) {
        detectHandler.post(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void run() {
                byte[] ori = new byte[width * height];
                int is = 0;
                if (!isFrontCamera) {
                    for (int x = width - 1; x >= 0; x--) {
                        for (int y = height - 1; y >= 0; y--) {
                            ori[is] = data[y * width + x];
                            is++;
                        }
                    }
                } else {
                    ori = rotateYUV420Degree90(data, width, height);
                }

                final FaceDetecter.Face[] faceinfo = facedetecter.findFaces(ori, height, width);//发现人脸
                if (faceinfo != null) {
                    if (safeToTakePicture) {
                        camera.takePicture(null, null, jpeg);
                        safeToTakePicture = false;
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mask.setFaceInfo(faceinfo);
                    }
                });
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        openCamera();
        try {
            camera.setPreviewDisplay(holder);
        } catch (IOException exception) {
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreview();
        closeCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        holder = camera_sf.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        facedetecter.release(this);
        handleThread.quit();
    }

    private void openCamera() {
        if (camera == null) {
            camera = Camera.open(isFrontCamera ? 0 : 1);
        }
    }

    private void startPreview() {
        Camera.Parameters para;
        if (null != camera) {
            para = camera.getParameters();
        } else {
            return;
        }
        para.setPreviewSize(width, height);
        setPictureSize(para, 1080, 1920);
        setCameraDisplayOrientation(isFrontCamera ? 0 : 1, camera);
        camera.setParameters(para);
        camera.startPreview();
        camera.setPreviewCallback(this);
    }

    private void setPictureSize(Camera.Parameters para, int width, int height) {
        int absWidth = 0;
        int absHeight = 0;
        List<Camera.Size> supportedPictureSizes = para.getSupportedPictureSizes();
        for (Camera.Size size : supportedPictureSizes) {
            if (Math.abs(width - size.width) < Math.abs(width - absWidth)) {
                absWidth = size.width;
            }
            if (Math.abs(height - size.height) < Math.abs(height - absHeight)) {
                absHeight = size.height;
            }
        }
    }

    public void setCameraDisplayOrientation(int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = MainActivity.this.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    private void stopPreview() {
        if (null != camera) {
            camera.stopPreview();
        }
    }

    private void closeCamera() {
        if (null != camera) {
            try {
                camera.setPreviewDisplay(null);
                camera.setPreviewCallback(null);
                camera.release();
                camera = null;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }


    private byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // 旋转Y
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }


        }
        // 旋转U和V
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth)
                        + (x - 1)];
                i--;
            }
        }
        return yuv;
    }
}
