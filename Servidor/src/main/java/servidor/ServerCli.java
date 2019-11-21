package servidor;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

class ServerCli {
    private static final Logger logger = Logger.getLogger(ServerCli.class.getName());

    private String host;
    private int porta;
    
    private final ManagedChannel channel;
    private final ServidorFuncGrpc.ServidorFuncBlockingStub blockingStub;

    public ServerCli(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(Integer.MAX_VALUE)
                .build());
        this.host = host;
        this.porta = port;
    }

    ServerCli(ManagedChannel channel) {
        this.channel = channel;
        blockingStub = ServidorFuncGrpc.newBlockingStub(channel);
    }
    
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    
    public AddSvReply entrar(String ip, int porta, String hash){
        AddSvRequest req = AddSvRequest.newBuilder()
                .setIp(ip)
                .setPorta(porta)
                .setHash(hash)
                .build();
        return repassar(req);
    }
    
    public AddSvReply repassar(AddSvRequest req){
        return blockingStub.adicionarServer(req);
    }
    
    public PingReply ping(String ip, int porta, String hash){
        PingRequest req = PingRequest.newBuilder()
                .setIp(ip)
                .setPorta(porta)
                .setHash(hash)
                .build();
        return blockingStub.ping(req);
    }
    
    public EnviarReply enviar(EnviarRequest req){
        return blockingStub.enviar(req);
    }
    
    public ReceberReply receber(ReceberRequest req){
        return blockingStub.receber(req);
    }
    
    public void sair(){
        
    }
    
    public void enviarArquivos(BigInteger start, BigInteger end){
        
    }
    
    public void receberArquivos(){
        
    }
}
