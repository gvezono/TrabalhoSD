package servidor;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Servidor {

    private static final Logger logger = Logger.getLogger(Servidor.class.getName());

    private final boolean restricao = false;

    private Server server;

    private void start() throws IOException {
        Path dirsv = Paths.get("SERVIDOR");
        if (Files.notExists(dirsv)) {
            File file = new File(dirsv.toUri());
            file.mkdir();
        }
        int port = 50051;
        server = ServerBuilder.forPort(port)
                .addService(new ServerFuncIm())
                .build()
                .start();
        logger.log(Level.INFO, "Server started, listening on {0}", port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                Servidor.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final Servidor server = new Servidor();
        server.start();
        server.blockUntilShutdown();
    }

    static class ServerFuncIm extends ServidorFuncGrpc.ServidorFuncImplBase {

        SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);
        String dataFormatada;
        String file;
        File arquivo;
        
        private ServerFuncIm(){
            formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));
        }

        @Override
        public void enviar(EnviarRequest req, StreamObserver<EnviarReply> responseObserver){
            file = req.getNome();
            arquivo = new File("SERVIDOR" + File.separator + file);
            try {
                Files.write(arquivo.toPath(), req.getArquivo().toByteArray());
            } catch (IOException ex) {
                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
            }

            dataFormatada = formatador.format(new Date()) + " GMT-3";
            EnviarReply reply = EnviarReply.newBuilder()
                    .setData(dataFormatada)
                    .setNome(req.getNome())
                    .setStatus("OK")
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void listar(ListarRequest req, StreamObserver<ListarReply> responseObserver) {
            arquivo = new File("SERVIDOR" + File.separator);
            String[] lista = arquivo.list();
            String erro = "";
            if (lista == null) {
                lista = new String[1];
                erro = "IOException";
            }
            String status;
            if (erro.equals("")) {
                status = "OK";
            } else {
                status = "ERRO " + erro;
            }
            dataFormatada = formatador.format(new Date()) + " GMT-3";
            ListarReply.Builder reply = ListarReply.newBuilder();
            for (String nome : lista) {
                reply = reply.addNome(nome);
            }
            reply = reply.setStatus(status);
            reply = reply.setData(dataFormatada);
            responseObserver.onNext(reply.build());
            responseObserver.onCompleted();
        }

        @Override
        public void receber(ReceberRequest req, StreamObserver<ReceberReply> responseObserver) {
            file = req.getNome();
            String erro = "";
            ReceberReply.Builder reply = ReceberReply.newBuilder();
            try {
                arquivo = new File("SERVIDOR" + File.separator + file);
                reply = reply.setArquivo(ByteString.copyFrom(Files.readAllBytes(arquivo.toPath())));
            } catch (Exception e) {
                erro = e.getClass().getSimpleName();
            }

            dataFormatada = formatador.format(new Date()) + " GMT-3";
            if (erro.equals("")) {
                reply = reply.setStatus("OK");
            } else {
                reply = reply.setStatus("ERRO " + erro);
            }
            reply = reply.setData(dataFormatada).setNome(req.getNome());
            responseObserver.onNext(reply.build());
            responseObserver.onCompleted();
        }
    }
}
