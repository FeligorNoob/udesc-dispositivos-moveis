package org.example;

import java.io.IOException;
import java.net.ServerSocket;

public class Servidor {
    public static void main(String[] args) {

        try {
            var servidor = new ServerSocket(12345);
            System.out.println("Servidor iniciado na 12345!");

            var socket = servidor.accept();
            System.out.println("Conexão estabelecida!");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}