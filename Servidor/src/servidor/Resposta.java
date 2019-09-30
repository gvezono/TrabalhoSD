package servidor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Resposta {

    private byte[] conteudoResposta;
    private Map<String, List> cabecalhos;
    private OutputStream saida;

    public Resposta() {

    }

    public void enviar() throws IOException {
        //escreve o headers em bytes
        saida.write(montaCabecalho());
        //escreve o conteudo em bytes
        if(conteudoResposta != null)
            saida.write(conteudoResposta);
        //encerra a resposta
        saida.flush();
    }

    public void setCabecalho(String chave, String... valores) {
        if (cabecalhos == null) {
            cabecalhos = new TreeMap<>();
        }
        cabecalhos.put(chave, Arrays.asList(valores));
    }

    public String getTamanhoResposta() {
        if(conteudoResposta == null)
            return "0";
        return getConteudoResposta().length + "";
    }

    private byte[] montaCabecalho() {
        return this.toString().getBytes();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        for (Map.Entry<String, List> entry : cabecalhos.entrySet()) {
            str.append(entry.getKey());
            String stringCorrigida = Arrays.toString(entry.getValue().toArray()).replace("[", "").replace("]", "");
            str.append(": ").append(stringCorrigida).append("\r\n");
        }
        str.append("\r\n");
        return str.toString();
    }

    public byte[] getConteudoResposta() {
        return conteudoResposta;
    }

    public void setConteudoResposta(byte[] conteudoResposta) {
        this.conteudoResposta = conteudoResposta;
    }

    public void setSaida(OutputStream saida) {
        this.saida = saida;
    }
}
