package servidor;

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

public class Requisicao {

    private Map<String, List> cabecalhos;
    private byte[] conteudoRequisicao;

    private static byte[] toBytes(char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = Charset.forName("ISO-8859-1").encode(charBuffer);
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(),
                byteBuffer.position(), byteBuffer.limit());
        Arrays.fill(byteBuffer.array(), (byte) 0);
        return bytes;
    }

    public static Requisicao lerRequisicao(InputStream entrada) throws IOException {
        Requisicao requisicao = new Requisicao();
        BufferedReader buffer = new BufferedReader(new InputStreamReader(entrada, "ISO-8859-1"));
        Logger.getLogger(Requisicao.class.getName()).log(Level.INFO, "Requisição: ");
        String dadosHeader = buffer.readLine();
        while (dadosHeader != null && !dadosHeader.isEmpty()) {
            Logger.getLogger(Requisicao.class.getName()).log(Level.INFO, dadosHeader);
            String[] linhaCabecalho = dadosHeader.split(":");
            requisicao.setCabecalho(linhaCabecalho[0], linhaCabecalho[1].trim().split(","));
            dadosHeader = buffer.readLine();
        }
        String length = (String) requisicao.getCabecalhos().get("Content-Length").get(0);
        long len = Long.parseLong(length);
        if (len > 0) {
            long control = 0;
            char[] conteudo = new char[(int) len];
            while (control < len) {
                control += buffer.read(conteudo, (int) control, (int) (len - control));
            }
            requisicao.setConteudoRequisicao(toBytes(conteudo));
        }
        return requisicao;
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

    public byte[] getConteudoRequisicao() {
        return conteudoRequisicao;
    }

    public void setConteudoRequisicao(byte[] conteudoRequisicao) {
        this.conteudoRequisicao = conteudoRequisicao;
    }
}
