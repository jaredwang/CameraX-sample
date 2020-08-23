package com.bs.cameraxdemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private int REQUEST_CODE_PERMISSIONS = 10;
    private static final String FILENAME_FROMAT = "yyyy-MMdd-HH-mm-ss";
    private ExecutorService cameraExcuter;
    private PreviewView viewFinder;
    private Camera camera;
    private CameraControl cameraControl;
    private ImageCapture imageCapture;
    private File outputFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (allPermissionsGranted()) {
            startCamera();

        } else {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_LONG);
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        cameraExcuter = Executors.newSingleThreadExecutor();
        viewFinder = findViewById(R.id.viewFinder);

        findViewById(R.id.camera_capture_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });
    }


    /**
     * take pic to file or memory
     */
    private void takePhoto() {
        if (imageCapture == null) {
            return;
        }

//            takePicToFile();

        takePickToMemory();

    }

    /**
     * take pic to memory
     */
    private void takePickToMemory() {

        //Take Picture to memory
        imageCapture.takePicture(ContextCompat.getMainExecutor(MainActivity.this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {

                //You need to manually manipulate the direction of the picture
                byte[] encodedJpeg = byteBufferToByteArray(image.getPlanes()[0].getBuffer());
                Bitmap rgb = BitmapFactory.decodeByteArray(encodedJpeg, 0, encodedJpeg.length);
                ImageView imageView = findViewById(R.id.iv_preview);
                imageView.setImageBitmap(rgb);
                imageView.bringToFront();
                super.onCaptureSuccess(image);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                super.onError(exception);
            }
        });
    }

    /**
     * take pic to file
     */
    private void takePicToFile() {
        //Create file
        outputFile = getOutputDir(MainActivity.this);
        File photoFile = new File(outputFile, (new SimpleDateFormat(FILENAME_FROMAT, Locale.CHINA)).format(System.currentTimeMillis()) + ".jpg");
        ImageCapture.OutputFileOptions OutputFileOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(OutputFileOptions, ContextCompat.getMainExecutor(MainActivity.this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {

                Uri uri = Uri.fromFile(photoFile);
                Log.e("RRRRRR", "onImageSaved PATH = " + uri.getPath());
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("RRRRRR", "ImageSave onError " + exception.toString());
            }
        });

    }

    /**
     * setup Camera
     */
    private void startCamera() {

        ListenableFuture cameraProviderFutrue = ProcessCameraProvider.getInstance(this);


        cameraProviderFutrue.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    //bind lifecycle owner
                    ProcessCameraProvider cameraProvider = (ProcessCameraProvider) cameraProviderFutrue.get();

                    //preview
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(viewFinder.createSurfaceProvider());

                    //build ImageCapture you can set FlashMode here or change FlashMode later
                    imageCapture = new ImageCapture.Builder()
                            .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                            .build();

                    //use back camera
                    CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                    OrientationEventListener orientationEventListener = new OrientationEventListener(MainActivity.this) {
                        @Override
                        public void onOrientationChanged(int orientation) {
                            int rotation;

                            // Monitors orientation values to determine the target rotation value
                            if (orientation >= 45 && orientation < 135) {
                                rotation = Surface.ROTATION_270;
                            } else if (orientation >= 135 && orientation < 225) {
                                rotation = Surface.ROTATION_180;
                            } else if (orientation >= 225 && orientation < 315) {
                                rotation = Surface.ROTATION_90;
                            } else {
                                rotation = Surface.ROTATION_0;
                            }

                            imageCapture.setTargetRotation(rotation);
                        }
                    };

                    orientationEventListener.enable();

                    //unbindAll before use
                    cameraProvider.unbindAll();
                    //bind Camera
                    camera = cameraProvider.bindToLifecycle((LifecycleOwner) MainActivity.this,
                            cameraSelector,
                            preview, imageCapture);

                    //get CameraContol,
                    cameraControl = camera.getCameraControl();

                    //tap to focus
                    viewFinder.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            if (event.equals(MotionEvent.ACTION_DOWN)) {
                                //设置对焦
                                MeteringPointFactory meteringPointFactory = new SurfaceOrientedMeteringPointFactory(1.0f, 1.0f);
                                MeteringPoint point = meteringPointFactory.createPoint(event.getX(), event.getY());
                                FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                        .setAutoCancelDuration(5, TimeUnit.SECONDS)
                                        .build();
                                cameraControl.startFocusAndMetering(action);
                                Log.e("RRRRRR", "onTouch");
                                return true;
                            }

                            return false;
                        }
                    });


                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(MainActivity.this));

    }

    //Image Output Path
    public static File getOutputDir(Context context) {
        Context applicationContext = context.getApplicationContext();
        File mediaDir = context.getExternalMediaDirs()[0];
        File output = null;
        if (mediaDir != null) {
            output = new File(mediaDir, context.getString(R.string.app_name));
            output.mkdir();
        }

        if (output != null && output.exists()) {
            return output;
        } else {
            return applicationContext.getFilesDir();
        }

    }

    private boolean allPermissionsGranted() {
        boolean status = ContextCompat.checkSelfPermission(MainActivity.this, REQUIRED_PERMISSIONS[0]) == PackageManager.PERMISSION_GRANTED;
        return status;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_LONG);
                finish();
            }
        }
    }


    //image Analyzer
    private static final class LuminosityAnalyzer implements ImageAnalysis.Analyzer {

        private byte[] toByteArray(ByteBuffer buffer) {
            buffer.rewind();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            return data;
        }

        @Override
        public void analyze(@NonNull ImageProxy image) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] data = toByteArray(buffer);
        }
    }

    public static byte[] byteBufferToByteArray(ByteBuffer byteBuffer) {
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return bytes;


    }
}