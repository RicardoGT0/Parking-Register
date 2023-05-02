package fenovtec.e_entrada;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.net.ftp.FTP; //Apache Commons Net 3.8.0

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    static final int REQUEST_TAKE_PHOTO = 1;
    String currentPhotoPath; // ruta de la foto tomada
    String currentPath;  // ruta de trabajo en el servidor FTP
    String currentTextPath; // ruta del archivo con informacion del momento de la entrada
    String vehiculo; // tipo de vehiculo
    String estacionamiento; //zona de estacionamiento asignada
    RadioGroup radioGroup;
    Spinner spinner;


    private RadioButton crearRadioButton(String texto) {
        //creacion del RadioButton
        RadioButton nuevoRadio = new RadioButton(this);
        LinearLayout.LayoutParams params = new RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.WRAP_CONTENT,
                RadioGroup.LayoutParams.WRAP_CONTENT);
        nuevoRadio.setLayoutParams(params);
        nuevoRadio.setText(texto);
        nuevoRadio.setTag(texto);
        // configuracion del comportamiento
        nuevoRadio.setOnClickListener(view -> {
            estacionamiento=view.getTag().toString().split(" ")[0];
            System.out.println("Estacionamiento: "+ estacionamiento);
        });
        return nuevoRadio;
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

                if (photoFile != null) {
                    Uri photoURI = FileProvider.getUriForFile(Objects.requireNonNull(getApplicationContext()),
                            "com.fenovtec.e_entrada.provider",
                            photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);// se hace el llamado a la interfaz de la camara
                }
            }
        }
        else{
            Snackbar.make(view, "Error: se necesita escanear tarjeta", Snackbar.LENGTH_LONG).show();
        }
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // metodo con el que inicia la app
        //inicializacion de variables y objetos
        currentPath=null;
        setContentView(R.layout.activity_main);
        radioGroup = findViewById(R.id.radioGroup1);

        spinner = findViewById(R.id.spinner); //configuracion del comportamiento del spinner
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                radioGroup.clearCheck();
                radioGroup.removeAllViews();

                if (currentPath!=null) {

                    vehiculo = adapterView.getItemAtPosition(i).toString();
                    System.out.println("Vehiculo: "+vehiculo);

                    switch (vehiculo) {
                        // dependendiendo del tipo de vehiculo se crean las opciones
                        // del estacionamiento en el que puede estar
                        case "Motocicleta":
                        case "Automóvil":
                            estacionamiento=null;
                            radioGroup.addView(crearRadioButton("VIP1 ."));
                            radioGroup.addView(crearRadioButton("VIP2 ."));
                            radioGroup.addView(crearRadioButton("VIP3 ."));
                            radioGroup.addView(crearRadioButton("VIP4 ."));
                            radioGroup.addView(crearRadioButton("VIP5 ."));
                            break;
                        case "Trip":
                            estacionamiento=null;
                            radioGroup.addView(crearRadioButton("II  ----  $ 450"));
                            break;
                        case "Camión":
                            estacionamiento=null;
                            radioGroup.addView(crearRadioButton("I  ----  $ 500"));
                            break;
                        default:
                            estacionamiento=null;
                            radioGroup.clearCheck();
                            radioGroup.removeAllViews();
                    }
                }
                else{
                    Snackbar.make(view, "Error: se necesita escanear tarjeta", Snackbar.LENGTH_LONG).show();
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }


    public void enviar(View view) {
        //metodo para enviar datos por FTP
        if (currentPath!=null) {
            File localFile;
            String remoteFile;
            int fileType;
            if (currentPhotoPath != null && estacionamiento != null) {
                // inicializar variables para envio de imagen
                localFile = new File(currentPhotoPath);
                remoteFile = "imagen.jpg";
                fileType = FTP.BINARY_FILE_TYPE;
                long size = localFile.length();
                if (size>1) {
                    // Creacion del hilo para enviar la imagen
                    Runnable hiloImagen = new HiloEnviar(localFile, remoteFile, fileType, currentPath, view);
                    Thread enviarImagen = new Thread(hiloImagen);
                    enviarImagen.start();

                    try {
                        enviarImagen.join(); // esperar a que termine de enviar la imagen
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //inicializar variables para enviar archivo de texto
                    llenarTexto();
                    localFile = new File(currentTextPath);
                    remoteFile = "text.txt";
                    fileType = FTP.ASCII_FILE_TYPE;
                    //creacion de hilo para enviar el archivo de texto
                    Runnable hiloTexto = new HiloEnviar(localFile, remoteFile, fileType, currentPath, view);
                    Thread enviarTexto = new Thread(hiloTexto);
                    enviarTexto.start();
                }
                else{
                    Snackbar.make(view, "Error: La Foto es muy pequeña (" + size + " bytes). Vuelva a Escanear la Tarjeta", Snackbar.LENGTH_LONG).show();
                }
            }
            else {
                Snackbar.make(view, "Error: Faltan Datos por Llenar. Vuelva a Escanear la Tarjeta", Snackbar.LENGTH_LONG).show();
            }

            // se reestablecen los variables y componentes usados
            currentPhotoPath=null;
            currentTextPath=null;
            currentPath=null;
            estacionamiento=null;
            vehiculo=null;
            radioGroup.clearCheck();
            radioGroup.removeAllViews();

        }
        else{
            Snackbar.make(view, "Error: se necesita escanear tarjeta", Snackbar.LENGTH_LONG).show();
        }
    }

    private File createTXTFile() throws IOException {
        // Creacion de un arhivo de texto
        String imageFileName = "TXT_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        File txtFile = File.createTempFile(
                imageFileName,  /* prefijo */
                ".txt",         /* sufijo */
                storageDir      /* directorio */
        );

        currentTextPath = txtFile.getAbsolutePath();

        return txtFile;
    }

    private void llenarTexto() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        try {
            File txtFile = createTXTFile(); // se crea archivo de texto
            FileWriter fw=new FileWriter(txtFile);
            BufferedWriter bw=new BufferedWriter(fw);
            System.out.println(timeStamp+","+vehiculo+","+estacionamiento);
            //se llena con fecha y hora del sistema, tipo de vehiculo y estacionamiento asignado
            bw.write(timeStamp+","+vehiculo+","+estacionamiento);
            bw.flush();
            bw.close();
            fw.close(); // se cierra el archivo
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void nuevo_registro(View view) {
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
                System.out.println("CurrentPanth: "+ currentPath);
                dialogInterface.dismiss();
            }
            return false;
        });

        AlertDialog dialog = builder.create();
        dialog.show(); // se muestra el cuadro de dialogo
        spinner.setSelection(0);
    }
}