package servidor;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Servidor {

    private static final Logger logger = Logger.getLogger(Servidor.class.getName());

    private HashMap<BigInteger, ServerCli> map = new HashMap<BigInteger, ServerCli>();
    private BigInteger[] ft;
    //BigInteger = hash convertido para int, servercli = conexao com o servidor

    private int porta;
    private String ip;

    private String saida;

    //private final boolean restricao = false;
    private Server server;

    //ft
    private int m = 256; //sha256
    private BigInteger p; //hash ip+porta

    public Servidor() throws SocketException, UnknownHostException, NoSuchAlgorithmException {
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            this.ip = socket.getLocalAddress().getHostAddress();
        }
        InputStream is = Servidor.class.getResourceAsStream("/app.properties");
        Properties prop = new Properties();
        try {
            prop.load(is);
            this.porta = Integer.parseInt(prop.getProperty("porta"));
            saida = prop.getProperty("saida");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((this.ip + this.porta).getBytes());
            p = new BigInteger(digest.digest());
            Enumeration e = prop.propertyNames();
            boolean skipnext = false;
            String newip, prefix;
            int newport;
            byte[] hash;
            while (e.hasMoreElements()) {
                String nome = (String) e.nextElement();
                if (nome.startsWith("servidor")) {
                    prefix = nome.split(".")[0];
                    if (skipnext) {
                        skipnext = false;
                    } else {
                        newip = prop.getProperty(prefix + ".ip");
                        newport = Integer.parseInt(prop.getProperty(prefix + ".porta"));
                        digest = MessageDigest.getInstance("SHA-256");
                        digest.update((newip + newport).getBytes());

                        hash = digest.digest();
                        this.map.put(new BigInteger(hash), new ServerCli(newip, newport));
                        skipnext = true;
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            logger.log(Level.INFO, e.toString());
            this.porta = 50051;
            this.saida = "SERVIDOR";
        }
        Path dirsv = Paths.get(this.saida);
        if (Files.notExists(dirsv)) {
            File file = new File(dirsv.toUri());
            file.mkdir();
        }
    }

    private void start() throws IOException {
        server = ServerBuilder.forPort(this.porta)
                .addService(new ServerFuncImpl(this.saida))
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

    private void create() {
        List<BigInteger> sortedKeys = new ArrayList<>(map.keySet());
        Collections.sort(sortedKeys);
        ft = new BigInteger[m];
        BigInteger suc, aux, real;
        int i;
        aux = new BigInteger("2");
        for (i = 0; i < m; i++) {
            suc = p.add(aux.pow(i)).mod(aux.pow(m));
            ft[i] = suc;
        }
        i = 0;
        Iterator it = sortedKeys.iterator();
        while (it.hasNext()) {
            aux = (BigInteger) it.next();
            while (true) {
                if (ft[i].compareTo(aux) <= 0) {
                    ft[i] = aux;
                    i++;
                } else {
                    break;
                }
            }
        }
        if (i < m) {
            it = sortedKeys.iterator();
            while (it.hasNext()) {
                real = (BigInteger) it.next();
                aux = real.add(aux.pow(255));
                while (true) {
                    if (ft[i].compareTo(aux) <= 0) {
                        ft[i] = real;
                        i++;
                    } else {
                        break;
                    }
                }
            }
        }
    }

    private ServerCli getOwner(BigInteger reqHash) {
        List<BigInteger> sortedKeys = new ArrayList<>(map.keySet());
        Collections.sort(sortedKeys);
        Iterator it = sortedKeys.iterator();
        BigInteger aux = null;
        while(it.hasNext()){
            aux = (BigInteger) it.next();
        }
        if(reqHash.compareTo(aux) == 1){
            return map.get(aux);
        }
        aux = ft[0];
        for (int i = 0; i < m; i++) {
            if (ft[i].compareTo(reqHash) < 0) { //ultimo menor
                aux = ft[i];
            }
        }
        if (map.containsKey(aux)) {
            return map.get(aux);
        } else { //erro mapa de nós nao contem os nós da rede
            return null;
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException {
        final Servidor server = new Servidor();
        server.start();
        server.blockUntilShutdown();
    }

    static class ServerFuncImpl extends ServidorFuncGrpc.ServidorFuncImplBase {

        private final SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);
        private String dataFormatada;
        private String file;
        private final String saida;
        private File arquivo;

        private ServerFuncImpl(String saida) {
            formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));
            this.saida = saida;
        }

        @Override
        public void enviar(EnviarRequest req, StreamObserver<EnviarReply> responseObserver) {
            file = req.getNome();
            arquivo = new File(this.saida + File.separator + file);
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
            arquivo = new File(this.saida + File.separator);
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
                arquivo = new File(this.saida + File.separator + file);
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
            } catch (IOException e) {
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
