import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final LocalDateTime dataConexao;

    private DataInputStream entrada;
    private DataOutputStream saida;

    private String name;

    public ClientHandler(Socket socket, LocalDateTime dataConexao) {
        this.socket = socket;
        this.dataConexao = dataConexao;
    }

    @Override
    public void run() {
        boolean registrado = false;
        try {
            entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            saida = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            // le o nome do cliente
            name = entrada.readUTF().trim();

            if (name.isEmpty() || name.contains(" ")) {
                enviarResposta(Protocolo.ERRO, "Nome inválido. Use um nome sem espaços.");
                return;
            }

            if (!Server.addClient(name, this)) {
                enviarResposta(Protocolo.ERRO, "Já existe um cliente conectado com o nome " + name + ".");
                return;
            }
            registrado = true;

            Server.log(socket.getInetAddress().getHostAddress(), name, dataConexao);
            enviarResposta(Protocolo.OK, "Conectado como " + name);

            System.out.println("Conectado como " + name);

            processarComandos();
        } catch (EOFException e) {
            // cliente fechou a conexao sem enviar /sair
        } catch (IOException e) {
            System.out.println("Problema com o cliente " + name + ": " + e.getMessage());
        } finally {
            if (registrado) {
                Server.deleteCliente(name, this);
            }
            try {
                socket.close();
            } catch (IOException e) {
                // conexao encerrada
            }
        }
    }

    private void processarComandos() throws IOException {
        while (true) {
            String comando = entrada.readUTF();

            switch (comando) {
                case Protocolo.MSG:
                    rotearMensagem(entrada.readUTF(), entrada.readUTF());
                    break;
                case Protocolo.FILE:
                    rotearArquivo(entrada.readUTF(), entrada.readUTF());
                    break;
                case Protocolo.USERS:
                    enviarInfo("Clientes conectados: " + Server.listClients());
                    break;
                case Protocolo.SAIR:
                    return;
                default:
                    throw new IOException("Comando desconhecido: " + comando);
            }
        }
    }

    private void rotearMensagem(String destinatario, String mensagem) throws IOException {
        ClientHandler destino = buscarDestino(destinatario);
        if (destino == null) {
            return;
        }

        try {
            synchronized (destino.saida) {
                destino.saida.writeUTF(Protocolo.MSG);
                destino.saida.writeUTF(name);
                destino.saida.writeUTF(mensagem);
                destino.saida.flush();
            }
        } catch (IOException e) {
            enviarInfo("Não foi possível entregar a mensagem para " + destinatario + ".");
        }
    }

    /**
     * Repassa os blocos do arquivo diretamente ao destinatário, sem guardá-lo no servidor.
     * Se o destinatário não existir ou cair durante o envio, os blocos restantes são descartados
     * para manter o fluxo do remetente sincronizado.
     */
    private void rotearArquivo(String destinatario, String nomeArquivo) throws IOException {
        ClientHandler destino = buscarDestino(destinatario);
        if (destino == null) {
            descartarBlocos();
            return;
        }

        boolean entregue = true;
        long total = 0;
        byte[] buffer = new byte[Protocolo.TAMANHO_BLOCO];

        synchronized (destino.saida) {
            try {
                destino.saida.writeUTF(Protocolo.FILE);
                destino.saida.writeUTF(name);
                destino.saida.writeUTF(nomeArquivo);
            } catch (IOException e) {
                entregue = false;
            }

            try {
                int tamanho;
                while ((tamanho = entrada.readInt()) > 0) {
                    entrada.readFully(buffer, 0, tamanho);
                    total += tamanho;

                    if (entregue) {
                        try {
                            destino.saida.writeInt(tamanho);
                            destino.saida.write(buffer, 0, tamanho);
                        } catch (IOException e) {
                            entregue = false;
                        }
                    }
                }

                if (tamanho == Protocolo.ARQUIVO_ABORTADO) {
                    entregue = false;
                }
                if (entregue) {
                    try {
                        destino.saida.writeInt(Protocolo.FIM_ARQUIVO);
                        destino.saida.flush();
                    } catch (IOException e) {
                        entregue = false;
                    }
                }
            } catch (IOException e) {
                // remetente caiu no meio do envio: avisa o destinatario para descartar o arquivo
                abortarArquivo(destino);
                throw e;
            }

            if (!entregue) {
                abortarArquivo(destino);
            }
        }

        if (entregue) {
            enviarInfo("Arquivo " + nomeArquivo + " (" + total + " bytes) enviado para " + destinatario + ".");
        } else {
            enviarInfo("Não foi possível entregar o arquivo " + nomeArquivo + " para " + destinatario + ".");
        }
    }

    private ClientHandler buscarDestino(String destinatario) throws IOException {
        if (destinatario.equals(name)) {
            enviarInfo("Não é possível enviar para si mesmo.");
            return null;
        }

        ClientHandler destino = Server.getClient(destinatario);
        if (destino == null) {
            enviarInfo("Cliente " + destinatario + " não está conectado. Use /users para ver a lista.");
        }
        return destino;
    }

    private void descartarBlocos() throws IOException {
        byte[] buffer = new byte[Protocolo.TAMANHO_BLOCO];
        int tamanho;
        while ((tamanho = entrada.readInt()) > 0) {
            entrada.readFully(buffer, 0, tamanho);
        }
    }

    private static void abortarArquivo(ClientHandler destino) {
        try {
            destino.saida.writeInt(Protocolo.ARQUIVO_ABORTADO);
            destino.saida.flush();
        } catch (IOException e) {
            // destinatario tambem desconectou
        }
    }

    private void enviarInfo(String texto) throws IOException {
        enviarResposta(Protocolo.INFO, texto);
    }

    private void enviarResposta(String tipo, String texto) throws IOException {
        synchronized (saida) {
            saida.writeUTF(tipo);
            saida.writeUTF(texto);
            saida.flush();
        }
    }
}
