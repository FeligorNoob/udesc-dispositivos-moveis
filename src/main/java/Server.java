import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final String LOG_FILE = "conexoes.log";
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final Map<String, ClientHandler> clients = new HashMap<>();

    public static void main(String[] args) {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : Protocolo.PORTA;

        // pool de threads: cada cliente conectado é atendido por uma thread do pool
        ExecutorService executor = Executors.newCachedThreadPool();

        try (ServerSocket server = new ServerSocket(porta)) {

            System.out.println("Servidor iniciado na porta " + porta);

            while (true) {

                Socket socket = server.accept();

                System.out.println("Nova conexão: " + socket.getInetAddress().getHostAddress());

                executor.execute(new ClientHandler(socket, LocalDateTime.now()));
            }

        } catch (IOException e) {
            System.out.println("Erro no servidor: " + e.getMessage());
        } finally {
            executor.shutdownNow();
        }

    }

    /**
     * Registra o cliente. Retorna false se já existe um cliente com o mesmo nome.
     */
    public static synchronized boolean addClient(String name, ClientHandler client) {
        if (clients.containsKey(name)) {
            return false;
        }

        clients.put(name, client);

        System.out.println("Cliente " + name + " entrou.");
        return true;
    }

    public static synchronized void deleteCliente(String name, ClientHandler client) {
        if (clients.remove(name, client)) {
            System.out.println("Cliente " + name + " saiu.");
        }
    }

    public static synchronized ClientHandler getClient(String name) {
        return clients.get(name);
    }

    public static synchronized String listClients() {
        if (clients.isEmpty()) {
            return "Nenhum cliente conectado.";
        }

        return String.join(", ", new TreeSet<>(clients.keySet()));
    }

    public static synchronized void log(String ip, String name, LocalDateTime dataConexao) {
        try (PrintWriter log = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            log.println(FORMATO_DATA.format(dataConexao) + " | IP: " + ip + " | Cliente: " + name);
        } catch (IOException e) {
            System.out.println("Erro ao gravar log: " + e.getMessage());
        }
    }
}
