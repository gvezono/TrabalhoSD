package servidor;

import command.AdicionarServerCommand;
import command.EnviarCommand;
import command.ListarQuery;
import command.ReceberQuery;

import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.netty.NettyTransport;
import io.atomix.copycat.client.CopycatClient;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class AtomixCli {

    private final List<Address> addresses = new LinkedList<>();

    private final CopycatClient.Builder builder;
    private final CopycatClient client;
    private final CompletableFuture<CopycatClient> future;

    public AtomixCli(String[] args) {
        builder = CopycatClient.builder()
                .withTransport(NettyTransport.builder()
                        .withThreads(4)
                        .build());

        client = builder.build();

        for (int i = 0; i < args.length; i += 2) {
            Address address = new Address(args[i], Integer.parseInt(args[i + 1]));
            addresses.add(address);
        }

        future = client.connect(addresses);
        future.join();
    }

    public EnviarReply enviar(EnviarRequest req) throws InterruptedException, ExecutionException {
        CompletableFuture fut = client.submit(new EnviarCommand(req));
        return (EnviarReply) fut.get();
    }

    public ListarReply listar(ListarRequest req) throws InterruptedException, ExecutionException {
        CompletableFuture fut = client.submit(new ListarQuery(req));
        return (ListarReply) fut.get();
    }

    public ReceberReply receber(ReceberRequest req) throws InterruptedException, ExecutionException {
        CompletableFuture fut = client.submit(new ReceberQuery(req));
        return (ReceberReply) fut.get();
    }

    public AddSvReply adicionarServer(AddSvRequest req) throws InterruptedException, ExecutionException {
        CompletableFuture fut = client.submit(new AdicionarServerCommand(req));
        return (AddSvReply) fut.get();
    }
    
}
