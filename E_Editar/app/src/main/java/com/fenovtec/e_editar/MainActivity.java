package com.fenovtec.e_editar;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient; //Apache Commons Net 3.8.0

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_TAKE_PHOTO = 1;
    private String currentPath; // ruta de trabajo en el servidor FTP
    private String currentPhotoPath; //Ruta de la imagen local
    final String server="192.168.0.5"; // parametros del servidor
    final String user="user1";
    final String password="123456";
    final int port=2121;

    private ImageView imageView;


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

    public void leerID(View view) {
        currentPhotoPath=null; //se limpian variables
        currentPath= null;

        // Ventana para solicitar ID
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Escribe el ID de REGISTRO");
        Context con=builder.getContext();
        EditText text =  new EditText(con);
        builder.setView(text);
        builder.setPositiveButton("Aceptar", (dialogInterface, i) -> {
            currentPath = String.valueOf(text.getText());
            if (currentPath.equals("") || currentPath.equals(" ") || currentPath.equals("\n")){
                currentPath=null;
            }
            dialogInterface.dismiss();
        });

        // metodo que se ejecura al cerrar el cuadro de dialogo
        builder.setOnDismissListener(dialogInterface -> {
            if (currentPath!=null) {
                Thread descargarImagen= new Thread(hiloDescargarIm);
                descargarImagen.start(); // hilo para descargar imagen del servidor FTP
                try {
                    descargarImagen.join(); // esperar a que termine la descarga
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                try {
                    Thread colocarImagen = new Thread(HiloColocarIm);
                    colocarImagen.start(); // Hilo para la poner la imagen en un ImageView
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        AlertDialog dialog = builder.create();
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
                    Snackbar.make(findViewById(R.id.imageView), "No hay Información para este ID de REGISTRO, Vuelva a Escribir el ID de REGISTRO", Snackbar.LENGTH_LONG).show();
                }

                // se cierra conexion
                ftpClient.logout();
                ftpClient.disconnect();

            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    };

    Runnable HiloColocarIm = new Runnable() {
        //Metodos a Ejecutar en el Hilo para colocar la imagen en un ImageView
        @Override
        public void run() {
            runOnUiThread(() -> { // Metodo para la interaccion con interfaz grafica del Hilo Principal

                if (currentPhotoPath != null) {
                    // la imagen se convierte a mapa de bits
                    Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath);
                    //android:layout_width="190dp"
                    //android:layout_height="165dp"
                    imageView.setImageBitmap(bitmap); // se coloca la imagen en el control
                }
                else{
                    imageView.setImageResource(0); // se limpia el Control
                }
            });
        }
    };

    public void zoom(View view) {
        //metodo para abrir la imagen en un visor externo
        if(currentPath!=null ) {
            System.out.println("PhotoPath --->  " + currentPhotoPath);

            //se requiere direccion URI del archivo de imagen
            File file=new File(currentPhotoPath);
            Uri path= FileProvider.getUriForFile(MainActivity.this,"com.fenovtec.e_editar.provider",file);
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

    public void onCapture(View view) {
        // creacion del objeto para llamado a la interfaz de la camara para tomar la foto
        if (currentPath != null) {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);


            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null; // archivo par almacenar la foto
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    Snackbar mensaje = Snackbar.make(view, ex.toString(), Snackbar.LENGTH_LONG);
                    mensaje.show();
                }
                // Continue only if the File was successfully created
                if (photoFile != null) {
                    Uri photoURI = FileProvider.getUriForFile(Objects.requireNonNull(getApplicationContext()),
                            "com.fenovtec.e_editar.provider",
                            photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO); // se hace el llamado a la interfaz de la camara
                }
            }
        }
        else{
            Snackbar.make(view, "Error: se necesita ID de REGISTRO", Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // metodo que se ejecuta al salir de la interfaz de camara
        super.onActivityResult(requestCode, resultCode, data);
        Thread colocarImagen = new Thread(HiloColocarIm);
        colocarImagen.start();// se ejecuta hilo para colocar imagen tomada en ImageView
    }

    private File createImageFile() throws IOException {
        // creacion de archivo de imagen

        String imageFileName = "JPEG_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES); // ruta de trabajo local de la app
        File image = File.createTempFile(
                imageFileName,  /* prefijo */
                ".jpg",         /* sufijo */
                storageDir      /* directorio */
        );

        // se guarda la ruta del archivo
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    Runnable hiloEnviarIm = new Runnable() {
        // Metodos a ejecutar en el Hilo para enviar Imagen al servidor FTP
        @Override
        public void run() {
            // Direcciones locales y remotas de trabajo
            String serverRoad=currentPath;
            File localFile = new File(currentPhotoPath);
            String remoteFile = "imagen.jpg";
            int fileType = FTP.BINARY_FILE_TYPE;

            try  {

                FTPClient ftpClient = new FTPClient();
                // conexion al servidor
                ftpClient.connect(server,port);
                ftpClient.login(user, password);
                // creacion del directorio de trabajo en caso de no existir
                ftpClient.makeDirectory(serverRoad);
                ftpClient.changeWorkingDirectory(serverRoad);

                System.out.println(ftpClient.printWorkingDirectory());
                // preparativos para el envio del archivo
                InputStream inputStream = new FileInputStream(localFile);
                ftpClient.setFileType(fileType);
                ftpClient.enterLocalPassiveMode();

                boolean done = ftpClient.storeFile(remoteFile, inputStream); // envio del archivo
                inputStream.close();
                if (done) {
                    System.out.println("The file "+ localFile +" is uploaded successfully.");
                    Snackbar.make(findViewById(R.id.imageView), "Se Envió Foto Exitosamente ", Snackbar.LENGTH_LONG).show();

                }
                else{
                    Snackbar.make(findViewById(R.id.imageView), "Ocurrió un error, Vuelva a Escribir el ID de REGISTRO", Snackbar.LENGTH_LONG).show();
                }
                // se cierra conexion
                ftpClient.logout();
                ftpClient.disconnect();

            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    };

    public void enviar(View view) {
        // metodo donde se ejecuta el hilo para enviar la imagen al servidor
        if (currentPath!=null) {
            if (currentPhotoPath != null) {
                Thread enviarImagen = new Thread(hiloEnviarIm);
                enviarImagen.start(); // Se ejecuta hilo para enviar imagen al servidor PFT
                Snackbar.make(findViewById(R.id.imageView), "Se Envió Foto Exitosamente ", Snackbar.LENGTH_LONG).show();
            }
            else {
                Snackbar.make(view, "Error: No hay Imagen. Vuelva a Escribir el ID de REGISTRO", Snackbar.LENGTH_LONG).show();
            }
            // se limpian las variables y controles usados
            currentPhotoPath=null;
            currentPath=null;
            imageView.setImageResource(0);

        }
        else{
            Snackbar.make(view, "Error: se necesita ID de REGISTRO", Snackbar.LENGTH_LONG).show();
        }
    }
}