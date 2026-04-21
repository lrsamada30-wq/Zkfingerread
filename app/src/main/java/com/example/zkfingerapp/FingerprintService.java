package com.example.zkfingerapp;

import android.content.Context;
import android.util.Log;
import com.zkteco.android.biometric.core.device.ParameterHelper;
import com.zkteco.android.biometric.core.device.TransportType;
import com.zkteco.android.biometric.module.fingerprintreader.*;
import com.zkteco.android.biometric.module.fingerprintreader.exception.FingerprintException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.zkfingerapp.db.*;

public class FingerprintService {
    private static final String TAG = "FingerprintService";
    private static final int MATCH_THRESHOLD = 55;
    private static final int TEMPLATE_SIZE = 2048;
    
    private FingerprintSensor fingerprintSensor;
    private Context context;
    private AppDatabase database;
    private Map<String, FingerprintCaptureListener> listeners;
    private boolean isCapturing = false;
    
    public FingerprintService(Context context) {
        this.context = context;
        this.database = AppDatabase.getInstance(context);
        this.listeners = new HashMap<>();
        initSensor();
    }
    
    private void initSensor() {
        try {
            ParameterHelper parameterHelper = new ParameterHelper();
            parameterHelper.putInt(ParameterHelper.KEY_VID, 6997);
            parameterHelper.putInt(ParameterHelper.KEY_PID, 288);
            
            fingerprintSensor = FingerprintFactory.createFingerprintSensor(
                context, TransportType.USB, parameterHelper);
            
            Log.d(TAG, "Sensor inicializado correctamente");
        } catch (Exception e) {
            Log.e(TAG, "Error inicializando sensor: " + e.getMessage());
        }
    }
    
    // Cargar todas las huellas de la BD a la caché del SDK
    public void loadAllFingerprintsToCache() {
        new Thread(() -> {
            try {
                // Limpiar caché actual
                ZKFingerService.clear();
                
                List<FingerprintEntity> fingerprints = database.fingerprintDao().getAll();
                Log.d(TAG, "Cargando " + fingerprints.size() + " huellas a la caché");
                
                for (FingerprintEntity fp : fingerprints) {
                    int result = ZKFingerService.save(fp.getTemplateData(), fp.getUserId());
                    if (result == 0) {
                        Log.d(TAG, "Huella cargada: " + fp.getUserId());
                    } else {
                        Log.e(TAG, "Error cargando huella " + fp.getUserId() + ": " + result);
                    }
                }
                
                int count = ZKFingerService.count();
                Log.d(TAG, "Total huellas en caché: " + count);
                
            } catch (Exception e) {
                Log.e(TAG, "Error cargando huellas: " + e.getMessage());
            }
        }).start();
    }
    
    // Registrar nueva huella
    public void registerFingerprint(String userId, final RegisterCallback callback) {
        if (isCapturing) {
            callback.onError("Ya está capturando una huella");
            return;
        }
        
        final byte[][] templates = new byte[3][TEMPLATE_SIZE];
        final int[] enrollIdx = {0};
        
        FingerprintCaptureListener listener = new FingerprintCaptureListener() {
            @Override
            public void captureOK(byte[] fpImage) {
                // Imagen capturada correctamente
            }
            
            @Override
            public void captureError(FingerprintException e) {
                isCapturing = false;
                stopCapture();
                callback.onError("Error captura: " + e.getMessage());
            }
            
            @Override
            public void extractOK(byte[] fpTemplate) {
                try {
                    // Verificar si la huella ya existe
                    byte[] bufids = new byte[256];
                    int ret = ZKFingerService.identify(fpTemplate, bufids, MATCH_THRESHOLD, 1);
                    if (ret > 0) {
                        String result = new String(bufids);
                        String[] parts = result.split("\t");
                        isCapturing = false;
                        stopCapture();
                        callback.onError("Esta huella ya está registrada con ID: " + parts[0]);
                        return;
                    }
                    
                    // Verificar consistencia con huellas anteriores
                    if (enrollIdx[0] > 0) {
                        int score = ZKFingerService.verify(templates[enrollIdx[0] - 1], fpTemplate);
                        if (score <= 0) {
                            callback.onError("Por favor presione el mismo dedo 3 veces");
                            return;
                        }
                    }
                    
                    System.arraycopy(fpTemplate, 0, templates[enrollIdx[0]], 0, fpTemplate.length);
                    enrollIdx[0]++;
                    
                    if (enrollIdx[0] == 3) {
                        // Fusionar las 3 huellas
                        byte[] finalTemplate = new byte[TEMPLATE_SIZE];
                        int retMerge = ZKFingerService.merge(templates[0], templates[1], 
                                                              templates[2], finalTemplate);
                        
                        if (retMerge > 0) {
                            // Guardar en BD y caché
                            saveFingerprintToDatabase(userId, finalTemplate);
                            callback.onSuccess();
                        } else {
                            callback.onError("Error al fusionar huellas");
                        }
                        isCapturing = false;
                        stopCapture();
                    } else {
                        callback.onProgress(3 - enrollIdx[0]);
                    }
                    
                } catch (Exception e) {
                    isCapturing = false;
                    stopCapture();
                    callback.onError("Error: " + e.getMessage());
                }
            }
            
            @Override
            public void extractError(int errno) {
                isCapturing = false;
                stopCapture();
                callback.onError("Error extrayendo template: " + errno);
            }
        };
        
        try {
            fingerprintSensor.setFingerprintCaptureListener(0, listener);
            fingerprintSensor.startCapture(0);
            isCapturing = true;
            callback.onProgress(3);
        } catch (Exception e) {
            callback.onError("Error iniciando captura: " + e.getMessage());
        }
    }
    
