package br.com.mindhaven;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Main {

    public static void main(String... args) {
        Quarkus.run(MyApp.class, args);
    }

    public static class MyApp implements QuarkusApplication {
        @Override
        public int run(String... args) {
            // Os client secrets por tenant saíram daqui para a configuração — ver
            // core.security.TenantClientsConfig e core.security.TenantClientRegistry.
            // Antes, cadastrar um tenant novo exigia recompilar a aplicação.
            Quarkus.waitForExit();
            return 0;
        }
    }
}
