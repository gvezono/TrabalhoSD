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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Servidor extends StateMachine {

    private static final Logger logger = Logger.getLogger(Servidor.class.getName());

    private static final HashMap<BigInteger, ServerCli> map = new HashMap<BigInteger, ServerCli>();
    private static BigInteger[] ft, ftReal;
    private static final List<BigInteger> transientids = new ArrayList<BigInteger>();
    
    private static List<Address> addresses;
    //BigInteger = hash convertido para int, servercli = conexao com o servidor

    private static int porta, porta_hash, myId;
    private static String ip;

    private static ServerCli c, prevCli;

    private static AtomixCli cli = null; //cliente do servidor de replicação

    private static String saida;

    private static String usedHash;

    //private final boolean restricao = false;
    private static Server server;

    //ft
    private static int m; //128 (SHA-256)
    private static BigInteger p, prev; //hash ip+porta

    public Servidor() {
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            Servidor.ip = socket.getLocalAddress().getHostAddress();
        } catch (Exception ex) {

        }
        InputStream is = Servidor.class.getResourceAsStream("/app.properties");
        Properties prop = new Properties();
        try {
            System.out.println(Servidor.class.getResource("/app.properties"));
            prop.load(new FileInputStream("/app.properties"));
            Servidor.porta = Integer.parseInt(prop.getProperty("porta"));
            saida = prop.getProperty("saida");
            usedHash = prop.getProperty("hash");
            MessageDigest digest = MessageDigest.getInstance(usedHash);
            digest.update((Servidor.ip + Servidor.porta_hash).getBytes());
            byte[] hash = digest.digest();
            p = new BigInteger(hash);
            m = (hash.length * 4);
            String newip;
            int newport;
            if (prop.containsKey("servidor.ip") && prop.containsKey("servidor.porta") && Servidor.myId == 0) {
                newip = prop.getProperty("servidor.ip");
                newport = Integer.parseInt(prop.getProperty("servidor.porta"));

                Servidor.c = new ServerCli(newip, newport);
                create();
                AddSvReply reply = c.entrar(Servidor.ip, Servidor.porta_hash, Servidor.p.toString());
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
            try {
                MessageDigest digest = MessageDigest.getInstance(usedHash);

                digest.update((Servidor.ip + Servidor.porta_hash).getBytes());
                byte[] hash = digest.digest();

                p = new BigInteger(hash);
                m = (hash.length * 4);
                prev = p;
                prevCli = null;
            } catch (Exception ex) {
                //F
            }
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
        }
        Path dirsv = Paths.get(Servidor.saida);
        if (Files.notExists(dirsv)) {
            File file = new File(dirsv.toUri());
            file.mkdir();
        }
    }

    private static void start() throws IOException {
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
                Servidor.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    private static void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private static void blockUntilShutdown() throws InterruptedException {
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

        Servidor.addresses = new LinkedList<>();

        for (int i = 1; i < args.length; i += 2) {
            Address address = new Address(args[i], Integer.parseInt(args[i + 1]));
            Servidor.addresses.add(address);
        }

        Servidor.porta_hash = Integer.parseInt(args[2]);
        Servidor.myId = Integer.parseInt(args[0]);

        //final Servidor server = new Servidor();
        //server.start();
        CopycatServer.Builder builder = CopycatServer.builder(Servidor.addresses.get(Servidor.myId))
                .withStateMachine(Servidor::new)
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
        Servidor.start();
        Servidor.blockUntilShutdown();
    }

    public static EnviarReply enviar(Commit<EnviarCommand> commit) {
        try {
            EnviarRequest req = commit.operation().req;
            String file = req.getNome();
            String dataFormatada;
            File arquivo;
            SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);
            formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));
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
                    return reply;
                } else {
                    ServerCli dest = Servidor.getOwner(fileHash);
                    if (dest != null) {
                        EnviarReply reply = dest.enviar(req);
                        return reply;
                    } else {
                        dataFormatada = formatador.format(new Date()) + " GMT-3";
                        EnviarReply reply = EnviarReply.newBuilder()
                                .setData(dataFormatada)
                                .setNome(req.getNome())
                                .setStatus("ERROR")
                                .build();
                        return reply;
                    }
                }
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                dataFormatada = formatador.format(new Date()) + " GMT-3";
                EnviarReply reply = EnviarReply.newBuilder()
                        .setData(dataFormatada)
                        .setNome(req.getNome())
                        .setStatus("ERROR" + ex.getMessage())
                        .build();
                return reply;
            }
        } finally {
            commit.close();
        }
    }

    public static ListarReply listar(Commit<ListarQuery> commit) {
        try {
            ListarRequest req = commit.operation().req;
            String dataFormatada;
            File arquivo;
            SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);
            formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));

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
            return reply.build();
        } finally {
            commit.close();
        }
    }

    public static ReceberReply receber(Commit<ReceberQuery> commit) {
        try {
            ReceberRequest req = commit.operation().req;
            String dataFormatada;
            File arquivo;
            SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);
            formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));
            String file = req.getNome();
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
                    return reply.build();
                } else {
                    ServerCli dest = Servidor.getOwner(fileHash);
                    if (dest != null) {
                        ReceberReply reply = dest.receber(req);
                        return reply;
                    } else {
                        ReceberReply reply = ReceberReply.newBuilder()
                                .setStatus("ERROR")
                                .build();
                        return reply;
                    }
                }
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                ReceberReply reply = ReceberReply.newBuilder()
                        .setStatus("ERROR " + ex.getMessage())
                        .build();
                return reply;
            }
        } finally {
            commit.close();
        }
    }

    public static AddSvReply adicionarServer(Commit<AdicionarServerCommand> commit) {
        try {
            AddSvRequest req = commit.operation().req;
            AddSvReply reply;
            String dataFormatada;
            File arquivo;
            SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);
            formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));
            BigInteger hash = new BigInteger(req.getHash());
            String ipl = req.getIp();
            int portal = req.getPorta();

            ServerCli newSv = new ServerCli(ipl, portal);

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
                    reply = aReply.toBuilder().addHosts(Servidor.ip + ":" + Servidor.porta).build();
                } else {
                    // cria anel
                    Servidor.prevCli = newSv;
                    Servidor.prev = hash;
                    Servidor.prevCli.enviarArquivos(Servidor.p, Servidor.prev);
                    Servidor.map.put(Servidor.prev, Servidor.prevCli);
                    Servidor.update();

                    Servidor.prevCli.enviarArquivos(Servidor.p, Servidor.prev);

                    reply = AddSvReply.newBuilder()
                            .setStatus("Ok")
                            .addHosts(Servidor.ip + ":" + Servidor.porta)
                            .build();
                }
            } else {
                if (transientids.contains(hash)) {
                    // chegou primeiro que foi contactado
                    transientids.remove(hash);
                    reply = AddSvReply.newBuilder()
                            .setStatus("Ok")
                            .build();
                } else {
                    // repassa para os outros servidores do anel
                    if (Servidor.map.get(Servidor.ft[0]) == null) {
                        reply = AddSvReply.newBuilder()
                                .setStatus("Ok")
                                .build();
                    } else {
                        AddSvReply aReply = Servidor.map.get(Servidor.ft[0]).repassar(req);
                        reply = aReply.toBuilder().addHosts(Servidor.ip + ":" + Servidor.porta).build();
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
                                EnviarReply rp = Servidor.prevCli.enviar(eReq.build());
                                if (rp.getStatus().equals("OK")) {
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
            return reply;
        } finally {
            commit.close();
        }
    }

    static class ServerFuncImpl extends ServidorFuncGrpc.ServidorFuncImplBase {

        private ServerFuncImpl() {
            Servidor.create();
            Servidor.cli = new AtomixCli(Servidor.addresses);
        }

        @Override
        public void enviar(EnviarRequest req, StreamObserver<EnviarReply> responseObserver) {
            try {
                responseObserver.onNext(Servidor.cli.enviar(req));
                responseObserver.onCompleted();
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);
                formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));
                String dataFormatada = formatador.format(new Date()) + " GMT-3";
                EnviarReply reply = EnviarReply.newBuilder()
                        .setData(dataFormatada)
                        .setNome(req.getNome())
                        .setStatus("ERROR " + ex.getMessage())
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
            }
        }

        @Override
        public void listar(ListarRequest req, StreamObserver<ListarReply> responseObserver) {
            try {
                responseObserver.onNext(Servidor.cli.listar(req));
                responseObserver.onCompleted();
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);
                formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));
                String dataFormatada = formatador.format(new Date()) + " GMT-3";
                ListarReply reply = ListarReply.newBuilder()
                        .setData(dataFormatada)
                        .setStatus("ERROR " + ex.getMessage())
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
            }
        }

        @Override
        public void receber(ReceberRequest req, StreamObserver<ReceberReply> responseObserver) {
            try {
                responseObserver.onNext(Servidor.cli.receber(req));
                responseObserver.onCompleted();
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);
                formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));
                String dataFormatada = formatador.format(new Date()) + " GMT-3";
                ReceberReply reply = ReceberReply.newBuilder()
                        .setData(dataFormatada)
                        .setStatus("ERROR " + ex.getMessage())
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
            }
        }

        @Override
        public void adicionarServer(AddSvRequest req, StreamObserver<AddSvReply> responseObserver) {
            try {
                responseObserver.onNext(Servidor.cli.adicionarServer(req));
                responseObserver.onCompleted();
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                AddSvReply reply = AddSvReply.newBuilder()
                        .setStatus("ERROR " + ex.getMessage())
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
            }
        }
    }
}
