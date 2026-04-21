package com.example.zkfingerapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {
    
    private FingerprintService fingerprintService;
    private EditText etUserId;
    private TextView tvStatus, tvCount;
    private Button btnRegister, btnVerify, btnLoadCache;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        initFingerprintService();
    }
    
    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        etUserId = findViewById(R.id.etUserId);
        tvStatus = findViewById(R.id.tvStatus);
        tvCount = findViewById(R.id.tvCount);
        btnRegister = findViewById(R.id.btnRegister);
        btnVerify = findViewById(R.id.btnVerify);
        btnLoadCache = findViewById(R.id.btnLoadCache);
        
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> showInfoDialog());
        
        btnRegister.setOnClickListener(v -> startRegistration());
        btnVerify.setOnClickListener(v -> startVerification());
        btnLoadCache.setOnClickListener(v -> loadCacheFromDatabase());
    }
    
    private void initFingerprintService() {
        fingerprintService = new FingerprintService(this);
        fingerprintService.openSensor();
        
        // Cargar huellas automáticamente al iniciar
        loadCacheFromDatabase();
    }
    
    private void loadCacheFromDatabase() {
        tvStatus.setText("Cargando huellas...");
        fingerprintService.loadAllFingerprintsToCache();
        
        // Mostrar cantidad después de cargar
        new android.os.Handler().postDelayed(() -> {
            int count = fingerprintService.getFingerprintCount();
            tvCount.setText("Huellas en caché: " + count);
            tvStatus.setText("Listo para usar");
        }, 1000);
    }
    
    private void startRegistration() {
        String userId = etUserId.getText().toString().trim();
        if (userId.isEmpty()) {
            Toast.makeText(this, "Ingrese un ID de usuario", Toast.LENGTH_SHORT).show();
            return;
        }
        
        tvStatus.setText("Registrando - Presione el dedo 3 veces");
        btnRegister.setEnabled(false);
        btnVerify.setEnabled(false);
        
        fingerprintService.registerFingerprint(userId, new FingerprintService.RegisterCallback() {
            @Override
            public void onProgress(int remaining) {
                runOnUiThread(() -> {
                    tvStatus.setText("Registrando - Faltan " + remaining + " capturas");
                });
            }
            
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Huella registrada exitosamente", Toast.LENGTH_SHORT).show();
                    tvStatus.setText("Registro completado");
                    etUserId.setText("");
                    int count = fingerprintService.getFingerprintCount();
                    tvCount.setText("Huellas en caché: " + count);
                    btnRegister.setEnabled(true);
                    btnVerify.setEnabled(true);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                    tvStatus.setText("Error en registro");
                    btnRegister.setEnabled(true);
                    btnVerify.setEnabled(true);
                });
            }
        });
    }
    
    private void startVerification() {
        tvStatus.setText("Verificando - Presione el dedo");
        btnRegister.setEnabled(false);
        btnVerify.setEnabled(false);
        
        fingerprintService.verifyFingerprint(new FingerprintService.VerifyCallback() {
            @Override
            public void onVerified(boolean success, String userId, int score) {
                runOnUiThread(() -> {
                    if (success) {
                        tvStatus.setText("✅ Verificado! Usuario: " + userId + " (Score: " + score + ")");
                        Toast.makeText(MainActivity.this, "Usuario verificado: " + userId, Toast.LENGTH_SHORT).show();
                    } else {
                        tvStatus.setText("❌ Huella no reconocida");
                        Toast.makeText(MainActivity.this, "Huella no reconocida", Toast.LENGTH_SHORT).show();
                    }
                    btnRegister.setEnabled(true);
                    btnVerify.setEnabled(true);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    tvStatus.setText("Error en verificación");
                    btnRegister.setEnabled(true);
                    btnVerify.setEnabled(true);
                });
            }
        });
    }
    
    private void showInfoDialog() {
        int count = fingerprintService.getFingerprintCount();
        new MaterialAlertDialogBuilder(this)
            .setTitle("Información")
            .setMessage("Huellas registradas en caché: " + count + 
                       "\n\nLas huellas se guardan persistentemente en la base de datos local.\n" +
                       "Al reiniciar la app, se cargan automáticamente a la caché del SDK.")
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
