package com.example.zkfingerapp;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;
import com.zkteco.android.biometric.core.device.ParameterHelper;
import com.zkteco.android.biometric.core.device.TransportType;
import com.zkteco.android.biometric.core.utils.LogHelper;
import com.zkteco.android.biometric.module.fingerprintreader.*;
import com.zkteco.android.biometric.module.fingerprintreader.exception.FingerprintException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.zkfingerapp.db.*;

public class FingerprintService {
    private static final String TAG = "FingerprintService";
    private static final int VID = 6997;
    private static final int PID = 292;
    private static final int MATCH_THRESHOLD = 55;
    private static final int TEMPLATE_SIZE = 2048;
    
    private FingerprintSensor fingerprintSensor;
    private Context context;
    private AppDatabase database;
    private boolean isCapturing = false;
    private boolean isRegister = false;
    private byte[][] regTempArray = new byte[3][TEMPLATE_SIZE];
    private int enrollIdx = 0;
    
    public FingerprintService(Context context) {
        this.context = context;
        this.database = AppDatabase.getInstance(context);
        initSensor();
    }
    
    private void initSensor() {
        try {
            LogHelper.setLevel(Log.VERBOSE);
            Map<String, Object> fingerprintParams = new HashMap<>();
            fingerprintParams.put(ParameterHelper.PARAM_KEY_VID, VID);
            fingerprintParams.put(ParameterHelper.PARAM_KEY_PID, PID);
            fingerprintSensor = FingprintFactory.createFingerprintSensor(
                context, TransportType.USB, fingerprintParams);
            Log.d(TAG, "Sensor inicializado correctamente");
        } catch (Exception e) {
            Log.e(TAG, "Error inicializando sensor: " + e.getMessage());
        }
    }
    
