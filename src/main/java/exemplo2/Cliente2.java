package exemplo2;

import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Scanner;

public class Cliente2 {
    public static void main(String args[]) throws IOException {
        var cliente = new Socket("10.20.180.92", 12345);
        var teclado = new Scanner(System.in);
        var saida = new PrintStream(cliente.getOutputStream());

        while (teclado.hasNextLine()) {
            saida.println(teclado.nextLine());
        }

        saida.close();
        teclado.close();
        cliente.close();
    }
}
