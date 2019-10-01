package servidor;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Servidor {

    public static void main(String[] args) throws IOException {
        ServerSocket servidor = new ServerSocket(5566);
        ExecutorService pool = Executors.newCachedThreadPool();
        boolean restricao = false;
        for (String arg : args) {
            if(arg.trim().equals("restricao")){
                restricao = true;
            }
        }

        while (true) {
            pool.execute(new ThreadConexao(servidor.accept(),restricao));
        }
    }
}
