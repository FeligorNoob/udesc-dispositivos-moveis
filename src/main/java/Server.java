import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Server {

    private static final int PORT = 5000;
    private static final Map<String, ClientHandler> clients = new HashMap<>();

    public static void main(String[] args) {

        try (ServerSocket server = new ServerSocket(PORT)) {

            System.out.println("Servidor iniciado na porta " + PORT);

            while (true) {

                Socket socket = server.accept();

                System.out.println("Nova conexão: " + socket.getInetAddress().getHostAddress());

                ClientHandler client = new ClientHandler(socket);

                client.start();
            }

        } catch (IOException e) {
            System.out.println("Erro no servidor: " + e.getMessage());
        }

    }

    public static synchronized void addClient(String name, ClientHandler client) {
        clients.put(name, client);

        System.out.println("Cliente " + name + " entrou.");
    }

    public static synchronized void deleteCliente(String name) {
        clients.remove(name);

        System.out.println("Cliente " + name + " saiu.");

    }

    public static synchronized String listClients() {
        if (clients.isEmpty()) {
            System.out.println("Nenhum cliente encontrado.");
        }

        return String.join(", ", clients.keySet());
    }
}