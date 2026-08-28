package org.example;

import java.io.IOException;
import java.net.Socket;

public class Cliente {
    public static void main(String args[]) throws IOException {
        var servidor = new Socket("127.0.0.1", 12345);
        System.out.println("Cliente conectado!");
    }
}
