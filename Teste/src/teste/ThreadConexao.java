package teste;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ThreadConexao implements Runnable {

    private final Socket socket;
    private final String saida;
    private final int id;
    private String ret = "";
    private final Path pathTeste;
    private boolean end = false;
    private static final int NTESTES = 10;

    public ThreadConexao(Socket socket, String saida, int id, Path pathTeste) {
        this.socket = socket;
        this.saida = saida;
        this.id = id;
        this.pathTeste = pathTeste;
    }

    @Override
    public void run() {
        int[] l = new int[2];
        Arrays.fill(l,0);
        Requisicao requisicao;
        String rootDir = pathTeste.getParent() + "" + File.separator;
        String arqTeste = pathTeste.getFileName() + "";
        String file = id + "_0_" + arqTeste;
        try {
            File arq = new File(rootDir + file);
            if (!Files.exists(arq.toPath())) {
                Files.copy(pathTeste, arq.toPath());
            }
        } catch (IOException ex) {
            System.out.println(ex.toString());
        }
        SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);

        formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));
        String dataFormatada;
        File arquivo;
        Resposta resposta;
        String status;

        String acao = "Enviar";
        try {
            for (int i = 1; i <= ((NTESTES * 3) + 2); i++) {
                if (end) {
                    break;
                }
                if ((i-1) % 3 == 0 && i < (NTESTES*3)+1) {
                    file = id + "_" + ((int) (i / 3)) + "_" + arqTeste;
                    File arq = new File(rootDir + file);
                    if (!Files.exists(arq.toPath())) {
                        Files.copy(pathTeste, arq.toPath());
                    }
                }
                if (i == (NTESTES * 3)+1) {
                    acao = "Sair";
                }
                switch (acao) {
                    case "Enviar":
                        requisicao = new Requisicao();
                        dataFormatada = formatador.format(new Date()) + " GMT-3";
                        requisicao.setCabecalho("Date", dataFormatada);
                        requisicao.setCabecalho("Acao", "Enviar");
                        arquivo = new File(rootDir + file);
                        requisicao.setConteudoRequisicao(Files.readAllBytes(arquivo.toPath()));
                        requisicao.setCabecalho("Content-Length", "" + requisicao.getTamanhoRequisicao());
                        requisicao.setCabecalho("Arquivo", arquivo.getName());
                        requisicao.setSaida(socket.getOutputStream());
                        requisicao.enviar();
                        resposta = Resposta.lerEntrada(socket.getInputStream());
                        status = (String) resposta.getCabecalho().get("Status").get(0);
                        if (status.equals("ERRO")) {
                            ret = "erro_enviar";
                            end = true;
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
                        status = (String) resposta.getCabecalho().get("Status").get(0);
                        if (status.equals("ERRO")) {
                            ret = "erro_listar";
                            end = true;
                        }/*
                        List<String> lista = resposta.getCabecalho().get("Lista");
                        System.out.println("Arquivos no servidor:");
                        int j = 1;
                        if (lista.size() <= 1 && lista.get(0).equals("")) {
                            System.out.println("Nenhum arquivo encontrado!");
                        } else {
                            for (String nome : lista) {
                                System.out.println((j++) + " " + nome.trim());
                            }
                        }*/
                        break;
                    case "Receber":
                        requisicao = new Requisicao();
                        dataFormatada = formatador.format(new Date()) + " GMT-3";
                        requisicao.setCabecalho("Date", dataFormatada);
                        requisicao.setCabecalho("Acao", "Receber");
                        requisicao.setCabecalho("Content-Length", "0");
                        requisicao.setCabecalho("Arquivo", file);
                        requisicao.setSaida(socket.getOutputStream());
                        requisicao.enviar();
                        resposta = Resposta.lerEntrada(socket.getInputStream());
                        status = (String) resposta.getCabecalho().get("Status").get(0);
                        if (status.equals("OK")) {
                            arquivo = new File(saida + file);
                            Files.write(arquivo.toPath(), resposta.getConteudoResposta());
                        }else{
                            System.out.println(resposta.getCabecalho().get("Status").get(0));
                            System.out.println(file);
                            ret = "erro_receber";
                            end = true;
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
                        end = true;
                        break;
                    default:
                        break;
                }
                acao = getNext(acao);
            }
            end = true;
        } catch (Exception e) {
            System.out.println(e.toString());
            ret = "erro_nao_definido";
            end = true;
        }
    }

    private static String getNext(String acao) {
        switch (acao) {
            case "Enviar":
                return "Listar";
            case "Listar":
                return "Receber";
            case "Receber":
                return "Enviar";
            default:
                return "Sair";
        }
    }

    public String getRet() {
        return this.ret;
    }

    public boolean ended() {
        return this.end;
    }
}
