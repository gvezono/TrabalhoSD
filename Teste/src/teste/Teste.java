package teste;

import java.util.logging.Logger;
import java.io.File;
import static java.lang.Thread.sleep;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class Teste {

    private static final int NTHREADS = 100;

    public static void main(String[] args) {
        try {
            Scanner sc = new Scanner(System.in);
            System.out.println("Digite o IP do servidor:");
            String ip = sc.nextLine().trim();
            int porta = 5566;

            System.out.println("Digite o caminho completo de saída dos arquivos:");
            String saida = sc.nextLine();

            saida += saida.trim().endsWith(File.separator) ? "" : File.separator;
            Path dircli = Paths.get(saida);
            while (!Files.exists(dircli) || !Files.isDirectory(dircli)) {
                System.out.println("Pasta inexistente!");
                System.out.println("Digite o caminho completo de saída dos arquivos:");
                saida = sc.nextLine();
                saida += saida.trim().endsWith(File.separator) ? "" : File.separator;
                dircli = Paths.get(saida);
            }

            System.out.println("Digite o caminho completo do arquivo de testes:");
            String arqTeste = sc.nextLine().trim();
            Path pathTeste = new File(arqTeste).toPath();
            
            while (!Files.isReadable(pathTeste) || Files.isDirectory(pathTeste)) {
                System.out.println("Arquivo inexistente ou não pode ser lido!");
                System.out.println("Digite o caminho completo do arquivo de testes:");
                arqTeste = sc.nextLine().trim();
                pathTeste = Paths.get(arqTeste);
            }
            
            ExecutorService pool = Executors.newCachedThreadPool();
            ThreadConexao[] t = new ThreadConexao[NTHREADS];
            for (int i = 0; i < NTHREADS; i++) {
                t[i] = new ThreadConexao(new Socket(ip, porta), saida, i, pathTeste);
                pool.execute(t[i]);
            }
            int[] results = new int[5];
            Arrays.fill(results, 0);
            for (int i = 0; i < NTHREADS; i++) {
                while (!t[i].ended()) {
                    sleep(1);
                }
                switch (t[i].getRet()) {
                    case "erro_enviar":
                        results[1]++;
                        break;
                    case "erro_listar":
                        results[2]++;
                        break;
                    case "erro_receber":
                        results[3]++;
                        break;
                    case "erro_nao_definido":
                        results[4]++;
                        break;
                    default:
                        results[0]++;
                        break;
                }
            }
            System.out.println("Total:");
            System.out.println(results[0]+"/"+NTHREADS+"\t OK");
            System.out.println(results[1]+"/"+NTHREADS+"\t ERRO_ENVIAR");
            System.out.println(results[2]+"/"+NTHREADS+"\t ERRO_LISTAR");
            System.out.println(results[3]+"/"+NTHREADS+"\t ERRO_RECEBER");
            System.out.println(results[4]+"/"+NTHREADS+"\t ERRO_NAO_DEFINIDO");
            System.exit(0);
        } catch (Exception ex) {
            Logger.getLogger(Teste.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
