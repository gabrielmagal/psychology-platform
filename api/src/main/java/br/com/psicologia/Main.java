package br.com.psicologia;

import br.com.psicologia.adapter.controller.dto.ClientSecretDto;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

import java.util.HashMap;

@QuarkusMain
public class Main {
    public static HashMap<String, ClientSecretDto> hashClientSecret = new HashMap<>();

    public static void main(String... args) {
        Quarkus.run(MyApp.class, args);
    }

    public static class MyApp implements QuarkusApplication {
        @Override
        public int run(String... args) {
            hashClientSecret.put("barueri", new ClientSecretDto("backend", "jBmhRAsGfp9QWcRTDBGdlSqXv959DWt0"));
            hashClientSecret.put("campinas", new ClientSecretDto("backend", "giRyjMncqT8x1Q7cm4BIkEcTZ4g8l46h"));
            hashClientSecret.put("doacoesalimenticias", new ClientSecretDto("doacoesalimenticias-backend", "FwS35Wrktt0526i2lVMPV2RF4FAgdItG"));
            hashClientSecret.put("petrodoacoes", new ClientSecretDto("petrodoacoes-backend", "rCuZpfNN7LInS5BqqRMoQMFf8BeTHFIY"));
            System.out.println("Do startup logic here");
            Quarkus.waitForExit();
            return 0;
        }
    }
}