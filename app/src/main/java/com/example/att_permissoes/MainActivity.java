package com.example.att_permissoes;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private static final String CANAL_ID = "canal_fotos";

    private TextView txtStatus;
    private TextView txtStatusAudio;
    private TextView txtStatusLocalizacao;
    private ImageView imgFoto;

    private final ActivityResultLauncher<String> pedirPermissaoLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedida -> {
                atualizarStatus();
                if (concedida) {
                    abrirCamera();
                } else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    mostrarDialogoConfiguracoes();
                } else {
                    Toast.makeText(this, "Sem a permissão não dá para tirar foto", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> pedirAudioLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedida -> {
                atualizarStatus();
                if (concedida) {
                    Toast.makeText(this, "Permissão do microfone concedida", Toast.LENGTH_SHORT).show();
                } else if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    mostrarDialogoConfiguracoes();
                } else {
                    Toast.makeText(this, "Permissão do microfone negada", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String[]> pedirMultiplasLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), resultado -> {
                atualizarStatus();
                boolean cameraOk = Boolean.TRUE.equals(resultado.get(Manifest.permission.CAMERA));
                boolean audioOk = Boolean.TRUE.equals(resultado.get(Manifest.permission.RECORD_AUDIO));
                String mensagem = "Câmera: " + (cameraOk ? "CONCEDIDA" : "NEGADA")
                        + " | Microfone: " + (audioOk ? "CONCEDIDA" : "NEGADA");
                Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show();
            });

    private final ActivityResultLauncher<String> pedirNotificacaoLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedida -> {
                if (concedida) {
                    mostrarNotificacaoFoto();
                }
            });

    private final ActivityResultLauncher<String[]> pedirLocalizacaoLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), resultado -> {
                atualizarStatus();
                boolean exata = Boolean.TRUE.equals(resultado.get(Manifest.permission.ACCESS_FINE_LOCATION));
                boolean aproximada = Boolean.TRUE.equals(resultado.get(Manifest.permission.ACCESS_COARSE_LOCATION));

                if (exata) {
                    Toast.makeText(this, "Localização exata concedida", Toast.LENGTH_SHORT).show();
                } else if (aproximada) {
                    Toast.makeText(this, "Apenas localização aproximada concedida", Toast.LENGTH_SHORT).show();
                } else if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    mostrarDialogoConfiguracoes();
                } else {
                    Toast.makeText(this, "Permissão de localização negada", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Void> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), foto -> {
                if (foto != null) {
                    imgFoto.setImageBitmap(foto);
                    vibrar();
                    verificarPermissaoNotificacao();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        txtStatus = findViewById(R.id.txtStatus);
        txtStatusAudio = findViewById(R.id.txtStatusAudio);
        txtStatusLocalizacao = findViewById(R.id.txtStatusLocalizacao);
        imgFoto = findViewById(R.id.imgFoto);

        Button btnFoto = findViewById(R.id.btnFoto);
        Button btnAudio = findViewById(R.id.btnAudio);
        Button btnMultiplas = findViewById(R.id.btnMultiplas);
        Button btnLocalizacao = findViewById(R.id.btnLocalizacao);

        btnFoto.setOnClickListener(v -> verificarPermissao());
        btnAudio.setOnClickListener(v -> verificarPermissaoAudio());
        btnMultiplas.setOnClickListener(v -> pedirMultiplasLauncher.launch(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        }));
        btnLocalizacao.setOnClickListener(v -> pedirLocalizacaoLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarStatus();
    }

    private void verificarPermissao() {
        if (temPermissao()) {
            abrirCamera();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            mostrarExplicacao();
        } else {
            pedirPermissaoLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void verificarPermissaoAudio() {
        if (temPermissaoAudio()) {
            Toast.makeText(this, "Microfone já está autorizado", Toast.LENGTH_SHORT).show();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            new AlertDialog.Builder(this)
                    .setTitle("Precisamos do microfone")
                    .setMessage("O microfone é necessário para gravar áudio no app.")
                    .setPositiveButton("Continuar", (dialogo, botao) ->
                            pedirAudioLauncher.launch(Manifest.permission.RECORD_AUDIO))
                    .setNegativeButton("Agora não", null)
                    .show();
        } else {
            pedirAudioLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private boolean temPermissao() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean temPermissaoAudio() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void atualizarStatus() {
        if (temPermissao()) {
            txtStatus.setText("Permissão da câmera: CONCEDIDA");
            txtStatus.setTextColor(Color.rgb(0, 128, 0));
        } else {
            txtStatus.setText("Permissão da câmera: NÃO CONCEDIDA");
            txtStatus.setTextColor(Color.RED);
        }

        if (temPermissaoAudio()) {
            txtStatusAudio.setText("Permissão do microfone: CONCEDIDA");
            txtStatusAudio.setTextColor(Color.rgb(0, 128, 0));
        } else {
            txtStatusAudio.setText("Permissão do microfone: NÃO CONCEDIDA");
            txtStatusAudio.setTextColor(Color.RED);
        }

        boolean locExata = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean locAprox = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (locExata) {
            txtStatusLocalizacao.setText("Localização: EXATA");
            txtStatusLocalizacao.setTextColor(Color.rgb(0, 128, 0));
        } else if (locAprox) {
            txtStatusLocalizacao.setText("Localização: APROXIMADA");
            txtStatusLocalizacao.setTextColor(Color.rgb(204, 120, 0));
        } else {
            txtStatusLocalizacao.setText("Localização: NÃO CONCEDIDA");
            txtStatusLocalizacao.setTextColor(Color.RED);
        }
    }

    private void mostrarExplicacao() {
        new AlertDialog.Builder(this)
                .setTitle("Precisamos da câmera")
                .setMessage("A câmera é usada apenas para tirar a foto que aparece nesta tela.")
                .setPositiveButton("Continuar", (dialogo, botao) ->
                        pedirPermissaoLauncher.launch(Manifest.permission.CAMERA))
                .setNegativeButton("Agora não", null)
                .show();
    }

    private void mostrarDialogoConfiguracoes() {
        new AlertDialog.Builder(this)
                .setTitle("Permissão negada")
                .setMessage("Para usar este recurso, ative a permissão nas Configurações do app.")
                .setPositiveButton("Abrir Configurações", (dialogo, botao) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void abrirCamera() {
        try {
            cameraLauncher.launch(null);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Nenhum app de câmera encontrado", Toast.LENGTH_LONG).show();
        }
    }

    private void vibrar() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(200);
            }
        }
    }

    private void verificarPermissaoNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                mostrarNotificacaoFoto();
            } else {
                pedirNotificacaoLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            mostrarNotificacaoFoto();
        }
    }

    private void mostrarNotificacaoFoto() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CANAL_ID,
                    "Fotos",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            manager.createNotificationChannel(canal);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CANAL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Foto capturada")
                .setContentText("A foto foi tirada e exibida na tela.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        manager.notify(1, builder.build());
    }
}
