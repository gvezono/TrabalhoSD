package servidor;

import io.atomix.copycat.server.StateMachine;
import io.atomix.copycat.server.Commit;
import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.netty.NettyTransport;
import io.atomix.copycat.server.CopycatServer;
import io.atomix.copycat.server.storage.Storage;
import io.atomix.copycat.server.storage.StorageLevel;

import command.*;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Servidor extends StateMachine {

    private static final Logger logger = Logger.getLogger(Servidor.class.getName());

    private static final HashMap<BigInteger, ServerCli> map = new HashMap<BigInteger, ServerCli>();
    private static BigInteger[] ft, ftReal;
    //BigInteger = hash convertido para int, servercli = conexao com o servidor

    private static int porta;
    private static String ip;

    private static ServerCli c, prevCli;
    
    private static AtomixCli cli; //cliente do servidor de replicação

    private static String saida;

    private static String usedHash;

    //private final boolean restricao = false;
    private Server server;

    //ft
    private static int m; //128 (SHA-256)
    private static BigInteger p, prev; //hash ip+porta

    public Servidor() throws SocketException, UnknownHostException, NoSuchAlgorithmException {
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            Servidor.ip = socket.getLocalAddress().getHostAddress();
        }
        InputStream is = Servidor.class.getResourceAsStream("/app.properties");
        Properties prop = new Properties();
        try {
            prop.load(is);
            Servidor.porta = Integer.parseInt(prop.getProperty("porta"));
            saida = prop.getProperty("saida");
            usedHash = prop.getProperty("hash");
            MessageDigest digest = MessageDigest.getInstance(usedHash);
            digest.update((Servidor.ip + Servidor.porta).getBytes());
            byte[] hash = digest.digest();
            p = new BigInteger(hash);
            m = (hash.length * 4);
            String newip;
            int newport;
            if (prop.containsKey("servidor.ip") && prop.containsKey("servidor.porta")) {
                newip = prop.getProperty("servidor.ip");
                newport = Integer.parseInt(prop.getProperty("servidor.porta"));

                Servidor.c = new ServerCli(newip, newport);
                create();
                AddSvReply reply = c.entrar(Servidor.ip, Servidor.porta, Servidor.p.toString());
                if (!reply.getStatus().equals("OK")) {
                    prev = p;
                    prevCli = null;
                } else {
                    List<String> hosts = reply.getHostsList();
                    for (String host : hosts) {
                        String hip = host.split(":")[0];
                        int hporta = Integer.parseInt(host.split(":")[1]);
                        MessageDigest digestHost = MessageDigest.getInstance(usedHash);
                        digestHost.update((hip + hporta).getBytes());
                        BigInteger hashHost = new BigInteger(digest.digest());
                        Servidor.map.put(hashHost, new ServerCli(ip, porta));
                        Servidor.update();
                        Servidor.purgeUnused();
                    }
                }
            } else {
                prev = p;
                prevCli = null;
            }
        } catch (IOException | NumberFormatException e) {
            logger.log(Level.INFO, e.toString());
            Servidor.porta = 50051;
            Servidor.saida = "SERVIDOR";
            usedHash = "SHA-256";
            MessageDigest digest = MessageDigest.getInstance(usedHash);
            digest.update((Servidor.ip + Servidor.porta).getBytes());
            byte[] hash = digest.digest();
            p = new BigInteger(hash);
            m = (hash.length * 4);
            prev = p;
            prevCli = null;
        }
        Path dirsv = Paths.get(Servidor.saida);
        if (Files.notExists(dirsv)) {
            File file = new File(dirsv.toUri());
            file.mkdir();
        }
    }

    private void start() throws IOException {
        server = ServerBuilder.forPort(Servidor.porta)
                .addService(new ServerFuncImpl())
                .maxInboundMessageSize(Integer.MAX_VALUE)
                .build()
                .start();

        logger.log(Level.INFO, "Server started, listening on {0}", Servidor.porta);
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

    private static void create() {
        List<BigInteger> sortedKeys = new ArrayList<>(map.keySet());
        Collections.sort(sortedKeys);
        ft = new BigInteger[m];
        ftReal = new BigInteger[m];
        BigInteger suc, aux;
        int i;
        aux = new BigInteger("2");
        for (i = 0; i < m; i++) {
            suc = p.add(aux.pow(i)).mod(aux.pow(m));
            ftReal[i] = suc;
        }
        update();
    }

    private static void update() {
        if (map.isEmpty()) {
            ft[0] = Servidor.p;
            return;
        }
        int i = 0;
        BigInteger aux = null, real;
        List<BigInteger> sortedKeys = new ArrayList<>(map.keySet());
        Iterator it = sortedKeys.iterator();
        while (it.hasNext()) {
            aux = (BigInteger) it.next();
            while (true) {
                if (ftReal[i].compareTo(aux) <= 0) {
                    ft[i] = aux;
                    i++;
                } else {
                    break;
                }
            }
        }
        if ((i < m) && (aux != null)) {
            it = sortedKeys.iterator();
            while (it.hasNext()) {
                real = (BigInteger) it.next();
                aux = real.add(aux.pow((m - 1)));
                while (true) {
                    if (ftReal[i].compareTo(aux) <= 0) {
                        ft[i] = real;
                        i++;
                    } else {
                        break;
                    }
                }
            }
        }
    }

    private static ServerCli getOwner(BigInteger reqHash) {
        List<BigInteger> sortedKeys = new ArrayList<>(map.keySet());
        Collections.sort(sortedKeys);
        Iterator it = sortedKeys.iterator();
        BigInteger aux = null;
        while (it.hasNext()) {
            aux = (BigInteger) it.next();
        }
        if (reqHash.compareTo(aux) == 1) {
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

    public static void purgeUnused() {
        List<BigInteger> keys = new ArrayList<>(map.keySet());
        Iterator it = keys.iterator();
        BigInteger aux;
        while (it.hasNext()) {
            aux = (BigInteger) it.next();
            int ret = Arrays.binarySearch(ft, aux);
            if (ret < 0) {
                map.remove(aux);
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException {
        final Servidor server = new Servidor();
        server.start();
        server.blockUntilShutdown();

        int myId = Integer.parseInt(args[0]);
        List<Address> addresses = new LinkedList<>();

        for (int i = 1; i < args.length; i += 2) {
            Address address = new Address(args[i], Integer.parseInt(args[i + 1]));
            addresses.add(address);
        }

        CopycatServer.Builder builder = CopycatServer.builder(addresses.get(myId))
                .withStateMachine((Supplier<StateMachine>) server)
                .withTransport(NettyTransport.builder()
                        .withThreads(4)
                        .build())
                .withStorage(Storage.builder()
                        .withDirectory(new File("logs_" + myId)) //Must be unique
                        .withStorageLevel(StorageLevel.DISK)
                        .build());
        CopycatServer sv = builder.build();

        if (myId == 0) {
            sv.bootstrap().join();
        } else {
            sv.join(addresses).join();
        }
        
        Servidor.cli = new AtomixCli(addresses);
    }

    public static EnviarReply enviar(Commit<EnviarCommand> commit) {
        try {
            EnviarCommand ec = commit.operation();
            return null;
        } finally {
            commit.close();
        }
    }

    public static ListarReply listar(Commit<ListarQuery> commit) {
        try {
            ListarQuery lq = commit.operation();
            return null;
        } finally {
            commit.close();
        }
    }

    public static ReceberReply receber(Commit<ReceberQuery> commit) {
        try {
            ReceberQuery rq = commit.operation();
            return null;
        } finally {
            commit.close();
        }
    }

    public static AddSvReply adicionarServer(Commit<AdicionarServerCommand> commit) {
        try {
            AdicionarServerCommand asc = commit.operation();
            return null;
        } finally {
            commit.close();
        }
    }

    static class ServerFuncImpl extends ServidorFuncGrpc.ServidorFuncImplBase {

        private final SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);
        private String dataFormatada;
        private String file;
        private File arquivo;

        private static final List<BigInteger> transientids = new ArrayList<BigInteger>();

        private ServerFuncImpl() {
            formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));
            Servidor.create();
        }

        @Override
        public void enviar(EnviarRequest req, StreamObserver<EnviarReply> responseObserver) {
            file = req.getNome();
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance(Servidor.usedHash);
                digest.update((Servidor.ip + Servidor.porta).getBytes());
                BigInteger fileHash = new BigInteger(digest.digest());
                if (((fileHash.compareTo(Servidor.prev) > 1) && (fileHash.compareTo(Servidor.p) < 1)) || (Servidor.prev.compareTo(Servidor.p) == 0)) {
                    arquivo = new File(Servidor.saida + File.separator + file);
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
                } else {
                    ServerCli dest = Servidor.getOwner(fileHash);
                    if (dest != null) {
                        EnviarReply reply = dest.enviar(req);
                        responseObserver.onNext(reply);
                        responseObserver.onCompleted();
                    } else {
                        EnviarReply reply = EnviarReply.newBuilder()
                                .setData(dataFormatada)
                                .setNome(req.getNome())
                                .setStatus("ERROR")
                                .build();
                        responseObserver.onNext(reply);
                        responseObserver.onCompleted();
                    }
                }
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                EnviarReply reply = EnviarReply.newBuilder()
                        .setData(dataFormatada)
                        .setNome(req.getNome())
                        .setStatus("ERROR" + ex.getMessage())
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
            }
        }

        @Override
        public void listar(ListarRequest req, StreamObserver<ListarReply> responseObserver) {
            arquivo = new File(Servidor.saida + File.separator);
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
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance(Servidor.usedHash);
                digest.update((Servidor.ip + Servidor.porta).getBytes());
                BigInteger fileHash = new BigInteger(digest.digest());
                if (((fileHash.compareTo(Servidor.prev) > 1) && (fileHash.compareTo(Servidor.p) < 1)) || (Servidor.prev.compareTo(Servidor.p) == 0)) {
                    String erro = "";
                    ReceberReply.Builder reply = ReceberReply.newBuilder();
                    try {
                        arquivo = new File(Servidor.saida + File.separator + file);
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
                } else {
                    ServerCli dest = Servidor.getOwner(fileHash);
                    if (dest != null) {
                        ReceberReply reply = dest.receber(req);
                        responseObserver.onNext(reply);
                        responseObserver.onCompleted();
                    } else {
                        ReceberReply reply = ReceberReply.newBuilder()
                                .setStatus("ERROR")
                                .build();
                        responseObserver.onNext(reply);
                        responseObserver.onCompleted();
                    }
                }
            } catch (NoSuchAlgorithmException ex) {

            }
        }

        @Override
        public void adicionarServer(AddSvRequest req, StreamObserver<AddSvReply> responseObserver) {
            BigInteger hash = new BigInteger(req.getHash());
            String ip = req.getIp();
            int porta = req.getPorta();

            ServerCli newSv = new ServerCli(ip, porta);

            boolean control = req.getControl();
            int ret = Arrays.binarySearch(ftReal, hash);
            if (ret > 0) {
                Servidor.map.put(hash, newSv);
                Servidor.update();
            }
            if (control) {//caso seja o primeiro a ser contactado
                if (Servidor.ft[0].compareTo(Servidor.p) != 0) {
                    // anel ja existe
                    transientids.add(hash);

                    AddSvRequest.Builder build = req.toBuilder();
                    build = build.setControl(false);
                    AddSvReply aReply = Servidor.map.get(Servidor.ft[0]).repassar(build.build());
                    responseObserver.onNext(aReply.toBuilder().addHosts(Servidor.ip + ":" + Servidor.porta).build());
                } else {
                    // cria anel
                    Servidor.prevCli = newSv;
                    Servidor.prev = hash;
                    Servidor.prevCli.enviarArquivos(Servidor.p, Servidor.prev);
                    Servidor.map.put(Servidor.prev, Servidor.prevCli);
                    Servidor.update();
                    AddSvReply reply = AddSvReply.newBuilder()
                            .setStatus("Ok")
                            .addHosts(Servidor.ip + ":" + Servidor.porta)
                            .build();
                    responseObserver.onNext(reply);
                    Servidor.prevCli.enviarArquivos(Servidor.p, Servidor.prev);
                }
            } else {
                if (transientids.contains(hash)) {
                    // chegou primeiro que foi contactado
                    transientids.remove(hash);
                    AddSvReply reply = AddSvReply.newBuilder()
                            .setStatus("Ok")
                            .build();
                    responseObserver.onNext(reply);
                } else {
                    // repassa para os outros servidores do anel
                    if (Servidor.map.get(Servidor.ft[0]) == null) {
                        AddSvReply reply = AddSvReply.newBuilder()
                                .setStatus("Ok")
                                .build();
                        responseObserver.onNext(reply);
                    } else {
                        AddSvReply aReply = Servidor.map.get(Servidor.ft[0]).repassar(req);
                        responseObserver.onNext(aReply.toBuilder().addHosts(Servidor.ip + ":" + Servidor.porta).build());
                    }
                }
            }
            if ((hash.compareTo(Servidor.prev) > 0) && (hash.compareTo(Servidor.p) < 1)) {
                // caso seja o servidor que contem os arquivos do servidor ingressante
                Servidor.prevCli = newSv;
                Servidor.prev = hash;
                arquivo = new File(Servidor.saida + File.separator);
                MessageDigest digest;
                BigInteger aux;
                try {
                    String[] lista = arquivo.list();
                    for (String nome : lista) {
                        digest = MessageDigest.getInstance(usedHash);
                        digest.update(nome.getBytes());
                        aux = new BigInteger(digest.digest());
                        if (aux.compareTo(hash) < 1) {
                            try {
                                arquivo = new File(Servidor.saida + File.separator + nome);
                                byte[] arq = Files.readAllBytes(arquivo.toPath());
                                long controle = 0;
                                long size = arq.length;
                                int datalen = Integer.MAX_VALUE;
                                EnviarRequest.Builder eReq = EnviarRequest.newBuilder().setNome(nome);
                                while ((controle + datalen) < size) {
                                    eReq = eReq.addArquivo(ByteString.copyFrom(arq, (int) controle, datalen));
                                    controle += datalen;
                                }
                                eReq = eReq.setTamanho(size);
                                eReq = eReq.addArquivo(ByteString.copyFrom(arq, (int) controle, (int) (size - controle)));
                                dataFormatada = formatador.format(new Date()) + " GMT-3";
                                eReq = eReq.setData(dataFormatada);
                                EnviarReply reply = Servidor.prevCli.enviar(eReq.build());
                                if (reply.getStatus().equals("OK")) {
                                    Files.delete(arquivo.toPath());
                                }
                            } catch (IOException e) {
                                logger.log(Level.INFO, e.getMessage());
                            }
                        }
                    }
                } catch (NoSuchAlgorithmException ex) {
                    logger.log(Level.INFO, ex.getMessage());
                }
            }
            responseObserver.onCompleted();
        }
    }
}
