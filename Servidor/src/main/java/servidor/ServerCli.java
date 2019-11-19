package servidor;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
}
