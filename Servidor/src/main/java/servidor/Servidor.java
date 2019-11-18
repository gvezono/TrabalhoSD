package servidor;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Servidor {

    private static final Logger logger = Logger.getLogger(Servidor.class.getName());

    //private final boolean restricao = false;

    private Server server;

    private void start() throws IOException {
        InputStream is = Servidor.class.getResourceAsStream("/app.properties");
        Properties prop = new Properties();
        int porta;
        String saida;
        try {
            prop.load(is);
            porta = Integer.parseInt(prop.getProperty("porta"));
            saida = prop.getProperty("saida");
        } catch (IOException | NumberFormatException e) {
            logger.log(Level.INFO, e.toString());
            porta = 50051;
            saida = "SERVIDOR";
        }
        Path dirsv = Paths.get(saida);
        if (Files.notExists(dirsv)) {
            File file = new File(dirsv.toUri());
            file.mkdir();
        }
        server = ServerBuilder.forPort(porta)
                .addService(new ServerFuncImpl())
                .maxInboundMessageSize(Integer.MAX_VALUE)
                .build()
                .start();
        logger.log(Level.INFO, "Server started, listening on {0}", porta);
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

    static class ServerFuncImpl extends ServidorFuncGrpc.ServidorFuncImplBase {

        SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);
        String dataFormatada;
        String file;
        File arquivo;

        private ServerFuncImpl() {
            formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));
        }

        @Override
        public void enviar(EnviarRequest req, StreamObserver<EnviarReply> responseObserver) {
            file = req.getNome();
            arquivo = new File("SERVIDOR" + File.separator + file);
            int count = req.getArquivoCount();
            long control = 0;
            long size = req.getTamanho();
            byte[] arq = new byte[(int) size];
            for (int i = 0; i < count; i++) {
                byte[] aux = req.getArquivo(i).toByteArray();
                System.arraycopy(aux, 0, arq, (int) control, aux.length);
                control += aux.length;
            }
            try {
                Files.write(arquivo.toPath(), arq);
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
                byte[] arq = Files.readAllBytes(arquivo.toPath());
                long control = 0;
                long size = arq.length;
                int datalen = Integer.MAX_VALUE;
                while ((control + datalen) < size) {
                    reply = reply.addArquivo(ByteString.copyFrom(arq, (int) control, datalen));
                    control += datalen;
                }
                reply.setTamanho(size);
                reply = reply.addArquivo(ByteString.copyFrom(arq, (int) control, (int) (size - control)));
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
