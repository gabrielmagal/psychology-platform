import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@RequestScoped
@ApplicationPath("/api")
@OpenAPIDefinition(info = @Info(
        title = "cliniclick",
        version = "0.1-beta"
))
public class App extends Application {

    public App() {
        super();
        System.out.println();

    }
}
