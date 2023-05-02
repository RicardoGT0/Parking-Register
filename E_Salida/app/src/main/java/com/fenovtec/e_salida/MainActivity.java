package com.fenovtec.e_salida;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.net.ftp.FTPClient; //Apache Commons Net 3.8.0

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private String currentPath; // ruta de trabajo en el servidor FTP
    private ImageView imageView;
    private String currentPhotoPath; //Ruta de la imagen local
    final String server="192.168.0.5"; // parametros del servidor
    final String user="user1";
    final String password="123456";
    final int port=2121;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //incializacion de la app
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = findViewById(R.id.imageView);
        currentPhotoPath=null;
        currentPath=null;
        imageView.setImageResource(0);

    }

    public void leerTarjeta(View view) {
        currentPhotoPath=null; //se limpian variables
        currentPath= null;

        // Cuadro de dialogo para leer tarjeta RFID
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Acerca tarjeta al lector");
        Context con=builder.getContext();
        EditText text =  new EditText(con);
        builder.setView(text);

        builder.setOnKeyListener((dialogInterface, i, keyEvent) -> {
            // se requiere de tecla ENTER para cerrar cuadro de dialogo
            if (i == KeyEvent.KEYCODE_ENTER) {
                currentPath = String.valueOf(text.getText());
                if (currentPath.equals("") || currentPath.equals(" ") || currentPath.equals("\n")){
                    currentPath=null;
                }
                dialogInterface.dismiss();
            }
            return false;
        });

        // metodo que se ejecura al cerrar el cuadro de dialogo
        builder.setOnDismissListener(dialogInterface -> {
            if (currentPath!=null) {
                Thread descargarImagen = new Thread(hiloDescargarIm);
                descargarImagen.start(); // hilo para descargar imagen del servidor FTP
                try {
                    descargarImagen.join(); // esperar a que termine la descarga
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Thread colocarImagen = new Thread(hiloColocarIm);
                colocarImagen.start(); // Hilo para la poner la imagen en un ImageView
            }
        });

        AlertDialog dialog=  builder.create();
        dialog.show(); // se muestra el cuadro de dialogo

        }


    Runnable hiloDescargarIm = new Runnable() {
        // Metodos a ejecutar en el Hilo para descargar imagen
        @Override
        public void run() {
            String serverRoad=currentPath; // ruta en el servidor

            try  {
                //Creacion y configuracion del cliente FTP
                FTPClient ftpClient = new FTPClient();
                // conexion al servidor
                ftpClient.connect(server,port);
                ftpClient.login(user, password);
                // creacion del directorio de trabajo en caso de no existir
                ftpClient.makeDirectory(serverRoad);
                ftpClient.changeWorkingDirectory(serverRoad);
                System.out.println("FTP Directory --> "+ftpClient.printWorkingDirectory());
                //creacion del archivo local que va a recibir la informacion del archivo remoto
                currentPhotoPath = getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath()+"/image.jpg";
                System.out.println("Local Directory --> "+currentPhotoPath);

                //Preparativos para la tranferencia
                OutputStream outputStream = new FileOutputStream(currentPhotoPath);
                ftpClient.enterLocalPassiveMode();
                ftpClient.setBufferSize(1024);
                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
                // Se realiza la transferencia
                boolean done = ftpClient.retrieveFile("imagen.jpg", outputStream);
                outputStream.close();

                if (done) {
                    System.out.println("The file is Downloaded successfully.");
                }
                else{
                    currentPhotoPath=null;
                    currentPath=null;
                    System.out.println("The file not found.");
                    Snackbar.make(findViewById(R.id.imageView), "No hay Informacion para esta Tarjeta", Snackbar.LENGTH_LONG).show();
                }

                // se cierra conexion
                ftpClient.logout();
                ftpClient.disconnect();

            } catch (Exception e) {
                System.out.println(e.toString());
            }
        }
    };

    Runnable hiloColocarIm = new Runnable() {
        //Metodos a Ejecutar en el Hilo para colocar la imagen en un ImageView
        @Override
        public void run() {
            runOnUiThread(() -> { // Metodo para la interaccion con interfaz grafica del Hilo Principal

                if (currentPhotoPath != null) {
                    // la imagen se convierte a mapa de bits
                    Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath);
                    imageView.setImageBitmap(bitmap); // se coloca la imagen en el control
                }
                else{
                    imageView.setImageResource(0); // se limpia el Control
                }
            });
        }
    };


    Runnable hiloCambiarNom = new Runnable() {
        // Metodos a ejecutar en el hilo para cambiar Nombre al directorio en el servidor FTP
        @Override
        public void run() {
            String serverRoad=currentPath; // ruta en el servidor
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

            try {
                //Creacion y configuracion del cliente FTP
                FTPClient ftpClient = new FTPClient();
                // conexion al servidor
                ftpClient.connect(server, port);
                ftpClient.login(user, password);
                // Cambio de nombre del directorio
                boolean done = ftpClient.rename(serverRoad, timeStamp);
                if (done) {
                    System.out.println("The rename is successfully.");
                    Snackbar.make(findViewById(R.id.imageView), "Salida Registrada con Exito", Snackbar.LENGTH_LONG).show();
                }
                else{
                    System.out.println("The rename is failed.");
                    Snackbar.make(findViewById(R.id.imageView), "Error: Fallo Registro de Salida", Snackbar.LENGTH_LONG).show();
                }

                // se cierra conexion
                ftpClient.logout();
                ftpClient.disconnect();

            } catch (Exception e) {
                System.out.println(e.toString());
            }
            // Se Limpian Variables
            currentPath=null;
            currentPhotoPath=null;

        }
    };

    public void registroSalida(View view) {
        // Metodo para registrar salida del vehiculo
        if (currentPath!=null) {
            Thread cambiarNombre = new Thread(hiloCambiarNom);
            cambiarNombre.start(); // Se ejecuta hilo para cambiar nombre al directorio FTP
            imageView.setImageResource(0); // se limpia Control imageView
        }
        else{
            Snackbar.make(view, "Error: se necesita escanear tarjeta", Snackbar.LENGTH_LONG).show();
        }
    }

    public void zoom(View view) {
        //metodo para abrir la imagen en un visor externo
        if(currentPath!=null ) {
            System.out.println("PhotoPath --->  " + currentPhotoPath);

            //se requiere direccion URI del archivo de imagen
            File file=new File(currentPhotoPath);
            Uri path= FileProvider.getUriForFile(MainActivity.this,"com.fenovtec.e_salida.provider",file);
            System.out.println("URI ---> " + path);
            Intent intent=new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(path,"image/*");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivity(intent); // se lanza solicitud de apertura de la imagen
        }
        else{
            Snackbar.make(view, "Error: se necesita ID de REGISTRO", Snackbar.LENGTH_LONG).show();
        }
    }
}