import java.util.*;
import java.io.*;
import java.net.*;

public class ClientHandler extends Thread {
    private final Socket socket;

    private BufferedReader entrada;
    private PrintWriter saida;

    private String name;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            saida = new PrintWriter(socket.getOutputStream(), true);

            // le o nome do cliente
            name = entrada.readLine();

            if (name == null || name.isBlank()) {
                socket.close();
                return;
            }

            Server.addClient(name, this);

            System.out.println("Conectado como " + name);

            // processar a mensagem...
        } catch (IOException e) {
            System.out.println("Problema com o cliente " + name);
        } finally {
            if (name != null) {
                Server.deleteCliente(name);
            }
            try {
                socket.close();
            } catch (IOException e) {
                // conexao encerrada
            }
        }
    }


}