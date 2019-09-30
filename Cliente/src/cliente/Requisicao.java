package cliente;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Requisicao {

    private Map<String, List> cabecalhos;
    private OutputStream saida;
    private byte[] conteudoRequisicao;
    private long tamanhoRequisicao;

    public void enviar() throws IOException {
        //escreve o headers em bytes
        saida.write(montaCabecalho());
        if(conteudoRequisicao != null)
            saida.write(conteudoRequisicao);
        //encerra a resposta
        saida.flush();
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

    public void setCabecalho(String chave, String... valores) {
        if (cabecalhos == null) {
            cabecalhos = new TreeMap<>();
        }
        cabecalhos.put(chave, Arrays.asList(valores));
    }

    public Map<String, List> getCabecalhos() {
        return cabecalhos;
    }

    public OutputStream getSaida() {
        return saida;
    }

    public void setSaida(OutputStream saida) {
        this.saida = saida;
    }

    public byte[] getConteudoRequisicao() {
        return conteudoRequisicao;
    }

    public void setConteudoRequisicao(byte[] conteudoRequisicao) {
        this.conteudoRequisicao = conteudoRequisicao;
        this.tamanhoRequisicao = this.conteudoRequisicao.length;
    }

    public long getTamanhoRequisicao() {
        return tamanhoRequisicao;
    }

}
