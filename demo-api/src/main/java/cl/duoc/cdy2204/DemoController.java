package cl.duoc.cdy2204;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    @GetMapping("/salud")
    public Map<String, Object> salud() {
        return Map.of("estado", "OK", "servicio", "cloudnative-semana4");
    }

    @GetMapping("/datos")
    public Map<String, Object> datos() {
        return Map.of(
            "asignatura", "Desarrollo Cloud Native",
            "semana", 4,
            "mensaje", "Datos de prueba desde una API alojada en EC2",
            "fechaConsulta", Instant.now().toString(),
            "productos", List.of(
                Map.of("id", 1, "nombre", "Notebook", "stock", 8),
                Map.of("id", 2, "nombre", "Teclado", "stock", 15)
            )
        );
    }
}