    // Verificar identidad
    public void verifyFingerprint(final VerifyCallback callback) {
        if (isCapturing) {
            callback.onError("Ya está capturando una huella");
            return;
        }
        
        FingerprintCaptureListener listener = new FingerprintCaptureListener() {
            @Override
            public void captureOK(byte[] fpImage) {}
            
            @Override
            public void captureError(FingerprintException e) {
                isCapturing = false;
                stopCapture();
                callback.onError("Error captura: " + e.getMessage());
            }
            
            @Override
            public void extractOK(byte[] fpTemplate) {
                try {
                    byte[] bufids = new byte[256];
                    int ret = ZKFingerService.identify(fpTemplate, bufids, MATCH_THRESHOLD, 1);
                    
                    if (ret > 0) {
                        String result = new String(bufids);
                        String[] parts = result.split("\t");
                        String userId = parts[0];
                        int score = Integer.parseInt(parts[1]);
                        callback.onVerified(true, userId, score);
                    } else {
                        callback.onVerified(false, null, 0);
                    }
                    
                } catch (Exception e) {
                    callback.onError("Error verificando: " + e.getMessage());
                } finally {
                    isCapturing = false;
                    stopCapture();
                }
            }
            
            @Override
            public void extractError(int errno) {
                isCapturing = false;
                stopCapture();
                callback.onError("Error extrayendo template: " + errno);
            }
        };
        
        try {
            fingerprintSensor.setFingerprintCaptureListener(0, listener);
            fingerprintSensor.startCapture(0);
            isCapturing = true;
        } catch (Exception e) {
            callback.onError("Error iniciando captura: " + e.getMessage());
        }
    }
    
    private void saveFingerprintToDatabase(String userId, byte[] template) {
        new Thread(() -> {
            FingerprintEntity entity = new FingerprintEntity(userId, template, System.currentTimeMillis());
            long id = database.fingerprintDao().insert(entity);
            if (id > 0) {
                ZKFingerService.save(template, userId);
                Log.d(TAG, "Huella guardada exitosamente: " + userId);
            }
        }).start();
    }
    
    private void stopCapture() {
        try {
            if (fingerprintSensor != null) {
                fingerprintSensor.stopCapture(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deteniendo captura: " + e.getMessage());
        }
    }
    
    public void openSensor() {
        try {
            if (fingerprintSensor != null) {
                fingerprintSensor.open(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error abriendo sensor: " + e.getMessage());
        }
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
    
    public void destroy() {
        closeSensor();
        if (fingerprintSensor != null) {
            FingerprintSensor.destroy(fingerprintSensor);
        }
    }
    
    public int getFingerprintCount() {
        return ZKFingerService.count();
    }
    
    public void clearAllFingerprints() {
        new Thread(() -> {
            database.fingerprintDao().clearAll();
            ZKFingerService.clear();
        }).start();
    }
    
    public interface RegisterCallback {
        void onProgress(int remaining);
        void onSuccess();
        void onError(String error);
    }
    
    public interface VerifyCallback {
        void onVerified(boolean success, String userId, int score);
        void onError(String error);
    }
}
