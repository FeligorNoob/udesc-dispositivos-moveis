/**
 * Constantes do protocolo usado entre cliente e servidor.
 *
 * Todos os pacotes começam com um tipo (writeUTF) seguido dos campos:
 *
 * Cliente -> Servidor
 *   nome                                   (primeiro pacote, identificação)
 *   MSG   destinatario texto
 *   FILE  destinatario nomeArquivo blocos...
 *   USERS
 *   SAIR
 *
 * Servidor -> Cliente
 *   OK | ERRO texto                        (resposta à identificação)
 *   MSG   remetente texto
 *   FILE  remetente nomeArquivo blocos...
 *   INFO  texto
 *
 * Os bytes do arquivo trafegam em blocos: writeInt(tamanho) + bytes.
 * Um bloco de tamanho 0 indica fim do arquivo e -1 indica que o envio foi abortado.
 */
public final class Protocolo {

    public static final int PORTA = 5000;

    public static final String OK = "OK";
    public static final String ERRO = "ERRO";
    public static final String MSG = "MSG";
    public static final String FILE = "FILE";
    public static final String USERS = "USERS";
    public static final String SAIR = "SAIR";
    public static final String INFO = "INFO";

    public static final int FIM_ARQUIVO = 0;
    public static final int ARQUIVO_ABORTADO = -1;
    public static final int TAMANHO_BLOCO = 8192;

    private Protocolo() {
    }
}
