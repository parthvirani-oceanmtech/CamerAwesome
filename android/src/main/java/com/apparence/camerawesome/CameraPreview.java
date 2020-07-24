package com.apparence.camerawesome;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import io.flutter.view.TextureRegistry;

import static com.apparence.camerawesome.CameraPictureStates.STATE_READY_AFTER_FOCUS;
import static com.apparence.camerawesome.CameraPictureStates.STATE_RELEASE_FOCUS;
import static com.apparence.camerawesome.CameraPictureStates.STATE_REQUEST_PHOTO_AFTER_FOCUS;
import static com.apparence.camerawesome.CameraPictureStates.STATE_WAITING_LOCK;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraPreview implements CameraSession.OnCaptureSession  {

    private static final String TAG = CameraPreview.class.getName();

    private final CameraSession mCameraSession;

    private TextureRegistry.SurfaceTextureEntry flutterTexture;

    private Size previewSize;

    private Handler mBackgroundHandler;

    private CaptureRequest.Builder mPreviewRequestBuilder;

    private CameraCaptureSession mCaptureSession;

    private CaptureRequest mPreviewRequest;

    private boolean autoFocus;

    private boolean flashMode;


    public CameraPreview(CameraSession cameraSession) {
        this.autoFocus = true;
        this.flashMode = false;
        this.mCameraSession = cameraSession;
    }

    public CameraPreview(CameraSession cameraSession, TextureRegistry.SurfaceTextureEntry flutterTexture) {
        this(cameraSession);
        this.flutterTexture = flutterTexture;
    }


    void createCameraPreviewSession(final CameraDevice cameraDevice) throws CameraAccessException {
        // create surface
        SurfaceTexture surfaceTexture = flutterTexture.surfaceTexture();
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface flutterSurface = new Surface(surfaceTexture);
        // create preview
        mPreviewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        initPreviewRequest();

        mPreviewRequestBuilder.addTarget(flutterSurface);
        mCameraSession.addSurface(flutterSurface);
        mCameraSession.createCameraCaptureSession(cameraDevice);
    }

    public void lockFocus() {
        mCameraSession.setState(STATE_WAITING_LOCK);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        refreshConfigurationWithCallback();
    }

    public void unlockFocus() {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        initPreviewRequest();
        refreshConfiguration();
        mCameraSession.setState(null);
    }
    
    public CameraCaptureSession getCaptureSession() {
        return mCaptureSession;
    }

    public void setFlutterTexture(TextureRegistry.SurfaceTextureEntry flutterTexture) {
        this.flutterTexture = flutterTexture;
    }

    public Long getFlutterTexture() {
        return this.flutterTexture.id();
    }

    public void dispose() {
        if(mCaptureSession != null) {
            mCaptureSession.close();
        }
    }

    public void setPreviewSize(int width, int height) {
        this.previewSize = new Size(width, height);
    }

    public void setAutoFocus(boolean autoFocus) {
        this.autoFocus = autoFocus;
        initPreviewRequest();
        refreshConfiguration();
    }

    // ------------------------------------------------------
    // PRIVATES
    // ------------------------------------------------------

    private void initPreviewRequest() {
        mPreviewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 270);
        mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, flashMode
                ? CaptureRequest.FLASH_MODE_TORCH
                : CaptureRequest.FLASH_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, this.autoFocus
                ? CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                : CaptureRequest.CONTROL_AF_MODE_OFF);

    }

    private void refreshConfiguration() {
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "refreshConfiguration", e);
        }
    }

    private void refreshConfigurationWithCallback() {
        try {
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "refreshConfigurationWithCallback", e);
        }
    }

    // --------------------------------------------------
    // CameraSession.OnCaptureSession
    // --------------------------------------------------

    @Override
    public void onConfigured(@NonNull CameraCaptureSession session) {
        mCaptureSession = session;
        refreshConfiguration();

    }

    @Override
    public void onConfigureFailed() {
        this.mCaptureSession = null;
    }

    @Override
    public void onStateChanged(CameraPictureStates state) {
        if(state !=null && state.equals(STATE_RELEASE_FOCUS)) {
            this.unlockFocus();
        }
    }

    // ------------------------------------------------------
    // ON FOCUS CALLBACK
    // ------------------------------------------------------

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            processCapture(result);
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
            processCapture(partialResult);
        }
    };

    private void processCapture(CaptureResult result) {
        if(mCameraSession.getState() == null) {
            Log.e(TAG, "processCapture: is null");
            return;
        }
        switch (mCameraSession.getState()) {
            case STATE_WAITING_LOCK:
                Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                if(afState == null) {
//                    mCameraSession.setState(STATE_READY_AFTER_FOCUS);
                } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState) {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                        mCameraSession.setState(STATE_READY_AFTER_FOCUS);
                    } else {
                        runPrecaptureSequence();
                    }
                } else if (CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {

                }
                break;
        }
    }

    private void runPrecaptureSequence() {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        refreshConfigurationWithCallback();
    }


    // ------------------------------------------------------
    // VISIBLE FOR TESTS
    // ------------------------------------------------------

    @VisibleForTesting
    public CaptureRequest.Builder getPreviewRequest() {
        return mPreviewRequestBuilder;
    }
}


