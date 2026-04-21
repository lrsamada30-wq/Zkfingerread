package com.example.zkfingerapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {
    
    private FingerprintService fingerprintService;
    private TextView tvStatus, tvCount;
    private ImageView imageView;
    private Button btnStart, btnStop, btnRegister, btnVerify, btnClear;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        initFingerprintService();
    }
    
    private void initViews() {
              
        tvStatus = findViewById(R.id.tvStatus);
        tvCount = findViewById(R.id.tvCount);
        imageView = findViewById(R.id.imageView);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnRegister = findViewById(R.id.btnRegister);
        btnVerify = findViewById(R.id.btnVerify);
        btnClear = findViewById(R.id.btnClear);
        
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> showInfoDialog());
        
        btnStart.setOnClickListener(v -> startCapture());
        btnStop.setOnClickListener(v -> stopCapture());
        btnRegister.setOnClickListener(v -> startRegistration());
        btnVerify.setOnClickListener(v -> startVerification());
        btnClear.setOnClickListener(v -> clearDatabase());
    }
    
    private void initFingerprintService() {
        fingerprintService = new FingerprintService(this);
        boolean opened = fingerprintService.openSensor();
        
        if (opened) {
            tvStatus.setText("Sensor abierto. Presione 'Iniciar Captura'");
        } else {
            tvStatus.setText("Error al abrir sensor. Verifique conexión USB");
        }
        
        // Cargar huellas existentes
        fingerprintService.loadAllFingerprintsToCache(new FingerprintService.LoadCallback() {
            @Override
            public void onLoaded(int loaded, int total) {
                runOnUiThread(() -> {
                    tvCount.setText("Huellas en caché: " + total);
                    tvStatus.setText("Listo. " + loaded + " huellas cargadas");
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> tvStatus.setText("Error carga: " + error));
            }
        });
    }
    
    private void startCapture() {
        boolean started = fingerprintService.startCapture();
        if (started) {
            tvStatus.setText("Capturando... Coloque su dedo");
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
        } else {
            Toast.makeText(this, "Error al iniciar captura", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void stopCapture() {
        fingerprintService.stopCapture();
        tvStatus.setText("Captura detenida");
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
    }
    
    private void startRegistration() {
        tvStatus.setText("Registrando - Presione el dedo 3 veces");
        
        fingerprintService.registerFingerprint(new FingerprintService.RegisterCallback() {
            @Override
            public void onProgress(int remaining) {
                runOnUiThread(() -> tvStatus.setText("Registro - Faltan " + remaining + " capturas"));
            }
            
            @Override
            public void onSuccess(String userId) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Huella registrada: " + userId, Toast.LENGTH_SHORT).show();
                    tvStatus.setText("Registro exitoso: " + userId);
                    int count = fingerprintService.getFingerprintCount();
                    tvCount.setText("Huellas en caché: " + count);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                    tvStatus.setText("Error: " + error);
                });
            }
        });
    }
    
    private void startVerification() {
        tvStatus.setText("Verificando - Presione el dedo");
        
        fingerprintService.verifyFingerprint(new FingerprintService.VerifyCallback() {
            @Override
            public void onVerified(boolean success, String userId, int score, long elapsedMicros) {
                runOnUiThread(() -> {
                    if (success) {
                        tvStatus.setText(String.format("✅ Verificado: %s (Score: %d, Tiempo: %.2f ms)",
                                userId, score, elapsedMicros / 1000.0));
                    } else {
                        tvStatus.setText(String.format("❌ No reconocido (Tiempo: %.2f ms)", 
                                elapsedMicros / 1000.0));
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    tvStatus.setText("Error: " + error);
                });
            }
        });
    }
    
    private void clearDatabase() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Confirmar")
            .setMessage("¿Eliminar todas las huellas?")
            .setPositiveButton("Sí", (d, w) -> {
                fingerprintService.clearAllFingerprints();
                tvStatus.setText("Base de datos limpiada");
                tvCount.setText("Huellas en caché: 0");
            })
            .setNegativeButton("No", null)
            .show();
    }
    
    private void showInfoDialog() {
        int count = fingerprintService.getFingerprintCount();
        new MaterialAlertDialogBuilder(this)
            .setTitle("Información")
            .setMessage("Huellas registradas: " + count + 
                       "\n\nVID: 6997, PID: 292\n" +
                       "Umbral de coincidencia: 55\n\n" +
                       "Las huellas se guardan persistentemente en Room Database")
            .setPositiveButton("OK", null)
            .show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fingerprintService != null) {
            fingerprintService.destroy();
        }
    }
}
