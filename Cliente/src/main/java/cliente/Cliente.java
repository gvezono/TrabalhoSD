package cliente;

import com.google.protobuf.ByteString;
import servidor.*;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.logging.Logger;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class Cliente {

    private static final Logger logger = Logger.getLogger(Cliente.class.getName());

    private final ManagedChannel channel;
    private final ServidorFuncGrpc.ServidorFuncBlockingStub blockingStub;

    public Cliente(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(Integer.MAX_VALUE)
                .build());
    }

    Cliente(ManagedChannel channel) {
        this.channel = channel;
        blockingStub = ServidorFuncGrpc.newBlockingStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public String Enviar(String data, String nome, byte[] arquivo) throws InvalidProtocolBufferException {
        EnviarRequest.Builder request = EnviarRequest.newBuilder()
                .setData(data)
                .setTamanho((long) arquivo.length)
                .setNome(nome);
        long control = 0;
        long size = arquivo.length;
        int datalen = Integer.MAX_VALUE;
        while ((control + datalen) < size) {
            request = request.addArquivo(ByteString.copyFrom(arquivo, (int) control, datalen));
            control += datalen;
        }
        request = request.addArquivo(ByteString.copyFrom(arquivo, (int) control, (int) (size - control)));
        EnviarReply response;
        try {
            response = blockingStub.enviar(request.build());
            return response.getStatus();
        } catch (StatusRuntimeException e) {
            return "RPC failed: " + e.getStatus();
        }
    }

    public String[] Listar() {
        ListarRequest request = ListarRequest.newBuilder().build();
        ListarReply response;
        try {
            response = blockingStub.listar(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return new String[]{""};
        }
        String[] res = new String[response.getNomeList().size()];
        Iterator it = response.getNomeList().iterator();
        int i = 0;
        while (it.hasNext()) {
            res[i] = (String) it.next();
            i++;
        }
        return res;
    }

    public Object[] Receber(String data, String nome) {
        ReceberRequest request = ReceberRequest.newBuilder()
                .setData(data)
                .setNome(nome)
                .build();
        ReceberReply response;
        try {
            response = blockingStub.receber(request);
        } catch (StatusRuntimeException e) {
            return new Object[]{"", "RPC failed: " + e.getStatus()};
        }
        int count = response.getArquivoCount();
        long control = 0;
        long size = response.getTamanho();
        byte[] arq = new byte[(int) size];
        for (int i = 0; i < count; i++) {
            byte[] aux = response.getArquivo(i).toByteArray();
            System.arraycopy(aux, 0, arq, (int) control, aux.length);
            control += aux.length;
        }
        return new Object[]{arq, response.getStatus()};
    }

    public static void main(String[] args) {
        try {
            Scanner sc = new Scanner(System.in);
            System.out.println("Digite o IP do servidor:");
            String ip = sc.nextLine().trim();
            int porta = 50051;
            Cliente c = new Cliente(ip, porta);
            String file;
            SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);
            formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));
            String dataFormatada;
            File arquivo;
            boolean conectado = true;
            System.out.println("Digite o caminho completo de saída dos arquivos:");
            String saida = sc.nextLine();
            saida += saida.trim().endsWith(File.separator) ? "" : File.separator;
            String status;

            Path dircli = Paths.get(saida);
            while (!Files.exists(dircli) || !Files.isDirectory(dircli)) {
                System.out.println("Pasta inexistente!");
                System.out.println("Digite o caminho completo de saída dos arquivos:");
                saida = sc.nextLine();
                saida += saida.trim().endsWith(File.separator) ? "" : File.separator;
                dircli = Paths.get(saida);
            }
            while (conectado) {
                printInterface();
                String acao = sc.nextLine();
                switch (acao) {
                    case "Enviar":
                        System.out.println("Digite o caminho completo do arquivo com o nome do arquivo:");
                        file = sc.nextLine().trim();
                        dataFormatada = formatador.format(new Date()) + " GMT-3";
                        arquivo = new File(file);
                        if (arquivo.exists()) {
                            try {
                                status = c.Enviar(dataFormatada, arquivo.getName(), Files.readAllBytes(arquivo.toPath()));
                            } catch (InvalidProtocolBufferException e) {
                                status = "Error: " + e.toString();
                            }
                            if (status.equals("OK")) {
                                System.out.println("Arquivo enviado com sucesso");
                                System.out.println("Pressione enter para continuar ...");
                                sc.nextLine();
                            } else {
                                logger.log(Level.WARNING, status);
                                System.out.println("Arquivo não pode ser enviado");
                                System.out.println("Pressione enter para continuar ...");
                                sc.nextLine();
                            }
                        } else {
                            System.out.println("Arquivo inexistente");
                            System.out.println("Pressione enter para continuar ...");
                            sc.nextLine();
                        }
                        break;

                    case "Listar":
                        String[] lista = c.Listar();
                        System.out.println("Arquivos no servidor:");
                        if (lista.length < 1 || lista[0].equals("")) {
                            System.out.println("Nenhum arquivo encontrado!");
                        } else {
                            int i = 1;
                            for (String nome : lista) {
                                System.out.println((i++) + " " + nome.trim());
                            }
                        }
                        System.out.println("Pressione enter para continuar ...");
                        sc.nextLine();
                        break;
                    case "Receber":
                        System.out.println("Digite o nome do arquivo:");
                        file = sc.nextLine().trim();
                        dataFormatada = formatador.format(new Date()) + " GMT-3";
                        Object[] res = c.Receber(dataFormatada, file);
                        status = (String) res[1];
                        if (status.equals("OK")) {
                            arquivo = new File(saida + file);
                            Files.write(arquivo.toPath(), (byte[]) res[0]);
                            System.out.println("Arquivo recebido");
                            System.out.println("Pressione enter para continuar ...");
                            sc.nextLine();
                        } else {
                            logger.log(Level.WARNING, (String) res[1]);
                            System.out.println("Pressione enter para continuar ...");
                            sc.nextLine();
                        }
                        break;
                    case "Sair":
                        conectado = false;
                        break;
                    case "Alterar":
                        System.out.println("Digite o caminho completo de saída dos arquivos:");
                        saida = sc.nextLine();
                        saida += saida.trim().endsWith(File.separator) ? "" : File.separator;
                        dircli = Paths.get(saida);
                        while (!Files.exists(dircli) || !Files.isDirectory(dircli)) {
                            System.out.println("Pasta inexistente!");
                            System.out.println("Digite o caminho completo de saída dos arquivos:");
                            saida = sc.nextLine();
                            saida += saida.trim().endsWith(File.separator) ? "" : File.separator;
                            dircli = Paths.get(saida);
                        }
                        System.out.println("Caminho de saída alterado, pressione enter para continuar ...");
                    default:
                        break;
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(Cliente.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void printInterface() {
        for (int i = 0; i < 30; i++) {
            System.out.println();
        }
        System.out.println("Enviar \t\tEnvia um arquivo para o servidor");
        System.out.println("Listar  \tLista os arquivos do servidor");
        System.out.println("Receber \tPega um arquivo do servidor");
        System.out.println("Sair \t\tSair do programa");
        System.out.println("Alterar \tAltera o caminho de saída dos arquivos recebidos");
        System.out.println("Digite sua ação:");
    }
}
