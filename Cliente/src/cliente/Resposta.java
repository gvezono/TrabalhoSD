package cliente;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Resposta {

    private byte[] conteudoResposta;
    private Map<String, List> cabecalhos;

    public Resposta() {

    }

    public void setCabecalho(String chave, String... valores) {
        if (cabecalhos == null) {
            cabecalhos = new TreeMap<>();
        }
        cabecalhos.put(chave, Arrays.asList(valores));
    }

    private static byte[] toBytes(char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = Charset.forName("ISO-8859-1").encode(charBuffer);
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(),
                byteBuffer.position(), byteBuffer.limit());
        Arrays.fill(byteBuffer.array(), (byte) 0);
        return bytes;
    }

    public static Resposta lerEntrada(InputStream entrada) throws IOException {
        Resposta resposta = new Resposta();
        BufferedReader buffer = new BufferedReader(new InputStreamReader(entrada, "ISO-8859-1"));
        Logger.getLogger(Resposta.class.getName()).log(Level.INFO, "Requisição: ");
        String dadosHeader = buffer.readLine();
        while (dadosHeader != null && !dadosHeader.isEmpty()) {
            Logger.getLogger(Resposta.class.getName()).log(Level.INFO, dadosHeader);
            String[] linhaCabecalho = dadosHeader.split(":");
            resposta.setCabecalho(linhaCabecalho[0], linhaCabecalho[1].trim().split(","));
            dadosHeader = buffer.readLine();
        }
        String length = "0";
        if (resposta.getCabecalho().containsKey("Content-Length")) {
            length = (String) resposta.getCabecalho().get("Content-Length").get(0);
        }
        long len = Long.parseLong(length);
        char[] conteudo = new char[(int) len];
        long control = 0;
        while (control < len) {
            control += buffer.read(conteudo, (int) control, (int) (len - control));
        }
        resposta.setConteudoResposta(toBytes(conteudo));

        return resposta;
    }

    public byte[] getConteudoResposta() {
        return conteudoResposta;
    }

    public void setConteudoResposta(byte[] conteudoResposta) {
        this.conteudoResposta = conteudoResposta;
    }

    public Map<String, List> getCabecalho() {
        return this.cabecalhos;
    }
}
