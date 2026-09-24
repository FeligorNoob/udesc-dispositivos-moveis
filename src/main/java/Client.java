import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Client {

    private static final String AJUDA = String.join(System.lineSeparator(),
            "Comandos:",
            "  /send message <destinatario> <mensagem>",
            "  /send file <destinatario> <caminho do arquivo>",
            "  /users",
            "  /sair");

    private final DataInputStream entrada;
    private final DataOutputStream saida;

    private volatile boolean saindo = false;

    public Client(Socket socket) throws IOException {
        this.entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.saida = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : Protocolo.PORTA;

        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));

        try (Socket socket = new Socket(host, porta)) {
            Client client = new Client(socket);

            System.out.print("Informe seu nome: ");
            String name = teclado.readLine();
            if (name == null || !client.conectar(name)) {
                return;
            }

            System.out.println(AJUDA);
            client.iniciarRecebimento();
            client.lerComandos(teclado);
        } catch (IOException e) {
            System.out.println("Erro de conexão com o servidor " + host + ":" + porta + ": " + e.getMessage());
        }
    }

    private boolean conectar(String name) throws IOException {
        saida.writeUTF(name);
        saida.flush();

        String resposta = entrada.readUTF();
        System.out.println(entrada.readUTF());
        return Protocolo.OK.equals(resposta);
    }

    private void lerComandos(BufferedReader teclado) throws IOException {
        String linha;
        while ((linha = teclado.readLine()) != null) {
            linha = linha.trim();
            if (linha.isEmpty()) {
                continue;
            }

            if (linha.equals("/sair")) {
                break;
            } else if (linha.equals("/users")) {
                saida.writeUTF(Protocolo.USERS);
                saida.flush();
            } else if (linha.startsWith("/send ")) {
                processarSend(linha);
            } else {
                System.out.println("Comando inválido.");
                System.out.println(AJUDA);
            }
        }

        saindo = true;
        saida.writeUTF(Protocolo.SAIR);
        saida.flush();
        System.out.println("Conexão encerrada.");
    }

    private void processarSend(String linha) throws IOException {
        // /send <tipo> <destinatario> <conteudo>
        String[] partes = linha.split("\\s+", 4);
        if (partes.length < 4) {
            System.out.println("Uso: /send message <destinatario> <mensagem> ou /send file <destinatario> <caminho>");
            return;
        }

        String tipo = partes[1];
        String destinatario = partes[2];
        String conteudo = partes[3];

        if (tipo.equals("message")) {
            saida.writeUTF(Protocolo.MSG);
            saida.writeUTF(destinatario);
            saida.writeUTF(conteudo);
            saida.flush();
        } else if (tipo.equals("file")) {
            enviarArquivo(destinatario, conteudo);
        } else {
            System.out.println("Tipo inválido: use message ou file.");
        }
    }

    private void enviarArquivo(String destinatario, String caminho) throws IOException {
        Path arquivo = Paths.get(removerAspas(caminho));
        if (!Files.isRegularFile(arquivo) || !Files.isReadable(arquivo)) {
            System.out.println("Arquivo não encontrado: " + arquivo.toAbsolutePath());
            return;
        }

        saida.writeUTF(Protocolo.FILE);
        saida.writeUTF(destinatario);
        saida.writeUTF(arquivo.getFileName().toString());

        byte[] buffer = new byte[Protocolo.TAMANHO_BLOCO];
        try (InputStream in = new FileInputStream(arquivo.toFile())) {
            int lidos;
            while ((lidos = in.read(buffer)) != -1) {
                if (lidos > 0) {
                    saida.writeInt(lidos);
                    saida.write(buffer, 0, lidos);
                }
            }
            saida.writeInt(Protocolo.FIM_ARQUIVO);
        } catch (IOException e) {
            // erro lendo o arquivo local: avisa o servidor para descartar o que ja foi enviado
            saida.writeInt(Protocolo.ARQUIVO_ABORTADO);
            System.out.println("Erro ao ler o arquivo: " + e.getMessage());
        }
        saida.flush();
    }

    private void iniciarRecebimento() {
        Thread receptor = new Thread(() -> {
            try {
                while (true) {
                    String tipo = entrada.readUTF();

                    switch (tipo) {
                        case Protocolo.MSG:
                            String remetente = entrada.readUTF();
                            System.out.println(remetente + ": " + entrada.readUTF());
                            break;
                        case Protocolo.FILE:
                            receberArquivo(entrada.readUTF(), entrada.readUTF());
                            break;
                        default:
                            System.out.println("[servidor] " + entrada.readUTF());
                    }
                }
            } catch (IOException e) {
                if (!saindo) {
                    System.out.println("Conexão com o servidor perdida.");
                    System.exit(1);
                }
            }
        });
        receptor.setDaemon(true);
        receptor.start();
    }

    /**
     * Grava os bytes recebidos no diretório corrente, com o nome original do arquivo.
     */
    private void receberArquivo(String remetente, String nomeArquivo) throws IOException {
        String nome = nomeLocal(nomeArquivo);
        Path destino = Paths.get(System.getProperty("user.dir")).resolve(nome);

        OutputStream out = null;
        String erro = null;
        try {
            out = new FileOutputStream(destino.toFile());
        } catch (IOException e) {
            erro = e.getMessage();
        }
        boolean criado = out != null;

        // os blocos sempre sao lidos ate o fim, mesmo com erro de gravacao, para nao dessincronizar o socket
        byte[] buffer = new byte[Protocolo.TAMANHO_BLOCO];
        long total = 0;
        int tamanho;
        try {
            while ((tamanho = entrada.readInt()) > 0) {
                entrada.readFully(buffer, 0, tamanho);
                total += tamanho;

                if (out != null) {
                    try {
                        out.write(buffer, 0, tamanho);
                    } catch (IOException e) {
                        erro = e.getMessage();
                        fecharSilenciosamente(out);
                        out = null;
                    }
                }
            }
        } finally {
            if (out != null) {
                out.close();
            }
        }

        if (tamanho == Protocolo.FIM_ARQUIVO && erro == null) {
            System.out.println(remetente + " enviou o arquivo " + nome + " (" + total + " bytes), salvo em " + destino);
            return;
        }

        if (criado) {
            Files.deleteIfExists(destino);
        }
        if (erro != null) {
            System.out.println("Erro ao gravar o arquivo " + nome + " enviado por " + remetente + ": " + erro);
        } else {
            System.out.println("O envio do arquivo " + nome + " por " + remetente + " foi interrompido.");
        }
    }

    /**
     * Mantem apenas o nome do arquivo, descartando qualquer diretorio vindo do remetente.
     */
    private static String nomeLocal(String nomeArquivo) {
        try {
            Path nome = Paths.get(nomeArquivo).getFileName();
            if (nome != null && !nome.toString().equals("..") && !nome.toString().equals(".")) {
                return nome.toString();
            }
        } catch (InvalidPathException e) {
            // nome invalido neste sistema operacional
        }
        return "arquivo_recebido";
    }

    private static void fecharSilenciosamente(OutputStream out) {
        try {
            out.close();
        } catch (IOException e) {
            // o erro de gravacao ja foi registrado
        }
    }

    private static String removerAspas(String caminho) {
        if (caminho.length() >= 2 && caminho.startsWith("\"") && caminho.endsWith("\"")) {
            return caminho.substring(1, caminho.length() - 1);
        }
        return caminho;
    }
}
