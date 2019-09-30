package servidor;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ThreadConexao implements Runnable {

    private final Socket socket;

    public ThreadConexao(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        boolean conn = true;
        while (conn) {
            if (socket.isClosed()) {
                conn = false;
                break;
            }
            try {
                Path dirsv = Paths.get("SERVIDOR");
                if(Files.notExists(dirsv)){
                    File file = new File(dirsv.toUri());
                    file.mkdir();
                }
                String erro = "";
                Requisicao requisicao = Requisicao.lerRequisicao(socket.getInputStream());
                String acao;
                if (requisicao.getCabecalhos().containsKey("Acao")) {
                    acao = (String) requisicao.getCabecalhos().get("Acao").get(0);
                } else {
                    acao = "ERRO";
                    erro = "Requisição inválida";
                }
                String file;
                File arquivo;
                Resposta resposta;
                SimpleDateFormat formatador = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss", Locale.ENGLISH);
                formatador.setTimeZone(TimeZone.getTimeZone("GMT-3"));
                String dataFormatada;
                switch (acao) {
                    case "Enviar":
                        if (requisicao.getCabecalhos().containsKey("Arquivo")) {
                            file = (String) requisicao.getCabecalhos().get("Arquivo").get(0);
                            arquivo = new File("SERVIDOR" + File.separator + file);
                        } else {
                            erro = "Requisição inválida";
                            file = "erro";
                            arquivo = new File(file); //não será enviado/lido
                        }
                        if (erro.equals("")) {
                            try {
                                Files.write(arquivo.toPath(), requisicao.getConteudoRequisicao());
                            } catch (Exception e) {
                                erro = e.getClass().getSimpleName();
                            }
                        }
                        resposta = new Resposta();
                        dataFormatada = formatador.format(new Date()) + " GMT-3";
                        resposta.setCabecalho("Date", dataFormatada);
                        resposta.setCabecalho("Content-Length", resposta.getTamanhoResposta());
                        resposta.setCabecalho("Arquivo", file);
                        if (erro.equals("")) {
                            resposta.setCabecalho("Status", "OK");
                        } else {
                            resposta.setCabecalho("Status", "ERRO");
                            resposta.setCabecalho("Status", erro);
                        }
                        resposta.setSaida(socket.getOutputStream());
                        resposta.enviar();
                        break;
                    case "Listar":
                        /*
                        if (requisicao.getCabecalhos().containsKey("Arquivo")) {
                            file = (String) requisicao.getCabecalhos().get("Arquivo").get(0);
                        } else {
                            erro = "Requisição inválida";
                            file = "erro";
                        }*/
                        resposta = new Resposta();
                        dataFormatada = formatador.format(new Date()) + " GMT-3";
                        resposta.setCabecalho("Date", dataFormatada);
                        resposta.setCabecalho("Content-Length", "0");
                        arquivo = new File("SERVIDOR" + File.separator);
                        String[] lista = arquivo.list(/*file*/);
                        if (lista == null) {
                            lista = new String[1];
                            erro = "IOException";
                        }
                        if (erro.equals("")) {
                            resposta.setCabecalho("Status", "OK");
                        } else {
                            resposta.setCabecalho("Status", "ERRO");
                            resposta.setCabecalho("Status", erro);
                        }
                        resposta.setCabecalho("Lista", lista);
                        resposta.setSaida(socket.getOutputStream());
                        resposta.enviar();
                        break;
                    case "Receber":
                        if (requisicao.getCabecalhos().containsKey("Arquivo")) {
                            file = (String) requisicao.getCabecalhos().get("Arquivo").get(0);
                        } else {
                            erro = "Requisição inválida";
                            file = "erro";
                        }
                        resposta = new Resposta();
                        if (erro.equals("")) {
                            try {
                                arquivo = new File("SERVIDOR" + File.separator + file);
                                resposta.setConteudoResposta(Files.readAllBytes(arquivo.toPath()));
                            } catch (Exception e) {
                                erro = e.getClass().getSimpleName();
                            }
                        }
                        dataFormatada = formatador.format(new Date()) + " GMT-3";
                        resposta.setCabecalho("Date", dataFormatada);
                        if (erro.equals("")) {
                            resposta.setCabecalho("Content-Length", resposta.getTamanhoResposta());
                            resposta.setCabecalho("Status", "OK");
                        } else {
                            resposta.setCabecalho("Content-Length", "0");
                            resposta.setCabecalho("Status", "ERRO");
                            resposta.setCabecalho("Status", erro);
                        }
                        resposta.setCabecalho("Arquivo", file);
                        resposta.setSaida(socket.getOutputStream());
                        resposta.enviar();
                        break;
                    case "Sair":
                        if (!socket.isClosed()) {
                            socket.close();
                            conn = false;
                        }
                        break;
                    case "ERRO":
                        resposta = new Resposta();
                        dataFormatada = formatador.format(new Date()) + " GMT-3";
                        resposta.setCabecalho("Date", dataFormatada);
                        resposta.setCabecalho("Arquivo", "");
                        resposta.setCabecalho("Content-Length", "0");
                        resposta.setCabecalho("Status", "ERRO");
                        resposta.setCabecalho("Status", erro);
                        resposta.setSaida(socket.getOutputStream());
                        resposta.enviar();
                        break;
                    default:
                        break;
                }
            } catch (IOException ex) {
                Logger.getLogger(ThreadConexao.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (!socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ex) {
                Logger.getLogger(ThreadConexao.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
