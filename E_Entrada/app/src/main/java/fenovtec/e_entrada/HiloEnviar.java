package fenovtec.e_entrada;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.net.ftp.FTPClient; //Apache Commons Net 3.8.0

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class HiloEnviar implements Runnable {
//clase que envia un archivo por FTP
    // Informacion del servidor
    final String server="192.168.0.5";
    final String user="user1";
    final String password="123456";
    final int port=2121;
    View view;

    File localFile;
    String remoteFile;
    int fileType;
    String serverRoad;

    public HiloEnviar(File localFile, String remoteFile, int fileType, String serverRoad, View view){
        // se inicializan variables con parametros proporcionados
        this.localFile=localFile;
        this.remoteFile=remoteFile;
        this.fileType=fileType;
        this.serverRoad=serverRoad;
        this.view=view;
    }

    @Override
    public void run() {
        try  {

            FTPClient ftpClient = new FTPClient();
            // conexion al servidor
            ftpClient.connect(this.server,this.port);
            ftpClient.login(this.user, this.password);
            // creacion del directorio de trabajo en caso de no existir
            ftpClient.makeDirectory(this.serverRoad);
            ftpClient.changeWorkingDirectory(this.serverRoad);

            System.out.println(ftpClient.printWorkingDirectory());
            // preparativos para el envio del archivo
            InputStream inputStream = new FileInputStream(this.localFile);
            ftpClient.setFileType(this.fileType);
            ftpClient.enterLocalPassiveMode();

            boolean done = ftpClient.storeFile(this.remoteFile, inputStream); //envio del archivo
            inputStream.close();
            if (done) {
                System.out.println("The file "+ this.localFile +" is uploaded successfully.");
                Snackbar.make(this.view, "Se Envió "+ remoteFile +" Exitosamente ", Snackbar.LENGTH_LONG).show();
            }

            // se cierra conexion
            ftpClient.logout();
            ftpClient.disconnect();

        } catch (Exception e) {
            System.out.println(e.toString());
            Snackbar.make(this.view, "Error al Enviar "+ remoteFile , Snackbar.LENGTH_LONG).show();
        }
    }
}
