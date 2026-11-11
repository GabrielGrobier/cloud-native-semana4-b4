package cl.duoc.cdy2204;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DemoControllerTest {
    private final DemoController controller = new DemoController();

    @Test
    void saludDevuelveEstadoOk() {
        assertThat(controller.salud()).containsEntry("estado", "OK");
    }

    @Test
    void datosIncluyeContenidoParaDemostracion() {
        Map<String, Object> respuesta = controller.datos();
        assertThat(respuesta).containsEntry("semana", 4);
        assertThat(respuesta).containsKeys("productos", "mensaje", "fechaConsulta");
    }
}