    public boolean openSensor() {
        try {
            if (fingerprintSensor != null) {
                fingerprintSensor.open(0);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error abriendo sensor: " + e.getMessage());
        }
        return false;
    }
    
    public void closeSensor() {
        try {
            if (fingerprintSensor != null) {
                fingerprintSensor.close(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cerrando sensor: " + e.getMessage());
        }
    }
    
    public boolean startCapture() {
        try {
            if (fingerprintSensor != null && !isCapturing) {
                fingerprintSensor.startCapture(0);
                isCapturing = true;
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting capture: " + e.getMessage());
        }
        return false;
    }
    
    public void stopCapture() {
        try {
            if (fingerprintSensor != null && isCapturing) {
                fingerprintSensor.stopCapture(0);
                isCapturing = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping capture: " + e.getMessage());
        }
    }
    
    public void loadAllFingerprintsToCache(final LoadCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ZKFingerService.clear();
                    
                    List<FingerprintEntity> fingerprints = database.fingerprintDao().getAll();
                    Log.d(TAG, "Cargando " + fingerprints.size() + " huellas a la caché");
                    
                    int successCount = 0;
                    for (FingerprintEntity fp : fingerprints) {
                        int result = ZKFingerService.save(fp.getTemplateData(), fp.getUserId());
                        if (result == 0) {
                            successCount++;
                        }
                    }
                    
                    final int finalSuccessCount = successCount;
                    final int finalCount = ZKFingerService.count();
                    
                    if (callback != null) {
                        ((android.app.Activity) context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.onLoaded(finalSuccessCount, finalCount);
                            }
                        });
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error cargando huellas: " + e.getMessage());
                    if (callback != null) {
                        ((android.app.Activity) context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError(e.getMessage());
                            }
                        });
                    }
                }
            }
        }).start();
    }
    
    public void registerFingerprint(final RegisterCallback callback) {
        if (!isCapturing) {
            callback.onError("Inicie la captura primero");
            return;
        }
        
        isRegister = true;
        enrollIdx = 0;
        
        FingerprintCaptureListener listener = new FingerprintCaptureListener() {
            @Override
            public void captureOK(byte[] fpImage) {}
            
            @Override
            public void captureError(FingerprintException e) {
                callback.onError("Error captura: " + e.getMessage());
            }
            
            @Override
            public void extractError(int errno) {
                callback.onError("Error extrayendo template: " + errno);
            }
            
            @Override
            public void extractOK(byte[] fpTemplate) {
                byte[] bufids = new byte[256];
                int ret = ZKFingerService.identify(fpTemplate, bufids, MATCH_THRESHOLD, 1);
                if (ret > 0) {
                    String result = new String(bufids);
                    String[] parts = result.split("\t");
                    callback.onError("Huella ya registrada por: " + parts[0]);
                    isRegister = false;
                    enrollIdx = 0;
                    return;
                }
                
                if (enrollIdx > 0 && ZKFingerService.verify(regTempArray[enrollIdx - 1], fpTemplate) <= 0) {
                    callback.onError("Por favor presione el mismo dedo 3 veces");
                    return;
                }
                
                System.arraycopy(fpTemplate, 0, regTempArray[enrollIdx], 0, TEMPLATE_SIZE);
                enrollIdx++;
                
                if (enrollIdx == 3) {
                    byte[] finalTemplate = new byte[TEMPLATE_SIZE];
                    int retMerge = ZKFingerService.merge(regTempArray[0], regTempArray[1], 
                                                          regTempArray[2], finalTemplate);
                    if (retMerge > 0) {
                        String userId = "user_" + System.currentTimeMillis();
                        saveFingerprintToDatabase(userId, finalTemplate);
                        callback.onSuccess(userId);
                    } else {
                        callback.onError("Error al fusionar huellas: " + retMerge);
                    }
                    isRegister = false;
                    enrollIdx = 0;
                } else {
                    callback.onProgress(3 - enrollIdx);
                }
            }
        };
        
        fingerprintSensor.setFingerprintCaptureListener(0, listener);
        callback.onProgress(3);
    }
    
    public void verifyFingerprint(final VerifyCallback callback) {
        if (!isCapturing) {
            callback.onError("Inicie la captura primero");
            return;
        }
        
        isRegister = false;
        
        FingerprintCaptureListener listener = new FingerprintCaptureListener() {
            @Override
            public void captureOK(byte[] fpImage) {}
            
            @Override
            public void captureError(FingerprintException e) {
                callback.onError("Error captura: " + e.getMessage());
            }
            
            @Override
            public void extractError(int errno) {
                callback.onError("Error extrayendo template: " + errno);
            }
            
            @Override
            public void extractOK(byte[] fpTemplate) {
                byte[] bufids = new byte[256];
                long startTime = System.nanoTime();
                int ret = ZKFingerService.identify(fpTemplate, bufids, MATCH_THRESHOLD, 1);
                long elapsedMicros = (System.nanoTime() - startTime) / 1000;
                
                if (ret > 0) {
                    String result = new String(bufids);
                    String[] parts = result.split("\t");
                    String userId = parts[0];
                    int score = Integer.parseInt(parts[1]);
                    callback.onVerified(true, userId, score, elapsedMicros);
                } else {
                    callback.onVerified(false, null, 0, elapsedMicros);
                }
            }
        };
        
        fingerprintSensor.setFingerprintCaptureListener(0, listener);
    }
    
    private void saveFingerprintToDatabase(String userId, byte[] template) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FingerprintEntity entity = new FingerprintEntity(userId, template, System.currentTimeMillis());
                    long id = database.fingerprintDao().insert(entity);
                    if (id > 0) {
                        ZKFingerService.save(template, userId);
                        Log.d(TAG, "Huella guardada: " + userId);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error guardando: " + e.getMessage());
                }
            }
        }).start();
    }
    
    public int getFingerprintCount() {
        try {
            return ZKFingerService.count();
        } catch (Exception e) {
            return 0;
        }
    }
    
    public void clearAllFingerprints() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                database.fingerprintDao().clearAll();
                ZKFingerService.clear();
            }
        }).start();
    }
    
    public void destroy() {
        stopCapture();
        closeSensor();
    }
    
    public interface RegisterCallback {
        void onProgress(int remaining);
        void onSuccess(String userId);
        void onError(String error);
    }
    
    public interface VerifyCallback {
        void onVerified(boolean success, String userId, int score, long elapsedMicros);
        void onError(String error);
    }
    
    public interface LoadCallback {
        void onLoaded(int loaded, int total);
        void onError(String error);
    }
}
