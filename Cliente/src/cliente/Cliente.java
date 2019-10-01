package cliente;

import java.util.logging.Logger;
import java.io.File;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.logging.Level;

public class Cliente {

    public static void main(String[] args) {
        try {
            Scanner sc = new Scanner(System.in);
            System.out.println("Digite o IP do servidor:");
            String ip = sc.nextLine().trim();
            int porta = 5566;
            Socket socket = new Socket(ip, porta);
            Requisicao requisicao;
            String file;
            SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);
            formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));
            String dataFormatada;
            File arquivo;
            boolean conectado = true;
            System.out.println("Digite o caminho completo de saída dos arquivos:");
            String saida = sc.nextLine();
            saida += saida.trim().endsWith(File.separator) ? "" : File.separator;
            Resposta resposta;
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
                        requisicao = new Requisicao();
                        dataFormatada = formatador.format(new Date()) + " GMT-3";
                        requisicao.setCabecalho("Date", dataFormatada);
                        requisicao.setCabecalho("Acao", "Enviar");
                        arquivo = new File(file);
                        requisicao.setConteudoRequisicao(Files.readAllBytes(arquivo.toPath()));
                        requisicao.setCabecalho("Content-Length", "" + requisicao.getTamanhoRequisicao());
                        requisicao.setCabecalho("Arquivo", arquivo.getName());
                        requisicao.setSaida(socket.getOutputStream());
                        requisicao.enviar();
                        resposta = Resposta.lerEntrada(socket.getInputStream());
                        String status = (String) resposta.getCabecalho().get("Status").get(0);
                        if (status.equals("OK")) {
                            System.out.println("Arquivo enviado com sucesso");
                            System.out.println("Pressione enter para continuar ...");
                            sc.nextLine();
                        } else {
                            System.out.println("Arquivo não pode ser enviado");
                            System.out.println("Pressione enter para continuar ...");
                            sc.nextLine();
                        }
                        break;
                    case "Listar":
                        requisicao = new Requisicao();
                        dataFormatada = formatador.format(new Date()) + " GMT-3";
                        requisicao.setCabecalho("Date", dataFormatada);
                        requisicao.setCabecalho("Acao", "Listar");
                        requisicao.setCabecalho("Content-Length", "0");
                        requisicao.setCabecalho("Arquivo", "");
                        requisicao.setSaida(socket.getOutputStream());
                        requisicao.enviar();
                        resposta = Resposta.lerEntrada(socket.getInputStream());
                        List<String> lista = resposta.getCabecalho().get("Lista");
                        System.out.println("Arquivos no servidor:");
                        int i = 1;
                        if (lista.size() <= 1 && lista.get(0).equals("")) {
                            System.out.println("Nenhum arquivo encontrado!");
                        } else {
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
                        requisicao = new Requisicao();
                        dataFormatada = formatador.format(new Date()) + " GMT-3";
                        requisicao.setCabecalho("Date", dataFormatada);
                        requisicao.setCabecalho("Acao", "Receber");
                        requisicao.setCabecalho("Content-Length", "0");
                        requisicao.setCabecalho("Arquivo", file);
                        requisicao.setSaida(socket.getOutputStream());
                        requisicao.enviar();
                        resposta = Resposta.lerEntrada(socket.getInputStream());
                        if (resposta.getCabecalho().get("Status").get(0).equals("OK")) {
                            arquivo = new File(saida + file);
                            Files.write(arquivo.toPath(), resposta.getConteudoResposta());
                            System.out.println("Arquivo recebido");
                            System.out.println("Pressione enter para continuar ...");
                            sc.nextLine();
                        } else {
                            Iterator it = resposta.getCabecalho().get("Status").iterator();
                            while (it.hasNext()) {
                                System.out.println((String) it.next());
                            }
                            System.out.println("Pressione enter para continuar ...");
                            sc.nextLine();
                        }
                        break;
                    case "Sair":
                        requisicao = new Requisicao();
                        dataFormatada = formatador.format(new Date()) + " GMT-3";
                        requisicao.setCabecalho("Date", dataFormatada);
                        requisicao.setCabecalho("Acao", "Sair");
                        requisicao.setCabecalho("Content-Length", "0");
                        requisicao.setSaida(socket.getOutputStream());
                        requisicao.enviar();
                        socket.close();
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
            Logger.getLogger(Cliente.class.getName()).log(Level.SEVERE, null, ex);
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
